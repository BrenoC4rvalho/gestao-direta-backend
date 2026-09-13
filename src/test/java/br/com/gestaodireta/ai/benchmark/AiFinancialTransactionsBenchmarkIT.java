package br.com.gestaodireta.ai.benchmark;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import br.com.gestaodireta.ai.config.AiHealthProperties;
import br.com.gestaodireta.ai.config.AiProviderConfiguration;
import br.com.gestaodireta.ai.config.AiProviderProperties;
import br.com.gestaodireta.ai.config.FinancialExtractionProperties;
import br.com.gestaodireta.ai.infrastructure.gemini.GeminiAiProperties;
import br.com.gestaodireta.ai.infrastructure.ollama.OllamaAiProperties;
import br.com.gestaodireta.ai.service.FinancialExtractionResponseSchema;
import br.com.gestaodireta.ai.service.FinancialTransactionExtractionService;
import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionResult;
import br.com.gestaodireta.ai.service.provider.AiGenerationRequest;
import br.com.gestaodireta.ai.service.provider.AiTextGenerationClient;
import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.PendingFinancialTransactionRepository;
import br.com.gestaodireta.messaging.domain.MessagingAccount;
import br.com.gestaodireta.messaging.domain.MessagingConversation;
import br.com.gestaodireta.messaging.domain.MessagingMessage;
import br.com.gestaodireta.messaging.enumeration.MessagingAccountStatus;
import br.com.gestaodireta.messaging.enumeration.MessagingConversationStatus;
import br.com.gestaodireta.messaging.enumeration.MessagingDirection;
import br.com.gestaodireta.messaging.enumeration.MessagingMessageStatus;
import br.com.gestaodireta.messaging.enumeration.MessagingMessageType;
import br.com.gestaodireta.messaging.service.FinancialMessageEvidenceExtractor;
import br.com.gestaodireta.messaging.service.FinancialTransactionExtractionResultValidator;
import br.com.gestaodireta.messaging.service.OutgoingMessagingService;
import br.com.gestaodireta.messaging.service.TelegramFinancialExtractionProcessor;
import br.com.gestaodireta.messaging.service.TelegramFinancialMessageEligibilityValidator;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.entity.UserContact;
import br.com.gestaodireta.user.enumeration.UserContactStatus;
import br.com.gestaodireta.user.enumeration.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.client.RestClient;

class AiFinancialTransactionsBenchmarkIT {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    @Test
    void shouldRunConfiguredAiBenchmark() {
        List<AiBenchmarkCase> dataset = new AiBenchmarkDataset().load();
        int limit = integerProperty("ai.benchmark.limit", dataset.size());
        List<AiBenchmarkCase> cases = dataset.stream().limit(Math.max(0, limit)).toList();
        List<AiBenchmarkCaseResult> results = new ArrayList<>();
        for (String provider : providers()) {
            results.addAll(run(provider, cases));
        }
        new AiBenchmarkReportWriter().write(results);
        AiBenchmarkThresholds.fromSystemProperties().assertSatisfied(results);
    }

    private List<AiBenchmarkCaseResult> run(String provider, List<AiBenchmarkCase> cases) {
        try {
            BenchmarkFixture fixture = new BenchmarkFixture(provider);
            return cases.stream().map(fixture::run).toList();
        } catch (RuntimeException exception) {
            return cases.stream()
                    .map(
                            item ->
                                    new AiBenchmarkScorer()
                                            .score(
                                                    item,
                                                    provider,
                                                    "unavailable",
                                                    new AiBenchmarkActual(
                                                            null,
                                                            false,
                                                            null,
                                                            sanitize(exception),
                                                            0,
                                                            null)))
                    .toList();
        }
    }

