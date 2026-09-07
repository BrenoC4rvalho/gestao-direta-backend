package br.com.gestaodireta.ai.transcription;

public interface AudioTranscriptionClient {

    AudioTranscriptionResult transcribe(AudioTranscriptionRequest request);

    String providerName();

    String modelName();

    default void probe() {}
}
