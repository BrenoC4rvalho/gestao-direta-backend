package br.com.gestaodireta.financial.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.financial.dto.FinancialSummaryResponse;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
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
class FinancialSummaryServiceTest extends PostgresIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 29);

    @Autowired private FinancialSummaryService financialSummaryService;

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
    void shouldCalculateDashboardSummaryIndicators() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        User user = saveUser();
        saveTransaction(farm, user, TransactionType.INCOME, PaymentStatus.PAID, "1000.00", TODAY);
        saveTransaction(farm, user, TransactionType.EXPENSE, PaymentStatus.PAID, "300.00", TODAY);
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                "500.00",
                TODAY.plusDays(10));
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.OVERDUE,
                "200.00",
                TODAY.minusDays(2));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                "150.00",
                TODAY.plusDays(10));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                "900.00",
                TODAY.plusDays(50));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.OVERDUE,
                "100.00",
                TODAY.minusDays(3));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.CANCELED,
                "9999.00",
                TODAY.plusDays(5));
        FinancialTransaction deleted =
                saveTransaction(
                        farm,
                        user,
                        TransactionType.EXPENSE,
                        PaymentStatus.PENDING,
                        "9999.00",
                        TODAY.plusDays(5));
        deleted.setRecordStatus(FinancialRecordStatus.DELETED);
        financialTransactionRepository.save(deleted);
        saveTransaction(
                otherFarm, user, TransactionType.INCOME, PaymentStatus.PAID, "9999.00", TODAY);

        FinancialSummaryResponse response = financialSummaryService.summarize(farm.getId());

        assertThat(response.farmId()).isEqualTo(farm.getId());
        assertThat(response.currentBalance()).isEqualByComparingTo("700.00");
        assertThat(response.expectedIncome()).isEqualByComparingTo("700.00");
        assertThat(response.expectedExpense()).isEqualByComparingTo("1150.00");
        assertThat(response.projectedBalance()).isEqualByComparingTo("250.00");
        assertThat(response.payableNext30Days()).isEqualByComparingTo("150.00");
        assertThat(response.overdueExpenses()).isEqualByComparingTo("100.00");
        assertThat(response.receivableNext30Days()).isEqualByComparingTo("700.00");
        assertThat(response.cashFlowNext30Days()).isEqualByComparingTo("450.00");
    }

    @Test
    void shouldReturnZeroValuesWhenFarmHasNoTransactions() {
        Farm farm = saveFarm("Farm");

        FinancialSummaryResponse response = financialSummaryService.summarize(farm.getId());

        assertThat(response.currentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.expectedIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.expectedExpense()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.projectedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.payableNext30Days()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.overdueExpenses()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.receivableNext30Days()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.cashFlowNext30Days()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldIgnorePendingTransactionsWithoutDueDateForNext30DaysWindows() {
        Farm farm = saveFarm("Farm");
        User user = saveUser();
        saveTransaction(farm, user, TransactionType.INCOME, PaymentStatus.PENDING, "500.00", null);
        saveTransaction(farm, user, TransactionType.EXPENSE, PaymentStatus.PENDING, "300.00", null);

        FinancialSummaryResponse response = financialSummaryService.summarize(farm.getId());

        assertThat(response.expectedIncome()).isEqualByComparingTo("500.00");
        assertThat(response.expectedExpense()).isEqualByComparingTo("300.00");
        assertThat(response.payableNext30Days()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.receivableNext30Days()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.cashFlowNext30Days()).isEqualByComparingTo(BigDecimal.ZERO);
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
        user.setEmail("user@example.com");
        user.setPassword(passwordEncoder.encode("Strong1!"));
        user.setUserType(UserType.USER);
        user.setStatus(UserStatus.ACTIVE);

        return userRepository.save(user);
    }

    private FinancialTransaction saveTransaction(
            Farm farm,
            User user,
            TransactionType type,
            PaymentStatus status,
            String amount,
            LocalDate dueDate) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Transaction");
        transaction.setAmount(new BigDecimal(amount));
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setTransactionDate(dueDate == null ? TODAY : dueDate);
        transaction.setDueDate(dueDate);
        transaction.setPaidAt(
                PaymentStatus.PAID.equals(status) ? transaction.getTransactionDate() : null);
        transaction.setFarm(farm);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(FinancialRecordStatus.ACTIVE);

        return financialTransactionRepository.save(transaction);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2026-06-29T03:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }
}
