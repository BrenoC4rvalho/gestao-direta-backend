package br.com.gestaodireta.ai.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class AiBenchmarkReportWriter {
    private static final Path OUTPUT = Path.of("target", "ai-benchmark");

    private final Path output;

    private final ObjectMapper objectMapper;

    AiBenchmarkReportWriter() {
        this(OUTPUT, configuredObjectMapper());
    }

    AiBenchmarkReportWriter(Path output, ObjectMapper objectMapper) {
        this.output = output;
        this.objectMapper = objectMapper;
    }

    void write(List<AiBenchmarkCaseResult> results) {
        try {
            Files.createDirectories(output);
            Map<String, AiBenchmarkMetrics> metrics =
                    results.stream()
                            .collect(
                                    Collectors.groupingBy(
                                            AiBenchmarkCaseResult::provider,
                                            Collectors.collectingAndThen(
                                                    Collectors.toList(),
                                                    values ->
                                                            AiBenchmarkMetrics.from(
                                                                    values.getFirst().provider(),
                                                                    values))));
            objectMapper.writeValue(output.resolve("summary.json").toFile(), metrics);
            writeJsonLines(results);
            writeCsv(results);
            Files.writeString(
                    output.resolve("report.html"), html(metrics, results), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write AI benchmark report", exception);
        }
    }

    private void writeJsonLines(List<AiBenchmarkCaseResult> results) throws IOException {
        StringBuilder content = new StringBuilder();
        for (AiBenchmarkCaseResult result : results) {
            content.append(objectMapper.writeValueAsString(result)).append('\n');
        }
        Files.writeString(output.resolve("results.jsonl"), content, StandardCharsets.UTF_8);
    }

    private void writeCsv(List<AiBenchmarkCaseResult> results) throws IOException {
        StringBuilder content =
                new StringBuilder(
                        "id,text,provider,model,group,expectedOutcome,actualOutcome,expectedType,actualType,expectedAmount,actualAmount,createdPending,missingFields,elapsedMillis,criticalFailure,failureReason\n");
        for (AiBenchmarkCaseResult result : results) {
            AiBenchmarkCase item = result.benchmarkCase();
            var extraction = result.extraction();
            content.append(csv(item.id()))
                    .append(',')
                    .append(csv(item.text()))
                    .append(',')
                    .append(csv(result.provider()))
                    .append(',')
                    .append(csv(result.model()))
                    .append(',')
                    .append(item.group())
                    .append(',')
                    .append(item.expectedOutcome())
                    .append(',')
                    .append(result.resultType())
                    .append(',')
                    .append(csv(item.expectedType()))
                    .append(',')
                    .append(csv(extraction == null ? null : extraction.type()))
                    .append(',')
                    .append(csv(item.expectedAmount()))
                    .append(',')
                    .append(csv(extraction == null ? null : extraction.amount()))
                    .append(',')
                    .append(result.actual().createdPending())
                    .append(',')
                    .append(csv(extraction == null ? null : extraction.missingFields()))
                    .append(',')
                    .append(result.actual().elapsedMillis())
                    .append(',')
                    .append(result.criticalFailure())
                    .append(',')
                    .append(csv(result.failureReason()))
                    .append('\n');
        }
        Files.writeString(output.resolve("results.csv"), content, StandardCharsets.UTF_8);
    }

    private String html(
            Map<String, AiBenchmarkMetrics> metrics, List<AiBenchmarkCaseResult> results) {
        String summaries =
                metrics.values().stream().map(this::summary).collect(Collectors.joining());
        String rows = results.stream().map(this::row).collect(Collectors.joining());
        return """
                <!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><title>Benchmark IA</title>
                <style>body{font:14px system-ui;margin:2rem;color:#1f2937}table{border-collapse:collapse;width:100%%}th,td{padding:.5rem;border:1px solid #d1d5db;text-align:left}th{background:#f3f4f6}.critical{background:#fee2e2}.filters button{margin-right:.5rem}</style>
                </head><body><h1>BENCHMARK IA — MOVIMENTAÇÕES</h1>%s<h2>COMPARAÇÃO</h2><table><thead><tr><th>Provider</th><th>Completas</th><th>Incompletas</th><th>Inválidas</th><th>Hallucinations</th></tr></thead><tbody>%s</tbody></table>
                <h2>Casos</h2><div class="filters"><button onclick="filter('')">Todos</button><button onclick="filter('ENTRY')">ENTRY</button><button onclick="filter('EXIT')">EXIT</button><button onclick="filter('INVALID')">INVALID</button><button onclick="filter('INCOMPLETE')">INCOMPLETE</button><button onclick="filter('CRITICAL')">Críticos</button></div>
                <table><thead><tr><th>ID</th><th>Provider</th><th>Grupo</th><th>Texto</th><th>Resultado</th><th>Tempo</th><th>Motivo</th></tr></thead><tbody id="cases">%s</tbody></table>
                <script>function filter(value){for(const row of document.querySelectorAll('#cases tr'))row.hidden=value&& !row.dataset.filter.includes(value)}</script></body></html>
                """
                .formatted(summaries, comparison(metrics), rows);
    }

    private String summary(AiBenchmarkMetrics metric) {
        return """
                <section><h2>%s</h2><p>Completas: %d/%d (%.1f%%)<br>Entradas: %d/%d<br>Saídas: %d/%d<br>Incompletas corretamente bloqueadas: %d/%d<br>Inválidas corretamente rejeitadas: %d/%d<br>Hallucinations: %d<br>Erros técnicos: %d<br>Latência média: %.0f ms; p50: %d ms; p95: %d ms</p></section>
                """
                .formatted(
                        escape(metric.provider()),
                        metric.completeCorrect(),
                        metric.completeTotal(),
                        metric.completeAccuracy(),
                        metric.entryCorrect(),
                        metric.entryTotal(),
                        metric.exitCorrect(),
                        metric.exitTotal(),
                        metric.incompleteCorrect(),
                        metric.incompleteTotal(),
                        metric.invalidCorrect(),
                        metric.invalidTotal(),
                        metric.hallucinations(),
                        metric.technicalErrors(),
                        metric.averageLatencyMillis(),
                        metric.p50LatencyMillis(),
                        metric.p95LatencyMillis());
    }

    private String comparison(Map<String, AiBenchmarkMetrics> metrics) {
        return metrics.values().stream()
                .map(
                        metric ->
                                "<tr><td>%s</td><td>%.1f%%</td><td>%.1f%%</td><td>%.1f%%</td><td>%d</td></tr>"
                                        .formatted(
                                                escape(metric.provider()),
                                                metric.completeAccuracy(),
                                                metric.incompleteBlockingAccuracy(),
                                                metric.invalidRejectionAccuracy(),
                                                metric.hallucinations()))
                .collect(Collectors.joining());
    }

    private String row(AiBenchmarkCaseResult result) {
        return "<tr class=\"%s\" data-filter=\"%s %s %s\"><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%d ms</td><td>%s</td></tr>"
                .formatted(
                        result.criticalFailure() ? "critical" : "",
                        result.benchmarkCase().group(),
                        result.provider(),
                        result.criticalFailure() ? "CRITICAL" : "",
                        escape(result.benchmarkCase().id()),
                        escape(result.provider()),
                        result.benchmarkCase().group(),
                        escape(result.benchmarkCase().text()),
                        result.resultType(),
                        result.actual().elapsedMillis(),
                        escape(result.failureReason()));
    }

    private String csv(Object value) {
        return value == null ? "" : "\"" + value.toString().replace("\"", "\"\"") + "\"";
    }

    private String escape(String value) {
        return value == null
                ? ""
                : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static ObjectMapper configuredObjectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }
}
