package br.com.gestaodireta.ai.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionResult;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiBenchmarkReportWriterTest {

    @TempDir Path output;

    @Test
    void shouldWriteAllReportsWithIsoLocalDate() throws Exception {
        AiBenchmarkCaseResult result = resultWithTransactionDate();

        new AiBenchmarkReportWriter(output, configuredObjectMapper()).write(List.of(result));

        Path jsonl = output.resolve("results.jsonl");
        Path summary = output.resolve("summary.json");
        Path csv = output.resolve("results.csv");
        Path html = output.resolve("report.html");
        assertThat(jsonl).exists();
        assertThat(summary).exists();
        assertThat(csv).exists();
        assertThat(html).exists();
        JsonNode jsonlResult = configuredObjectMapper().readTree(Files.readString(jsonl));
        assertThat(jsonlResult.path("actual").path("extraction").path("transactionDate").asText())
                .isEqualTo("2026-09-13");
        JsonNode summaryJson = configuredObjectMapper().readTree(Files.readString(summary));
        assertThat(summaryJson.path("gemini").path("total").asInt()).isEqualTo(1);
        assertThat(Files.readString(csv)).contains("ENTRY-01", "1250.00");
        assertThat(Files.readString(html)).contains("BENCHMARK IA — MOVIMENTAÇÕES", "ENTRY-01");
    }

    private AiBenchmarkCaseResult resultWithTransactionDate() {
        AiBenchmarkCase benchmarkCase =
                new AiBenchmarkCase(
                        "ENTRY-01",
                        "Recebi R$ 1.250 pela venda de café.",
                        AiBenchmarkGroup.ENTRY,
                        AiBenchmarkExpectedOutcome.CREATE_PENDING,
                        TransactionType.INCOME,
                        new BigDecimal("1250.00"),
                        "Venda",
                        null,
                        "DECLARED",
                        true,
                        true,
                        List.of());
        FinancialTransactionExtractionResult extraction =
                new FinancialTransactionExtractionResult(
                        true,
                        TransactionType.INCOME,
                        new BigDecimal("1250.00"),
                        LocalDate.of(2026, 9, 13),
                        "Venda de café",
                        "Vendas",
                        new BigDecimal("0.95"),
                        List.of());
        AiBenchmarkActual actual = new AiBenchmarkActual(extraction, true, null, null, 10, null);
        return new AiBenchmarkCaseResult(
                benchmarkCase,
                "gemini",
                "gemini-test",
                AiBenchmarkResultType.CORRECT_CREATED,
                false,
                null,
                actual);
    }

    private ObjectMapper configuredObjectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(
                        com.fasterxml.jackson.databind.SerializationFeature
                                .WRITE_DATES_AS_TIMESTAMPS);
    }
}
