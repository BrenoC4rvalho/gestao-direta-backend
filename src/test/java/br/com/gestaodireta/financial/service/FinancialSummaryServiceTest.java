package br.com.gestaodireta.financial.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.financial.dto.FinancialSummaryResponse;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialCoverageStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

        FinancialSummaryResponse response = financialSummaryService.summarize(farm.getId(), 30);

        assertThat(response.farmId()).isEqualTo(farm.getId());
        assertThat(response.currentBalance()).isEqualByComparingTo("700.00");
        assertThat(response.totalReceivable()).isEqualByComparingTo("700.00");
        assertThat(response.totalPayable()).isEqualByComparingTo("1150.00");
        assertThat(response.projectedBalance()).isEqualByComparingTo("950.00");
        assertThat(response.payableInHorizon()).isEqualByComparingTo("150.00");
        assertThat(response.overduePayable()).isEqualByComparingTo("100.00");
        assertThat(response.receivableInHorizon()).isEqualByComparingTo("500.00");
        assertThat(response.financialCoverage().coveragePercentage())
                .isEqualByComparingTo("480.00");
    }

    @Test
    void shouldReturnZeroValuesWhenFarmHasNoTransactions() {
        Farm farm = saveFarm("Farm");

        FinancialSummaryResponse response = financialSummaryService.summarize(farm.getId(), 30);

        assertThat(response.currentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.totalReceivable()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.totalPayable()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.projectedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.payableInHorizon()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.overduePayable()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.receivableInHorizon()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.financialCoverage().coveragePercentage()).isNull();
        assertThat(response.financialCoverage().status())
                .isEqualTo(FinancialCoverageStatus.NO_OBLIGATIONS);
    }

    @Test
    void shouldIgnorePendingTransactionsWithoutDueDateForNext30DaysWindows() {
        Farm farm = saveFarm("Farm");
        User user = saveUser();
        saveTransaction(farm, user, TransactionType.INCOME, PaymentStatus.PENDING, "500.00", null);
        saveTransaction(farm, user, TransactionType.EXPENSE, PaymentStatus.PENDING, "300.00", null);

        FinancialSummaryResponse response = financialSummaryService.summarize(farm.getId(), 30);

        assertThat(response.totalReceivable()).isEqualByComparingTo("500.00");
        assertThat(response.totalPayable()).isEqualByComparingTo("300.00");
        assertThat(response.payableInHorizon()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.receivableInHorizon()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.financialCoverage().coveragePercentage()).isNull();
        assertThat(response.financialCoverage().status())
                .isEqualTo(FinancialCoverageStatus.NO_OBLIGATIONS);
    }

    @ParameterizedTest
    @CsvSource({"30,100", "90,200", "180,300"})
    void shouldSelectHorizonWithoutChangingCurrentPosition(int horizon, String expected) {
        Farm farm = saveFarm("Horizon");
        Farm otherFarm = saveFarm("Other");
        User user = saveUser();
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                "1000",
                TODAY.plusDays(200));
        for (int day : new int[] {10, 50, 120, 200}) {
            for (TransactionType type : TransactionType.values()) {
                saveTransaction(
                        farm, user, type, PaymentStatus.PENDING, "100", TODAY.plusDays(day));
            }
        }
        saveTransaction(
                otherFarm, user, TransactionType.EXPENSE, PaymentStatus.PENDING, "9999", TODAY);
        FinancialSummaryResponse response =
                financialSummaryService.summarize(farm.getId(), horizon);
        assertThat(response.horizonDays()).isEqualTo(horizon);
        assertThat(response.currentBalance()).isEqualByComparingTo("1000");
        assertThat(response.totalReceivable()).isEqualByComparingTo("400");
        assertThat(response.totalPayable()).isEqualByComparingTo("400");
        assertThat(response.overduePayable()).isZero();
        assertThat(response.receivableInHorizon()).isEqualByComparingTo(expected);
        assertThat(response.payableInHorizon()).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"30", "90", "180"})
    void shouldUseDisjointInclusiveDateWindowsEvenWhenStatusIsStale(int horizon) {
        Farm farm = saveFarm("Boundaries");
        User user = saveUser();
        for (TransactionType type : TransactionType.values()) {
            saveTransaction(farm, user, type, PaymentStatus.PENDING, "10", TODAY.minusDays(1));
            saveTransaction(farm, user, type, PaymentStatus.OVERDUE, "20", TODAY);
            saveTransaction(farm, user, type, PaymentStatus.PENDING, "30", TODAY.plusDays(horizon));
            saveTransaction(
                    farm, user, type, PaymentStatus.PENDING, "40", TODAY.plusDays(horizon + 1));
        }
        FinancialSummaryResponse response =
                financialSummaryService.summarize(farm.getId(), horizon);
        assertThat(response.overduePayable()).isEqualByComparingTo("10");
        assertThat(response.receivableInHorizon()).isEqualByComparingTo("50");
        assertThat(response.payableInHorizon()).isEqualByComparingTo("50");
        assertThat(response.projectedBalance()).isEqualByComparingTo("-10");
        assertThat(response.financialCoverage().status())
                .isEqualTo(FinancialCoverageStatus.INSUFFICIENT);
    }

    @Test
    void shouldCalculatePrudentCoverageWithoutOverdueReceivables() {
        Farm farm = saveFarm("Coverage");
        User user = saveUser();
        saveTransaction(farm, user, TransactionType.INCOME, PaymentStatus.PAID, "100000", TODAY);
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                "50000",
                TODAY.plusDays(10));
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.OVERDUE,
                "90000",
                TODAY.minusDays(1));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                "80000",
                TODAY.plusDays(10));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.OVERDUE,
                "40000",
                TODAY.minusDays(1));
        FinancialSummaryResponse response = financialSummaryService.summarize(farm.getId(), 30);
        assertThat(response.projectedBalance()).isEqualByComparingTo("30000");
        assertThat(response.financialCoverage().coveragePercentage()).isEqualByComparingTo("125");
        assertThat(response.financialCoverage().status())
                .isEqualTo(FinancialCoverageStatus.SUFFICIENT);
    }

    @ParameterizedTest
    @CsvSource({
        "100,100,100.00,SUFFICIENT",
        "999999,1000000,100.00,INSUFFICIENT",
        "0,100,0.00,INSUFFICIENT",
        "-100,100,-100.00,INSUFFICIENT",
        "100,300,33.33,INSUFFICIENT"
    })
    void shouldCalculateCoverageStatusBeforeRounding(
            String resources,
            String obligations,
            String percentage,
            FinancialCoverageStatus status) {
        Farm farm = saveFarm("Coverage status");
        User user = saveUser();
        BigDecimal amount = new BigDecimal(resources);
        TransactionType type =
                amount.signum() < 0 ? TransactionType.EXPENSE : TransactionType.INCOME;
        if (amount.signum() != 0) {
            saveTransaction(
                    farm, user, type, PaymentStatus.PAID, amount.abs().toPlainString(), TODAY);
        }
        saveTransaction(
                farm, user, TransactionType.EXPENSE, PaymentStatus.PENDING, obligations, TODAY);
        FinancialSummaryResponse response = financialSummaryService.summarize(farm.getId(), 30);
        assertThat(response.financialCoverage().coveragePercentage())
                .isEqualByComparingTo(percentage);
        assertThat(response.financialCoverage().status()).isEqualTo(status);
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
