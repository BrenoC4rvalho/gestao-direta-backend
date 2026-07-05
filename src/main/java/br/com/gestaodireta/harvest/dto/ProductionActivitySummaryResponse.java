package br.com.gestaodireta.harvest.dto;

public record ProductionActivitySummaryResponse(
        Long farmId, Long totalCount, Long activeCount, Long inactiveCount, Long inProgressCount) {}
