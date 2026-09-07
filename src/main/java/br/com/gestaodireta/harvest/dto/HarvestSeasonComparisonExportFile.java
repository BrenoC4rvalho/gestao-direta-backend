package br.com.gestaodireta.harvest.dto;

public record HarvestSeasonComparisonExportFile(
        String filename, String contentType, byte[] content) {}
