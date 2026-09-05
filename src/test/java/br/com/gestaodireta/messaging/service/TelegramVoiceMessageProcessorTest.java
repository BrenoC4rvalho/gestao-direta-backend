package br.com.gestaodireta.messaging.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.gestaodireta.ai.transcription.AudioTranscriptionClient;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionException;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionProperties;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionRequest;
import br.com.gestaodireta.messaging.domain.MessagingAccount;
import br.com.gestaodireta.messaging.domain.MessagingConversation;
import br.com.gestaodireta.messaging.domain.MessagingMessage;
import br.com.gestaodireta.messaging.dto.IncomingAudioAttachment;
import br.com.gestaodireta.messaging.telegram.client.TelegramBotClient;
import br.com.gestaodireta.messaging.telegram.config.TelegramAudioDebugProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TelegramVoiceMessageProcessorTest {

    @TempDir Path tempDir;

    @Test
    void shouldTranscribeVoiceAndReuseFinancialTextProcessor() {
        Fixture fixture = fixture();
        when(fixture.telegram.getFilePath("voice-file")).thenReturn("voice/file.oga");
        when(fixture.telegram.downloadFile("voice/file.oga")).thenReturn(new byte[] {1, 2, 3});
        when(fixture.transcriber.transcribe(any())).thenReturn("Gastei R$ 850 com diesel hoje.");

        fixture.processor.process(
                fixture.account,
                fixture.conversation,
                fixture.message,
                new IncomingAudioAttachment("voice-file", 5, "audio/ogg", 3L));

        verify(fixture.financial).process(fixture.account, fixture.conversation, fixture.message);
        verify(fixture.transcriber)
                .transcribe(
                        org.mockito.ArgumentMatchers.argThat(
                                request ->
                                        hasExpectedAudioContext(
                                                request, new byte[] {1, 2, 3}, "audio/ogg")));
        org.assertj.core.api.Assertions.assertThat(fixture.message.getContent())
                .isEqualTo("Gastei R$ 850 com diesel hoje.");
    }

    @Test
    void shouldRejectLongVoiceBeforeDownload() {
        Fixture fixture = fixture();

        fixture.processor.process(
                fixture.account,
                fixture.conversation,
                fixture.message,
                new IncomingAudioAttachment("voice-file", 61, "audio/ogg", 3L));

        verify(fixture.telegram, never()).getFilePath(any());
        verify(fixture.transcriber, never()).transcribe(any());
        verify(fixture.financial, never()).process(any(), any(), any());
    }

    @Test
    void shouldNotForwardEmptyOrFailedTranscription() {
        Fixture fixture = fixture();
        when(fixture.telegram.getFilePath("voice-file")).thenReturn("voice/file.oga");
        when(fixture.telegram.downloadFile("voice/file.oga")).thenReturn(new byte[] {1});
        when(fixture.transcriber.transcribe(any()))
                .thenThrow(
                        new AudioTranscriptionException(
                                AudioTranscriptionException.Reason.TIMEOUT, "timeout", null));

        fixture.processor.process(
                fixture.account,
                fixture.conversation,
                fixture.message,
                new IncomingAudioAttachment("voice-file", 5, "audio/ogg", null));

        verify(fixture.financial, never()).process(any(), any(), any());
    }

    @Test
    void shouldContinueTranscriptionWhenDebugFileSavingFails() throws Exception {
        Fixture fixture = fixture();
        Path fileInsteadOfDirectory = tempDir.resolve("not-a-directory");
        Files.createFile(fileInsteadOfDirectory);
        TelegramAudioDebugProperties debugProperties = new TelegramAudioDebugProperties();
        debugProperties.setSaveEnabled(true);
        debugProperties.setDirectory(fileInsteadOfDirectory.toString());
        TelegramVoiceMessageProcessor processor =
                new TelegramVoiceMessageProcessor(
                        fixture.properties,
                        fixture.telegram,
                        fixture.transcriber,
                        fixture.financial,
                        fixture.outgoing,
                        new TelegramAudioDebugFileSaver(debugProperties));
        when(fixture.telegram.getFilePath("voice-file")).thenReturn("voice/file.oga");
        when(fixture.telegram.downloadFile("voice/file.oga")).thenReturn(new byte[] {1, 2, 3});
        when(fixture.transcriber.transcribe(any())).thenReturn("Gastei R$ 850 com diesel hoje.");

        processor.process(
                fixture.account,
                fixture.conversation,
                fixture.message,
                new IncomingAudioAttachment("voice-file", 5, "audio/ogg", 3L));

        verify(fixture.transcriber)
                .transcribe(
                        org.mockito.ArgumentMatchers.argThat(
                                request ->
                                        hasExpectedAudioContext(
                                                request, new byte[] {1, 2, 3}, "audio/ogg")));
        verify(fixture.financial).process(fixture.account, fixture.conversation, fixture.message);
    }

    private Fixture fixture() {
        AudioTranscriptionProperties properties = new AudioTranscriptionProperties();
        properties.setEnabled(true);
        TelegramBotClient telegram = mock(TelegramBotClient.class);
        AudioTranscriptionClient transcriber = mock(AudioTranscriptionClient.class);
        TelegramFinancialExtractionProcessor financial =
                mock(TelegramFinancialExtractionProcessor.class);
        OutgoingMessagingService outgoing = mock(OutgoingMessagingService.class);
        TelegramAudioDebugProperties debugProperties = new TelegramAudioDebugProperties();
        MessagingMessage message = new MessagingMessage();
        message.setProviderUpdateId("519749095");
        message.setProviderMessageId("391");
        return new Fixture(
                new TelegramVoiceMessageProcessor(
                        properties,
                        telegram,
                        transcriber,
                        financial,
                        outgoing,
                        new TelegramAudioDebugFileSaver(debugProperties)),
                properties,
                telegram,
                transcriber,
                financial,
                outgoing,
                new MessagingAccount(),
                new MessagingConversation(),
                message);
    }

    private boolean hasExpectedAudioContext(
            AudioTranscriptionRequest request, byte[] audio, String mimeType) {
        return request != null
                && java.util.Arrays.equals(request.audioBytes(), audio)
                && mimeType.equals(request.mimeType())
                && "519749095".equals(request.updateId())
                && "391".equals(request.sourceMessageId());
    }

    private record Fixture(
            TelegramVoiceMessageProcessor processor,
            AudioTranscriptionProperties properties,
            TelegramBotClient telegram,
            AudioTranscriptionClient transcriber,
            TelegramFinancialExtractionProcessor financial,
            OutgoingMessagingService outgoing,
            MessagingAccount account,
            MessagingConversation conversation,
            MessagingMessage message) {}
}
