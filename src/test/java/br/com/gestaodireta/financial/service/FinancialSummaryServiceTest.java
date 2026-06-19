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
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class FinancialSummaryServiceTest extends PostgresIntegrationTest {

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
    void shouldIgnoreCanceledAndDeletedTransactionsInSummary() {
        Farm farm = saveFarm();
        User user = saveUser();
        saveTransaction(farm, user, TransactionType.INCOME, PaymentStatus.PAID, "100.00");
        saveTransaction(farm, user, TransactionType.EXPENSE, PaymentStatus.PENDING, "40.00");
        saveTransaction(farm, user, TransactionType.EXPENSE, PaymentStatus.CANCELED, "30.00");
        FinancialTransaction deleted =
                saveTransaction(
                        farm, user, TransactionType.EXPENSE, PaymentStatus.PENDING, "20.00");
        deleted.setRecordStatus(FinancialRecordStatus.DELETED);
        financialTransactionRepository.save(deleted);

        FinancialSummaryResponse response = financialSummaryService.summarize(farm.getId());

        assertThat(response.incomeTotal()).isEqualByComparingTo("100.00");
        assertThat(response.expenseTotal()).isEqualByComparingTo("40.00");
        assertThat(response.balance()).isEqualByComparingTo("60.00");
        assertThat(response.pendingTotal()).isEqualByComparingTo("40.00");
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

    private FinancialTransaction saveTransaction(
            Farm farm, User user, TransactionType type, PaymentStatus status, String amount) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Transaction");
        transaction.setAmount(new BigDecimal(amount));
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setTransactionDate(LocalDate.now());
        transaction.setDueDate(LocalDate.now().plusDays(5));
        transaction.setFarm(farm);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(FinancialRecordStatus.ACTIVE);

        return financialTransactionRepository.save(transaction);
    }
}
