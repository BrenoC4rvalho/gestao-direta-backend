package br.com.gestaodireta.harvest.dto;

public record HarvestSeasonStatusContextResponse(
        Long daysUntilStart, boolean startDatePassed, Long daysSinceEnd) {}
