package br.com.gestaodireta.financial.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.financial.dto.FinancialAlertsResponse;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class FinancialAlertServiceTest extends PostgresIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 5);

    @Autowired private FinancialAlertService financialAlertService;

    @Autowired private FinancialTransactionRepository financialTransactionRepository;

    @Autowired private FinancialCategoryRepository financialCategoryRepository;

    @Autowired private FarmUserRepository farmUserRepository;

    @Autowired private FarmRepository farmRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        financialTransactionRepository.deleteAll();
        financialCategoryRepository.deleteAll();
        farmUserRepository.deleteAll();
        farmRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldReturnFinancialAlertsForSelectedFarm() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        User user = saveUser();
        FinancialCategory category = saveCategory(farm, "Insumos");

        FinancialTransaction pendingOverdue =
                saveTransaction(
                        farm,
                        user,
                        category,
                        "Boleto fornecedor AgroSul",
                        TransactionType.EXPENSE,
                        PaymentStatus.PENDING,
                        "3200.00",
                        TODAY.minusDays(3),
                        FinancialRecordStatus.ACTIVE);
        FinancialTransaction overdue =
                saveTransaction(
                        farm,
                        user,
                        category,
                        "Parcela oficina trator",
                        TransactionType.EXPENSE,
                        PaymentStatus.OVERDUE,
                        "1250.00",
                        TODAY.minusDays(1),
                        FinancialRecordStatus.ACTIVE);
        saveTransaction(
                farm,
                user,
                category,
                "Conta vencendo hoje",
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                "1000.00",
                TODAY,
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                farm,
                user,
                category,
                "Conta em tres dias",
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                "2000.00",
                TODAY.plusDays(3),
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                farm,
                user,
                category,
                "Conta em sete dias",
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                "3000.00",
                TODAY.plusDays(7),
                FinancialRecordStatus.ACTIVE);
        saveIgnoredTransactions(farm, otherFarm, user, category);

        FinancialAlertsResponse response = financialAlertService.getAlerts(farm.getId());

        assertThat(response.farmId()).isEqualTo(farm.getId());
        assertThat(response.overdueBills()).hasSize(2);
        assertThat(response.overdueBills().get(0).transactionId())
                .isEqualTo(pendingOverdue.getId());
        assertThat(response.overdueBills().get(0).categoryName()).isEqualTo("Insumos");
        assertThat(response.overdueBills().get(0).amount()).isEqualByComparingTo("3200.00");
        assertThat(response.overdueBills().get(0).daysOverdue()).isEqualTo(3);
        assertThat(response.overdueBills().get(1).transactionId()).isEqualTo(overdue.getId());
        assertThat(response.overdueBills().get(1).daysOverdue()).isEqualTo(1);
        assertThat(response.dueToday().count()).isEqualTo(1);
        assertThat(response.dueToday().totalAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.dueNext7Days().count()).isEqualTo(2);
        assertThat(response.dueNext7Days().totalAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void shouldReturnEmptyAlertsWhenFarmHasNoMatchingTransactions() {
        Farm farm = saveFarm("Farm");

        FinancialAlertsResponse response = financialAlertService.getAlerts(farm.getId());

        assertThat(response.overdueBills()).isEmpty();
        assertThat(response.dueToday().count()).isZero();
        assertThat(response.dueToday().totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.dueNext7Days().count()).isZero();
        assertThat(response.dueNext7Days().totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private void saveIgnoredTransactions(
            Farm farm, Farm otherFarm, User user, FinancialCategory category) {
        saveTransaction(
                farm,
                user,
                category,
                "Conta em oito dias",
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                "9000.00",
                TODAY.plusDays(8),
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                farm,
                user,
                category,
                "Despesa paga",
                TransactionType.EXPENSE,
                PaymentStatus.PAID,
                "9000.00",
                TODAY,
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                farm,
                user,
                category,
                "Despesa cancelada",
                TransactionType.EXPENSE,
                PaymentStatus.CANCELED,
                "9000.00",
                TODAY,
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                farm,
                user,
                category,
                "Despesa removida",
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                "9000.00",
                TODAY,
                FinancialRecordStatus.DELETED);
        saveTransaction(
                farm,
                user,
                category,
                "Receita",
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                "9000.00",
                TODAY,
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                otherFarm,
                user,
                category,
                "Outra fazenda",
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                "9000.00",
                TODAY,
                FinancialRecordStatus.ACTIVE);
        saveTransaction(
                farm,
                user,
                category,
                "Sem vencimento",
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                "9000.00",
                null,
                FinancialRecordStatus.ACTIVE);
    }

    private Farm saveFarm(String name) {
        Farm farm = new Farm();
        farm.setName(name);
        farm.setStatus(FarmStatus.ACTIVE);

        return farmRepository.save(farm);
    }

    private User saveUser() {
        User user = new User();
        user.setName("User");
        user.setEmail("alerts-user@example.com");
        user.setPassword(passwordEncoder.encode("Strong1!"));
        user.setUserType(UserType.USER);
        user.setStatus(UserStatus.ACTIVE);

        return userRepository.save(user);
    }

    private FinancialCategory saveCategory(Farm farm, String name) {
        FinancialCategory category = new FinancialCategory();
        category.setName(name);
        category.setType(TransactionType.EXPENSE);
        category.setFarm(farm);
        category.setStatus(FinancialCategoryStatus.ACTIVE);

        return financialCategoryRepository.save(category);
    }

    private FinancialTransaction saveTransaction(
            Farm farm,
            User user,
            FinancialCategory category,
            String description,
            TransactionType type,
            PaymentStatus status,
            String amount,
            LocalDate dueDate,
            FinancialRecordStatus recordStatus) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription(description);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setTransactionDate(dueDate == null ? TODAY : dueDate);
        transaction.setDueDate(dueDate);
        transaction.setPaidAt(
                PaymentStatus.PAID.equals(status) ? transaction.getTransactionDate() : null);
        transaction.setFarm(farm);
        transaction.setCategory(category);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(recordStatus);

        return financialTransactionRepository.save(transaction);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2026-07-05T03:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }
}
