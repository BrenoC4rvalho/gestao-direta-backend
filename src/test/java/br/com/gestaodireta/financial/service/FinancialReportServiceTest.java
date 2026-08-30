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
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class FinancialReportServiceTest extends PostgresIntegrationTest {

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
    void shouldSummarizeOpenOverdueAndNextThirtyDayCommitments() {
        Farm farm = saveFarm();
        User user = saveUser();
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("100.00"),
                today,
                today.minusDays(1),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("40.00"),
                today,
                today,
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("60.00"),
                today,
                today.plusDays(30),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.EXPENSE,
                PaymentStatus.PENDING,
                new BigDecimal("30.00"),
                today,
                today.plusDays(31),
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PENDING,
                new BigDecimal("25.00"),
                today,
                null,
                null);
        saveTransaction(
                farm,
                user,
                TransactionType.INCOME,
                PaymentStatus.PAID,
                new BigDecimal("90.00"),
                today,
                today.minusDays(2),
                today);

        FinancialReportResponse report =
                financialReportService.getReport(
                        new FinancialReportFilter(
                                farm.getId(),
                                today.plusYears(1),
                                today.plusYears(1),
                                FinancialReportBasis.CASH,
                                null,
                                null));

        assertThat(report.commitments().accountsReceivable()).isEqualByComparingTo("185.00");
        assertThat(report.commitments().accountsPayable()).isEqualByComparingTo("70.00");
        assertThat(report.commitments().overdueReceivableAmount()).isEqualByComparingTo("100.00");
        assertThat(report.commitments().overdueReceivableCount()).isEqualTo(1);
        assertThat(report.commitments().overduePayableAmount()).isZero();
        assertThat(report.commitments().next30DaysReceivable()).isEqualByComparingTo("60.00");
        assertThat(report.commitments().next30DaysPayable()).isEqualByComparingTo("40.00");
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
}
