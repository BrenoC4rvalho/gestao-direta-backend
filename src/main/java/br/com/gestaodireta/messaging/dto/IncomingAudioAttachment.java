package br.com.gestaodireta.messaging.dto;

public record IncomingAudioAttachment(
        String fileId, Integer durationSeconds, String mimeType, Long fileSize) {}
