package br.com.gestaodireta.ai.transcription;

public record AudioTranscriptionRequest(
        byte[] audioBytes, String mimeType, String updateId, String sourceMessageId) {}
