package br.com.gestaodireta.messaging.service;

import br.com.gestaodireta.ai.transcription.AudioTranscriptionClient;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionException;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionProperties;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionRequest;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionResult;
import br.com.gestaodireta.messaging.domain.MessagingAccount;
import br.com.gestaodireta.messaging.domain.MessagingConversation;
import br.com.gestaodireta.messaging.domain.MessagingMessage;
import br.com.gestaodireta.messaging.dto.IncomingAudioAttachment;
import br.com.gestaodireta.messaging.telegram.client.TelegramBotClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TelegramVoiceMessageProcessor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(TelegramVoiceMessageProcessor.class);
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of("audio/ogg", "audio/opus");
    private static final String DISABLED_MESSAGE =
            "Mensagens de áudio não estão disponíveis no momento. Envie a movimentação por texto.";
    private static final String TOO_LONG_MESSAGE =
            "Seu áudio é muito longo. Envie uma mensagem de voz de até %d segundos descrevendo uma movimentação.";
    private static final String TOO_LARGE_MESSAGE =
            "Seu áudio é muito grande. Envie uma mensagem de voz menor descrevendo uma movimentação.";
    private static final String UNSUPPORTED_FORMAT_MESSAGE =
            "Não consegui processar o formato deste áudio. Envie uma mensagem de voz pelo Telegram.";
    private static final String DOWNLOAD_ERROR_MESSAGE =
            "Não consegui baixar seu áudio agora. Tente novamente em instantes ou envie a movimentação por texto.";
    private static final String TRANSCRIPTION_ERROR_MESSAGE =
            "Não consegui processar seu áudio agora. Tente novamente em instantes ou envie a movimentação por texto.";
    private static final String EMPTY_TRANSCRIPT_MESSAGE =
            "Não consegui entender o áudio. Tente novamente falando a movimentação de forma clara.";

    private final AudioTranscriptionProperties properties;
    private final TelegramBotClient telegram;
    private final AudioTranscriptionClient transcriber;
    private final TelegramFinancialExtractionProcessor financialExtractionProcessor;
    private final OutgoingMessagingService outgoing;
    private final TelegramAudioDebugFileSaver audioDebugFileSaver;

    public TelegramVoiceMessageProcessor(
            AudioTranscriptionProperties properties,
            TelegramBotClient telegram,
            AudioTranscriptionClient transcriber,
            TelegramFinancialExtractionProcessor financialExtractionProcessor,
            OutgoingMessagingService outgoing,
            TelegramAudioDebugFileSaver audioDebugFileSaver) {
        this.properties = properties;
        this.telegram = telegram;
        this.transcriber = transcriber;
        this.financialExtractionProcessor = financialExtractionProcessor;
        this.outgoing = outgoing;
        this.audioDebugFileSaver = audioDebugFileSaver;
    }

    public void process(
            MessagingAccount account,
            MessagingConversation conversation,
            MessagingMessage message,
            IncomingAudioAttachment voice) {
        String mimeType = normalizeMimeType(voice.mimeType());
        LOGGER.info(
                "telegram voice received: updateId={} sourceMessageId={} internalMessageId={} durationSeconds={} mimeType={} fileSize={}",
                message.getProviderUpdateId(),
                message.getProviderMessageId(),
                message.getId(),
                voice.durationSeconds(),
                mimeType,
                voice.fileSize());
        if (!properties.isEnabled()) {
            outgoing.send(conversation, DISABLED_MESSAGE);
            return;
        }
        if (exceedsDuration(voice.durationSeconds())) {
            outgoing.send(
                    conversation, TOO_LONG_MESSAGE.formatted(properties.getMaxDurationSeconds()));
            return;
        }
        if (!SUPPORTED_MIME_TYPES.contains(mimeType)) {
            outgoing.send(conversation, UNSUPPORTED_FORMAT_MESSAGE);
            return;
        }
        if (exceedsSize(voice.fileSize())) {
            outgoing.send(conversation, TOO_LARGE_MESSAGE);
            return;
        }
        outgoing.send(conversation, "🎙️ Recebi seu áudio. Estou processando...");
        byte[] audio;
        long downloadStart = System.nanoTime();
        try {
            audio = telegram.downloadFile(telegram.getFilePath(voice.fileId()));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "telegram voice rejected: stage=DOWNLOAD reason=TELEGRAM_FILE_DOWNLOAD_ERROR updateId={} sourceMessageId={} internalMessageId={}",
                    message.getProviderUpdateId(),
                    message.getProviderMessageId(),
                    message.getId());
            outgoing.send(conversation, DOWNLOAD_ERROR_MESSAGE);
            return;
        }
        if (audio == null || audio.length == 0) {
            LOGGER.warn(
                    "telegram voice rejected: stage=DOWNLOAD reason=EMPTY_AUDIO updateId={} sourceMessageId={} internalMessageId={}",
                    message.getProviderUpdateId(),
                    message.getProviderMessageId(),
                    message.getId());
            outgoing.send(conversation, DOWNLOAD_ERROR_MESSAGE);
            return;
        }
        LOGGER.info(
                "telegram voice downloaded: updateId={} sourceMessageId={} internalMessageId={} bytes={} elapsedMs={}",
                message.getProviderUpdateId(),
                message.getProviderMessageId(),
                message.getId(),
                audio.length,
                elapsedMillis(downloadStart));
        if (exceedsSize((long) audio.length)) {
            outgoing.send(conversation, TOO_LARGE_MESSAGE);
            return;
        }
        audioDebugFileSaver.save(
                message.getProviderUpdateId(), message.getProviderMessageId(), audio, mimeType);
        try {
            AudioTranscriptionResult transcription =
                    transcriber.transcribe(
                            new AudioTranscriptionRequest(
                                    audio,
                                    mimeType,
                                    message.getProviderUpdateId(),
                                    message.getProviderMessageId()));
            String transcript = transcription == null ? null : transcription.text();
            if (transcript == null || transcript.isBlank()) {
                outgoing.send(conversation, EMPTY_TRANSCRIPT_MESSAGE);
                return;
            }
            LOGGER.debug(
                    "audio transcription completed: provider={} text=\"{}\"",
                    transcriber.providerName(),
                    normalizeTranscriptForLog(transcript));
            message.setContent(transcript.trim());
            financialExtractionProcessor.process(account, conversation, message);
        } catch (AudioTranscriptionException exception) {
            String response =
                    exception.getReason() == AudioTranscriptionException.Reason.EMPTY_TRANSCRIPT
                            ? EMPTY_TRANSCRIPT_MESSAGE
                            : TRANSCRIPTION_ERROR_MESSAGE;
            LOGGER.warn(
                    "telegram voice rejected: stage=TRANSCRIPTION reason={} updateId={} sourceMessageId={} internalMessageId={}",
                    exception.getReason(),
                    message.getProviderUpdateId(),
                    message.getProviderMessageId(),
                    message.getId());
            outgoing.send(conversation, response);
        }
    }

    private String normalizeMimeType(String value) {
        if (value == null || value.isBlank()) {
            return "audio/ogg";
        }
        return value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private boolean exceedsDuration(Integer durationSeconds) {
        return durationSeconds != null && durationSeconds > properties.getMaxDurationSeconds();
    }

    private boolean exceedsSize(Long size) {
        return size != null && size > maxSizeBytes();
    }

    private long maxSizeBytes() {
        return properties.getMaxSizeMb() * 1024L * 1024L;
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private String normalizeTranscriptForLog(String transcript) {
        return transcript.replace('\r', ' ').replace('\n', ' ');
    }
}
