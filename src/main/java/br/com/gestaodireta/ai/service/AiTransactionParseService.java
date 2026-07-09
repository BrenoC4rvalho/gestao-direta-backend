package br.com.gestaodireta.ai.service;

import br.com.gestaodireta.ai.service.dto.ParseTransactionTextRequest;
import br.com.gestaodireta.ai.service.dto.ParsedTransactionResponse;
import br.com.gestaodireta.ai.service.provider.AiGenerationRequest;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
import br.com.gestaodireta.shared.security.SecurityUtils;
import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiTransactionParseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiTransactionParseService.class);

    private final AiTextGenerationClient aiTextGenerationClient;

    private final TransactionTextPromptBuilder promptBuilder;

    private final ParsedTransactionJsonParser jsonParser;

    private final Clock clock;

    public AiTransactionParseService(
            AiTextGenerationClient aiTextGenerationClient,
            TransactionTextPromptBuilder promptBuilder,
            ParsedTransactionJsonParser jsonParser,
            Clock clock) {
        this.aiTextGenerationClient = aiTextGenerationClient;
        this.promptBuilder = promptBuilder;
        this.jsonParser = jsonParser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ParsedTransactionResponse parse(ParseTransactionTextRequest request) {
        long startNanos = System.nanoTime();
        Long userId = SecurityUtils.getAuthenticatedUserId();

        try {
            LocalDate currentDate = LocalDate.now(clock);
            String prompt = promptBuilder.build(request.text(), currentDate);
            String generatedText = aiTextGenerationClient.generate(new AiGenerationRequest(prompt));
            ParsedTransactionResponse response = jsonParser.parse(request.farmId(), generatedText);

            logResult(userId, request.farmId(), startNanos, true);

            return response;
        } catch (RuntimeException exception) {
            logResult(userId, request.farmId(), startNanos, false);
            throw exception;
        }
    }

    private void logResult(Long userId, Long farmId, long startNanos, boolean success) {
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        LOGGER.info(
                "AI transaction parse provider={} userId={} farmId={} success={} elapsedMs={}",
                aiTextGenerationClient.providerName(),
                userId,
                farmId,
                success,
                elapsedMillis);
    }
}
