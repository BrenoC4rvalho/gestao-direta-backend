package br.com.gestaodireta.financial.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.financial.dto.FinancialReportFilter;
import br.com.gestaodireta.financial.dto.FinancialReportResponse;
import br.com.gestaodireta.financial.dto.FinancialReportTransactionResponse;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.FinancialReportBasis;
import br.com.gestaodireta.financial.enumeration.FinancialReportGranularity;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.shared.response.PageResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class FinancialReportServiceTest extends PostgresIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 30);

    @Autowired private FinancialReportService financialReportService;

    @Autowired private FinancialTransactionRepository financialTransactionRepository;

    @Autowired private FarmRepository farmRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        financialTransactionRepository.deleteAll();
        farmRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldUseCashReferenceDatesAndExposeUnallocatedTransactions() {
        Farm farm = saveFarm();
        User user = saveUser();
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 5),
                null,
                LocalDate.of(2026, 2, 2));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("25.00"),
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 2, 8),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("30.00"),
                LocalDate.of(2026, 1, 5),
                null,
                null);

        FinancialReportFilter filter =
                new FinancialReportFilter(
                        farm.getId(),
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 2, 28),
                        FinancialReportBasis.CASH,
                        null,
                        null);

        FinancialReportResponse report = financialReportService.getReport(filter);
        PageResponse<FinancialReportTransactionResponse> transactions =
                financialReportService.findTransactions(filter, 0, 20, "referenceDate", "ASC");

        assertThat(report.summary().totalIncome()).isEqualByComparingTo("100.00");
        assertThat(report.summary().totalExpense()).isEqualByComparingTo("25.00");
        assertThat(report.summary().netBalance()).isEqualByComparingTo("75.00");
        assertThat(report.unallocated().expense()).isEqualByComparingTo("30.00");
        assertThat(report.evolution()).hasSize(2);
        assertThat(report.evolution().get(0).transactionCount()).isZero();
        assertThat(report.evolution().get(1).transactionCount()).isEqualTo(2);
        assertThat(transactions.content())
                .extracting(FinancialReportTransactionResponse::referenceDate)
                .containsExactly(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 8));
    }

    @Test
    void shouldUseTransactionDateForAllAccrualStatuses() {
        Farm farm = saveFarm();
        User user = saveUser();
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.OVERDUE,
                new BigDecimal("70.00"),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 3, 1),
                null);

        FinancialReportResponse report =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 1, 31),
                                FinancialReportBasis.ACCRUAL,
                                null,
                                null));

        assertThat(report.summary().totalExpense()).isEqualByComparingTo("70.00");
        assertThat(report.evolution().getFirst().transactionCount()).isEqualTo(1);
    }

    @Test
    void shouldBuildCommitmentsAsOfTheReportEndDate() {
        Farm farm = saveFarm();
        User user = saveUser();
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("100.00"),
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 31),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("40.00"),
                TODAY,
                TODAY,
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("60.00"),
                TODAY,
                LocalDate.of(2026, 8, 31),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("30.00"),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 1),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("25.00"),
                LocalDate.of(2026, 8, 20),
                null,
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                new BigDecimal("80.00"),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 5));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PAID,
                new BigDecimal("90.00"),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 20));

        FinancialReportResponse report =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 8, 1),
                                LocalDate.of(2026, 8, 31),
                                FinancialReportBasis.ACCRUAL,
                                null,
                                null));

        assertThat(report.summary().totalIncome()).isEqualByComparingTo("165.00");
        assertThat(report.commitments().accountsReceivable()).isEqualByComparingTo("265.00");
        assertThat(report.commitments().accountsPayable()).isEqualByComparingTo("40.00");
        assertThat(report.commitments().overdueReceivableAmount()).isEqualByComparingTo("180.00");
        assertThat(report.commitments().overdueReceivableCount()).isEqualTo(2);
        assertThat(report.commitments().overduePayableAmount()).isEqualByComparingTo("40.00");
        assertThat(report.commitments().overduePayableCount()).isEqualTo(1);
        assertThat(report.commitments().next30DaysAvailable()).isFalse();
        assertThat(report.commitments().next30DaysReceivable()).isNull();
        assertThat(report.commitments().next30DaysPayable()).isNull();
    }

    @Test
    void shouldExposeTheFixedNextThirtyDayWindowOnlyWhenAvailable() {
        Farm farm = saveFarm();
        User user = saveUser();
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("10.00"),
                TODAY,
                TODAY,
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("20.00"),
                TODAY,
                TODAY.plusDays(15),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("30.00"),
                TODAY,
                TODAY.plusDays(30),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("40.00"),
                TODAY,
                TODAY.plusDays(31),
                null);

        FinancialReportResponse unavailable = reportForEndDate(farm, TODAY.plusDays(29));
        FinancialReportResponse available = reportForEndDate(farm, TODAY.plusDays(30));
        FinancialReportResponse future = reportForEndDate(farm, TODAY.plusDays(31));

        assertThat(unavailable.commitments().next30DaysAvailable()).isFalse();
        assertThat(unavailable.commitments().next30DaysReceivable()).isNull();
        assertThat(unavailable.commitments().next30DaysPayable()).isNull();
        assertThat(available.commitments().next30DaysAvailable()).isTrue();
        assertThat(available.commitments().next30DaysReceivable()).isEqualByComparingTo("40.00");
        assertThat(available.commitments().next30DaysPayable()).isEqualByComparingTo("20.00");
        assertThat(future.commitments().next30DaysAvailable()).isTrue();
    }

    @Test
    void shouldClassifyCashEvolutionStatesWithoutDoubleCounting() {
        Farm farm = saveFarm();
        User user = saveUser();
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                new BigDecimal("40.00"),
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 8, 15));
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("20.00"),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 9, 20),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                new BigDecimal("10.00"),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 9, 15));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PAID,
                new BigDecimal("30.00"),
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 8, 20));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("15.00"),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 9, 10),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("5.00"),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 20),
                null);

        FinancialReportResponse report =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 7, 1),
                                LocalDate.of(2026, 9, 30),
                                FinancialReportBasis.CASH,
                                null,
                                null,
                                FinancialReportGranularity.MONTHLY));

        assertThat(report.evolution()).hasSize(3);
        assertThat(report.evolution().get(0).overdueIncome()).isEqualByComparingTo("10.00");
        assertThat(report.evolution().get(0).overdueExpense()).isEqualByComparingTo("5.00");
        assertThat(report.evolution().get(0).overdueIncomeCount()).isEqualTo(1);
        assertThat(report.evolution().get(1).realizedIncome()).isEqualByComparingTo("40.00");
        assertThat(report.evolution().get(1).realizedExpense()).isEqualByComparingTo("30.00");
        assertThat(report.evolution().get(1).realizedResult()).isEqualByComparingTo("10.00");
        assertThat(report.evolution().get(2).projectedIncome()).isEqualByComparingTo("20.00");
        assertThat(report.evolution().get(2).projectedExpense()).isEqualByComparingTo("15.00");
        assertThat(report.evolution().get(2).overdueIncome()).isZero();
        assertThat(report.evolution().get(2).realizedIncome()).isZero();
    }

    @Test
    void shouldAggregateQuarterlyEvolutionAndFillMissingQuarters() {
        Farm farm = saveFarm();
        User user = saveUser();
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                new BigDecimal("100.00"),
                LocalDate.of(2026, 4, 10),
                LocalDate.of(2026, 4, 10),
                LocalDate.of(2026, 4, 10));
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PAID,
                new BigDecimal("25.00"),
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 10));

        FinancialReportResponse report =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                LocalDate.of(2026, 1, 15),
                                LocalDate.of(2026, 12, 10),
                                FinancialReportBasis.CASH,
                                null,
                                null,
                                FinancialReportGranularity.QUARTERLY));

        assertThat(report.evolution()).hasSize(4);
        assertThat(report.evolution().get(0).label()).isEqualTo("1º tri");
        assertThat(report.evolution().get(0).periodStart()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(report.evolution().get(0).transactionCount()).isZero();
        assertThat(report.evolution().get(1).realizedIncome()).isEqualByComparingTo("100.00");
        assertThat(report.evolution().get(1).realizedExpense()).isEqualByComparingTo("25.00");
        assertThat(report.evolution().get(1).realizedResult()).isEqualByComparingTo("75.00");
        assertThat(report.evolution().get(3).periodEnd()).isEqualTo(LocalDate.of(2026, 12, 10));
    }

    private FinancialReportResponse reportForEndDate(Farm farm, LocalDate endDate) {
        return financialReportService.getReport(
                new FinancialReportFilter(
                        farm.getId(), TODAY, endDate, FinancialReportBasis.ACCRUAL, null, null));
    }

    private Farm saveFarm() {
        Farm farm = new Farm();
        farm.setName("Farm");
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

    private void saveTransaction(
            Farm farm,
            User user,
            TransactionType type,
            PaymentStatus status,
            BigDecimal amount,
            LocalDate transactionDate,
            LocalDate dueDate,
            LocalDate paidAt) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Transaction");
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setTransactionDate(transactionDate);
        transaction.setDueDate(dueDate);
        transaction.setPaidAt(paidAt);
        transaction.setFarm(farm);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(FinancialRecordStatus.ACTIVE);
        financialTransactionRepository.save(transaction);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2026-08-30T03:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }
}
