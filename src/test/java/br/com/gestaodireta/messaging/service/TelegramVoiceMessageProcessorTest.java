package br.com.gestaodireta.messaging.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.gestaodireta.ai.transcription.AudioTranscriptionClient;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionException;
import br.com.gestaodireta.ai.transcription.AudioTranscriptionProperties;
import br.com.gestaodireta.messaging.domain.MessagingAccount;
import br.com.gestaodireta.messaging.domain.MessagingConversation;
import br.com.gestaodireta.messaging.domain.MessagingMessage;
import br.com.gestaodireta.messaging.dto.IncomingAudioAttachment;
import br.com.gestaodireta.messaging.telegram.client.TelegramBotClient;
import org.junit.jupiter.api.Test;

class TelegramVoiceMessageProcessorTest {

    @Test
    void shouldTranscribeVoiceAndReuseFinancialTextProcessor() {
        Fixture fixture = fixture();
        when(fixture.telegram.getFilePath("voice-file")).thenReturn("voice/file.oga");
        when(fixture.telegram.downloadFile("voice/file.oga")).thenReturn(new byte[] {1, 2, 3});
        when(fixture.transcriber.transcribe(new byte[] {1, 2, 3}, "audio/ogg"))
                .thenReturn("Gastei R$ 850 com diesel hoje.");

        fixture.processor.process(
                fixture.account,
                fixture.conversation,
                fixture.message,
                new IncomingAudioAttachment("voice-file", 5, "audio/ogg", 3L));

        verify(fixture.financial).process(fixture.account, fixture.conversation, fixture.message);
        verify(fixture.transcriber).transcribe(new byte[] {1, 2, 3}, "audio/ogg");
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
        verify(fixture.transcriber, never()).transcribe(any(), any());
        verify(fixture.financial, never()).process(any(), any(), any());
    }

    @Test
    void shouldNotForwardEmptyOrFailedTranscription() {
        Fixture fixture = fixture();
        when(fixture.telegram.getFilePath("voice-file")).thenReturn("voice/file.oga");
        when(fixture.telegram.downloadFile("voice/file.oga")).thenReturn(new byte[] {1});
        when(fixture.transcriber.transcribe(any(), eq("audio/ogg")))
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

    private Fixture fixture() {
        AudioTranscriptionProperties properties = new AudioTranscriptionProperties();
        properties.setEnabled(true);
        TelegramBotClient telegram = mock(TelegramBotClient.class);
        AudioTranscriptionClient transcriber = mock(AudioTranscriptionClient.class);
        TelegramFinancialExtractionProcessor financial =
                mock(TelegramFinancialExtractionProcessor.class);
        OutgoingMessagingService outgoing = mock(OutgoingMessagingService.class);
        return new Fixture(
                new TelegramVoiceMessageProcessor(
                        properties, telegram, transcriber, financial, outgoing),
                telegram,
                transcriber,
                financial,
                new MessagingAccount(),
                new MessagingConversation(),
                new MessagingMessage());
    }

    private record Fixture(
            TelegramVoiceMessageProcessor processor,
            TelegramBotClient telegram,
            AudioTranscriptionClient transcriber,
            TelegramFinancialExtractionProcessor financial,
            MessagingAccount account,
            MessagingConversation conversation,
            MessagingMessage message) {}
}
