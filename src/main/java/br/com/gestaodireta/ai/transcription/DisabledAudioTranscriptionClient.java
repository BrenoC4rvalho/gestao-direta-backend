package br.com.gestaodireta.ai.transcription;

public class DisabledAudioTranscriptionClient implements AudioTranscriptionClient {

    @Override
    public String transcribe(byte[] audio, String mimeType) {
        throw new AudioTranscriptionException(
                AudioTranscriptionException.Reason.DISABLED,
                "Audio transcription is disabled",
                null);
    }
}