    private List<String> providers() {
        String value =
                System.getProperty("ai.benchmark.provider", "both").trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "gemini", "ollama" -> List.of(value);
            case "both" -> List.of("gemini", "ollama");
            default ->
                    throw new IllegalArgumentException(
                            "ai.benchmark.provider must be gemini, ollama or both");
        };
    }

    private int integerProperty(String name, int defaultValue) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private static String sanitize(Throwable exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName()
                + (message == null ? "" : ": " + message.replaceAll("(?i)key=[^\\s&]+", "key=***"));
    }

    private static final class BenchmarkFixture {
        private final String provider;
        private final String model;
        private final TelegramFinancialExtractionProcessor processor;
        private final AtomicReference<FinancialTransactionExtractionResult> extraction =
                new AtomicReference<>();
        private final AtomicReference<String> rawResponse = new AtomicReference<>();
        private final AtomicReference<Boolean> pendingCreated = new AtomicReference<>(false);
        private final MessagingAccount account = account();
        private final MessagingConversation conversation = conversation();

        BenchmarkFixture(String provider) {
            this.provider = provider;
            FinancialExtractionProperties extractionProperties = extractionProperties();
            AiTextGenerationClient client = capturingClient(provider, extractionProperties);
            this.model = client.modelName();
            FinancialTransactionExtractionService extractionService =
                    spy(
                            new FinancialTransactionExtractionService(
                                    client,
                                    extractionProperties,
                                    new ObjectMapper(),
                                    CLOCK,
                                    new FinancialExtractionResponseSchema()));
            doAnswer(
                            invocation -> {
                                FinancialTransactionExtractionResult result =
                                        (FinancialTransactionExtractionResult)
                                                invocation.callRealMethod();
                                extraction.set(result);
                                return result;
                            })
                    .when(extractionService)
                    .extract(any(), any(), any());

            PendingFinancialTransactionRepository pendingRepository =
                    mock(PendingFinancialTransactionRepository.class);
            when(pendingRepository.findBySourceMessageId(any())).thenReturn(Optional.empty());
            when(pendingRepository.save(any()))
                    .thenAnswer(
                            invocation -> {
                                pendingCreated.set(true);
                                return invocation.getArgument(0);
                            });
            FinancialCategoryRepository categoryRepository =
                    mock(FinancialCategoryRepository.class);
            when(categoryRepository.findByFarmId(any(), anyBoolean(), any()))
                    .thenReturn(new PageImpl<>(categories(conversation.getFarm())));
            FarmUserRepository farmUsers = mock(FarmUserRepository.class);
            when(farmUsers.findRoleByFarmIdAndUserId(any(), any()))
                    .thenReturn(Optional.of(FarmUserRole.PRODUCER));
            FinancialMessageEvidenceExtractor evidence = new FinancialMessageEvidenceExtractor();
            processor =
                    new TelegramFinancialExtractionProcessor(
                            extractionService,
                            pendingRepository,
                            categoryRepository,
                            farmUsers,
                            mock(OutgoingMessagingService.class),
                            new TelegramFinancialMessageEligibilityValidator(evidence),
                            new FinancialTransactionExtractionResultValidator(
                                    extractionProperties, evidence),
                            CLOCK);
        }

        AiBenchmarkCaseResult run(AiBenchmarkCase benchmarkCase) {
            extraction.set(null);
            rawResponse.set(null);
            pendingCreated.set(false);
            long start = System.nanoTime();
            String error = null;
            try {
                processor.process(account, conversation, message(benchmarkCase.text()));
            } catch (RuntimeException exception) {
                error = sanitize(exception);
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            AiBenchmarkActual actual =
                    new AiBenchmarkActual(
                            extraction.get(),
                            pendingCreated.get(),
                            pendingCreated.get() ? null : blockedReason(extraction.get()),
                            error,
                            elapsed,
                            rawResponse.get());
            return new AiBenchmarkScorer().score(benchmarkCase, provider, model, actual);
        }

        private AiTextGenerationClient capturingClient(
                String configuredProvider, FinancialExtractionProperties extractionProperties) {
            AiProviderProperties providerProperties = new AiProviderProperties();
            providerProperties.setProvider(configuredProvider);
            AiTextGenerationClient delegate =
                    new AiProviderConfiguration()
                            .aiTextGenerationClient(
                                    providerProperties,
                                    ollamaProperties(),
                                    geminiProperties(),
                                    extractionProperties,
                                    healthProperties(),
                                    RestClient.builder());
            return new AiTextGenerationClient() {
                @Override
                public String generate(AiGenerationRequest request) {
                    String response = delegate.generate(request);
                    rawResponse.set(
                            response.length() > 10_000 ? response.substring(0, 10_000) : response);
                    return response;
                }

                @Override
                public String providerName() {
                    return delegate.providerName();
                }

                @Override
                public String modelName() {
                    return delegate.modelName();
                }
            };
        }

        private String blockedReason(FinancialTransactionExtractionResult result) {
            return result == null
                    ? "Ineligible message or provider/parser failure"
                    : "Validation blocked pending creation";
        }
    }

    private static FinancialExtractionProperties extractionProperties() {
        FinancialExtractionProperties properties = new FinancialExtractionProperties();
        properties.setEnabled(true);
        properties.setTimeoutSeconds(integerEnv("AI_FINANCIAL_EXTRACTION_TIMEOUT_SECONDS", 20));
        properties.setMinimumConfidence(
                doubleEnv("AI_FINANCIAL_EXTRACTION_MINIMUM_CONFIDENCE", 0.60));
        return properties;
    }

    private static AiHealthProperties healthProperties() {
        AiHealthProperties properties = new AiHealthProperties();
        properties.setTimeoutSeconds(integerEnv("AI_HEALTH_CHECK_TIMEOUT_SECONDS", 5));
        return properties;
    }

    private static GeminiAiProperties geminiProperties() {
        GeminiAiProperties properties = new GeminiAiProperties();
        properties.setApiKey(System.getenv("APP_AI_GEMINI_API_KEY"));
        properties.setModel(env("APP_AI_GEMINI_MODEL", "gemini-3.1-flash-lite"));
        return properties;
    }

    private static OllamaAiProperties ollamaProperties() {
        OllamaAiProperties properties = new OllamaAiProperties();
        properties.setBaseUrl(env("APP_AI_OLLAMA_BASE_URL", "http://localhost:11434"));
        properties.setModel(env("APP_AI_OLLAMA_MODEL", "llama3.2:3b"));
        properties.setFormat(env("APP_AI_OLLAMA_FORMAT", "json"));
        properties.setTemperature(doubleEnv("APP_AI_OLLAMA_TEMPERATURE", 0));
        return properties;
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int integerEnv(String name, int defaultValue) {
        return Integer.parseInt(env(name, String.valueOf(defaultValue)));
    }

    private static double doubleEnv(String name, double defaultValue) {
        return Double.parseDouble(env(name, String.valueOf(defaultValue)));
    }

    private static List<FinancialCategory> categories(Farm farm) {
        return List.of(
                category(farm, "Vendas", TransactionType.INCOME),
                category(farm, "Insumos", TransactionType.EXPENSE),
                category(farm, "Combustível", TransactionType.EXPENSE),
                category(farm, "Manutenção", TransactionType.EXPENSE),
                category(farm, "Utilidades", TransactionType.EXPENSE));
    }

    private static FinancialCategory category(Farm farm, String name, TransactionType type) {
        FinancialCategory category = new FinancialCategory();
        category.setFarm(farm);
        category.setName(name);
        category.setType(type);
        category.setStatus(FinancialCategoryStatus.ACTIVE);
        return category;
    }

    private static MessagingAccount account() {
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        UserContact contact = new UserContact();
        contact.setUser(user);
        contact.setStatus(UserContactStatus.ACTIVE);
        MessagingAccount account = new MessagingAccount();
        account.setStatus(MessagingAccountStatus.ACTIVE);
        account.setUserContact(contact);
        return account;
    }

    private static MessagingConversation conversation() {
        Farm farm = new Farm();
        farm.setName("Fazenda Boa Vista");
        MessagingConversation conversation = new MessagingConversation();
        conversation.setFarm(farm);
        conversation.setStatus(MessagingConversationStatus.ACTIVE);
        return conversation;
    }

    private static MessagingMessage message(String text) {
        MessagingMessage message = new MessagingMessage();
        message.setMessageType(MessagingMessageType.TEXT);
        message.setDirection(MessagingDirection.INBOUND);
        message.setStatus(MessagingMessageStatus.RECEIVED);
        message.setContent(text);
        return message;
    }
}
