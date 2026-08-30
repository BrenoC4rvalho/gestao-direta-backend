package br.com.gestaodireta.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FinancialExtractionResponseSchemaTest {

    private final FinancialExtractionResponseSchema responseSchema =
            new FinancialExtractionResponseSchema();

    @Test
    void shouldExposeFinancialSchemaWithNullableReviewFields() {
        Map<String, Object> schema = responseSchema.schema();
        Map<String, Object> properties = properties(schema);

        assertThat(schema)
                .containsEntry("type", "object")
                .containsEntry("additionalProperties", false);
        assertThat(required(schema))
                .contains(
                        "isFinancialTransaction",
                        "type",
                        "amount",
                        "description",
                        "transactionDate",
                        "categoryName",
                        "confidence",
                        "missingFields");
        assertThat(type(properties, "transactionDate")).containsExactly("string", "null");
        assertThat(type(properties, "categoryName")).containsExactly("string", "null");
        assertThat(type(properties, "type")).containsExactly("string", "null");
        assertThat(type(properties, "amount")).containsExactly("number", "null");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> properties(Map<String, Object> schema) {
        return (Map<String, Object>) schema.get("properties");
    }

    @SuppressWarnings("unchecked")
    private List<String> required(Map<String, Object> schema) {
        return (List<String>) schema.get("required");
    }

    @SuppressWarnings("unchecked")
    private List<String> type(Map<String, Object> properties, String field) {
        return (List<String>) ((Map<String, Object>) properties.get(field)).get("type");
    }
}
