package br.com.gestaodireta.ai.benchmark;

import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.util.List;

record AiBenchmarkCase(
        String id,
        String text,
        AiBenchmarkGroup group,
        AiBenchmarkExpectedOutcome expectedOutcome,
        TransactionType expectedType,
        BigDecimal expectedAmount,
        String expectedDescriptionContains,
        String expectedCategory,
        String expectedDateBehavior,
        boolean shouldCreate,
        boolean shouldRequireConfirmation,
        List<String> expectedMissingFields) {}
