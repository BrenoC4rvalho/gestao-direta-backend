package br.com.gestaodireta.ai.transcription;

public interface AudioTranscriptionClient {

    String transcribe(AudioTranscriptionRequest request);
}
