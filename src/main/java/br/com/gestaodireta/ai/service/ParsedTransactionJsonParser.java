package br.com.gestaodireta.ai.service;

import br.com.gestaodireta.ai.service.dto.ParsedTransactionResponse;
import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ParsedTransactionJsonParser {

    private static final String ERROR_MESSAGE =
            "Não foi possível interpretar o texto como movimentação financeira.";

    private static final List<String> REQUIRED_FIELDS =
            List.of(
                    "type",
                    "amount",
                    "description",
                    "transactionDate",
                    "dueDate",
                    "paymentStatus",
                    "paymentMethod",
                    "categoryName",
                    "harvestSeasonName",
                    "confidence",
                    "missingFields",
                    "warnings");

    private final ObjectMapper objectMapper;

    public ParsedTransactionJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedTransactionResponse parse(Long farmId, String rawResponse) {
        JsonNode root = readJson(rawResponse);
        validateRequiredFields(root);

        try {
            PaymentStatus paymentStatus = enumValue(PaymentStatus.class, root, "paymentStatus");

            if (PaymentStatus.CANCELED.equals(paymentStatus)) {
                throw new AiParsingException(ERROR_MESSAGE);
            }

            return new ParsedTransactionResponse(
                    farmId,
                    enumValue(TransactionType.class, root, "type"),
                    decimalValue(root, "amount"),
                    textValue(root, "description"),
                    dateValue(root, "transactionDate"),
                    nullableDateValue(root, "dueDate"),
                    paymentStatus,
                    enumValue(PaymentMethod.class, root, "paymentMethod"),
                    nullableTextValue(root, "categoryName"),
                    nullableTextValue(root, "harvestSeasonName"),
                    decimalValue(root, "confidence"),
                    stringList(root, "missingFields"),
                    stringList(root, "warnings"));
        } catch (AiParsingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiParsingException(ERROR_MESSAGE, exception);
        }
    }

    private JsonNode readJson(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new AiParsingException(ERROR_MESSAGE);
        }

        try {
            return objectMapper.readTree(rawResponse);
        } catch (IOException exception) {
            return readExtractedJson(rawResponse, exception);
        }
    }

    private JsonNode readExtractedJson(String rawResponse, IOException originalException) {
        String extractedJson = extractFirstJsonObject(rawResponse);

        try {
            return objectMapper.readTree(extractedJson);
        } catch (IOException exception) {
            throw new AiParsingException(ERROR_MESSAGE, originalException);
        }
    }

    private String extractFirstJsonObject(String rawResponse) {
        int start = rawResponse.indexOf('{');

        if (start < 0) {
            throw new AiParsingException(ERROR_MESSAGE);
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int index = start; index < rawResponse.length(); index++) {
            char current = rawResponse.charAt(index);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (current == '\\' && inString) {
                escaped = true;
                continue;
            }

            if (current == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (current == '{') {
                depth++;
            }

            if (current == '}') {
                depth--;
            }

            if (depth == 0) {
                return rawResponse.substring(start, index + 1);
            }
        }

        throw new AiParsingException(ERROR_MESSAGE);
    }

    private void validateRequiredFields(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new AiParsingException(ERROR_MESSAGE);
        }

        for (String field : REQUIRED_FIELDS) {
            if (!root.has(field)) {
                throw new AiParsingException(ERROR_MESSAGE);
            }
        }
    }

    private <T extends Enum<T>> T enumValue(Class<T> enumClass, JsonNode root, String field) {
        try {
            return Enum.valueOf(enumClass, textValue(root, field));
        } catch (IllegalArgumentException exception) {
            throw new AiParsingException(ERROR_MESSAGE, exception);
        }
    }

    private BigDecimal decimalValue(JsonNode root, String field) {
        JsonNode node = root.get(field);

        if (node == null || !node.isNumber()) {
            throw new AiParsingException(ERROR_MESSAGE);
        }

        return node.decimalValue();
    }

    private String textValue(JsonNode root, String field) {
        JsonNode node = root.get(field);

        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new AiParsingException(ERROR_MESSAGE);
        }

        return node.asText();
    }

    private String nullableTextValue(JsonNode root, String field) {
        JsonNode node = root.get(field);

        if (node == null || node.isNull()) {
            return null;
        }

        if (!node.isTextual()) {
            throw new AiParsingException(ERROR_MESSAGE);
        }

        return node.asText();
    }

    private LocalDate dateValue(JsonNode root, String field) {
        try {
            return LocalDate.parse(textValue(root, field));
        } catch (DateTimeParseException exception) {
            throw new AiParsingException(ERROR_MESSAGE, exception);
        }
    }

    private LocalDate nullableDateValue(JsonNode root, String field) {
        JsonNode node = root.get(field);

        if (node == null || node.isNull()) {
            return null;
        }

        if (!node.isTextual()) {
            throw new AiParsingException(ERROR_MESSAGE);
        }

        try {
            return LocalDate.parse(node.asText());
        } catch (DateTimeParseException exception) {
            throw new AiParsingException(ERROR_MESSAGE, exception);
        }
    }

    private List<String> stringList(JsonNode root, String field) {
        JsonNode node = root.get(field);

        if (node == null || !node.isArray()) {
            throw new AiParsingException(ERROR_MESSAGE);
        }

        List<String> values = new ArrayList<>();

        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw new AiParsingException(ERROR_MESSAGE);
            }

            values.add(item.asText());
        }

        return values;
    }
}
