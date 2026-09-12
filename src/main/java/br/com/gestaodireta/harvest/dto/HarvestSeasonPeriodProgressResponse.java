package br.com.gestaodireta.harvest.dto;

public record HarvestSeasonPeriodProgressResponse(
        Integer percentage, Long elapsedDays, Long totalDays, Long remainingDays) {}
