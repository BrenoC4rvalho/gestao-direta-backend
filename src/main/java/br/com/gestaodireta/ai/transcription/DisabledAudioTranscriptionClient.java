package br.com.gestaodireta.ai.transcription;

public class DisabledAudioTranscriptionClient implements AudioTranscriptionClient {

    @Override
    public String transcribe(AudioTranscriptionRequest request) {
        throw new AudioTranscriptionException(
                AudioTranscriptionException.Reason.DISABLED,
                "Audio transcription is disabled",
                null);
    }
}
