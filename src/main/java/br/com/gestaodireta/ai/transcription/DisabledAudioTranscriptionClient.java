package br.com.gestaodireta.ai.transcription;

public class DisabledAudioTranscriptionClient implements AudioTranscriptionClient {

    @Override
    public AudioTranscriptionResult transcribe(AudioTranscriptionRequest request) {
        throw new AudioTranscriptionException(
                AudioTranscriptionException.Reason.DISABLED,
                "Audio transcription is disabled",
                null);
    }

    @Override
    public String providerName() {
        return "disabled";
    }

    @Override
    public String modelName() {
        return "disabled";
    }
}
