package br.com.gestaodireta.ai.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FinancialExtractionResponseSchema {

    public Map<String, Object> schema() {
        return Map.of(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                Map.of(
                        "isFinancialTransaction",
                                Map.of(
                                        "type",
                                        "boolean",
                                        "description",
                                        "Whether the text declares a financial transaction."),
                        "status",
                                Map.of(
                                        "type",
                                        "string",
                                        "enum",
                                        List.of(
                                                "VALID",
                                                "INCOMPLETE",
                                                "INVALID",
                                                "MULTIPLE_TRANSACTIONS")),
                        "type",
                                Map.of(
                                        "type",
                                        List.of("string", "null"),
                                        "enum",
                                        Arrays.asList("INCOME", "EXPENSE", null)),
                        "amount",
                                Map.of(
                                        "type",
                                        List.of("number", "null"),
                                        "description",
                                        "The BRL monetary amount, never a physical quantity."),
                        "transactionDate",
                                Map.of("type", List.of("string", "null"), "format", "date"),
                        "dueDate",
                                Map.of(
                                        "type", List.of("string", "null"),
                                        "format", "date",
                                        "description",
                                        "The explicitly declared payment or receipt due date; never infer it."),
                        "description",
                                Map.of(
                                        "type",
                                        List.of("string", "null"),
                                        "description",
                                        "The declared item, service or purpose; never invent it."),
                        "categoryName", Map.of("type", List.of("string", "null")),
                        "confidence", Map.of("type", "number", "minimum", 0, "maximum", 1),
                        "missingFields",
                                Map.of("type", "array", "items", Map.of("type", "string"))),
                "required",
                List.of(
                        "isFinancialTransaction",
                        "status",
                        "type",
                        "amount",
                        "transactionDate",
                        "dueDate",
                        "description",
                        "categoryName",
                        "confidence",
                        "missingFields"));
    }
}
