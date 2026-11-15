package br.com.gestaodireta.shared.demo;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.repository.UserRepository;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class DemoFinancialDataSeederTest {

    @Test
    void shouldReconcileTheDemonstrationTransactionsWithoutDuplicatingThem() throws Exception {
        FarmRepository farmRepository = mock(FarmRepository.class);
        FarmUserRepository farmUserRepository = mock(FarmUserRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        FinancialCategoryRepository categoryRepository = mock(FinancialCategoryRepository.class);
        FinancialTransactionRepository transactionRepository = mock(FinancialTransactionRepository.class);
        List<FinancialTransaction> persistedTransactions = new ArrayList<>();
        Farm farm = mock(Farm.class);
        User user = mock(User.class);
        FinancialCategory category = mock(FinancialCategory.class);

        when(userRepository.findByEmailIgnoreCase("demo@example.com")).thenReturn(Optional.of(user));
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getId()).thenReturn(9L);
        when(farmRepository.findByNameIgnoreCase("Fazenda Vale Verde")).thenReturn(Optional.of(farm));
        when(farm.getId()).thenReturn(7L);
        when(farm.getStatus()).thenReturn(FarmStatus.ACTIVE);
        when(farmUserRepository.existsByFarmIdAndUserId(7L, 9L)).thenReturn(true);
        when(categoryRepository.findByFarmIdAndTypeAndNameIgnoreCase(any(), any(), any()))
                .thenReturn(Optional.of(category));
        when(transactionRepository.findByFarmIdAndNotesStartingWith(
                7L, "[DEMO-SEED:VALE-VERDE-2026-10]"))
                .thenAnswer(invocation -> List.copyOf(persistedTransactions));
        when(transactionRepository.save(any(FinancialTransaction.class)))
                .thenAnswer(invocation -> {
                    FinancialTransaction transaction = invocation.getArgument(0);
                    if (!persistedTransactions.contains(transaction)) {
                        persistedTransactions.add(transaction);
                    }
                    return transaction;
                });

        DemoFinancialDataSeeder seeder = new DemoFinancialDataSeeder(
                farmRepository, farmUserRepository, userRepository, categoryRepository,
                transactionRepository, "demo@example.com");

        seeder.run(new DefaultApplicationArguments(new String[0]));
        verify(transactionRepository, times(13)).save(any());
        ArgumentCaptor<br.com.gestaodireta.financial.entity.FinancialTransaction> captor =
                ArgumentCaptor.forClass(br.com.gestaodireta.financial.entity.FinancialTransaction.class);
        verify(transactionRepository, times(13)).save(captor.capture());
        Map<String, BigDecimal> totals = captor.getAllValues().stream()
                .collect(Collectors.groupingBy(
                        transaction -> transaction.getType() + ":" + transaction.getStatus(),
                        Collectors.mapping(
                                br.com.gestaodireta.financial.entity.FinancialTransaction::getAmount,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        assertThat(totals).containsEntry("INCOME:PAID", new BigDecimal("180000.00"));
        assertThat(totals).containsEntry("INCOME:PENDING", new BigDecimal("165000.00"));
        assertThat(totals).containsEntry("EXPENSE:PAID", new BigDecimal("95000.00"));
        assertThat(totals).containsEntry("EXPENSE:PENDING", new BigDecimal("90000.00"));
        assertThat(captor.getAllValues()).allMatch(transaction ->
                transaction.getStatus() != PaymentStatus.PAID
                        || transaction.getPaidAt() != null);
        assertThat(captor.getAllValues().stream()
                .filter(transaction -> transaction.getStatus() == PaymentStatus.PAID)
                .map(FinancialTransaction::getPaidAt)
                .allMatch(date -> date.getMonthValue() == 10 && date.getYear() == 2026)).isTrue();
        assertThat(captor.getAllValues().stream()
                .filter(transaction -> transaction.getStatus() == PaymentStatus.PAID)
                .filter(transaction -> transaction.getPaidAt().isAfter(transaction.getDueDate()))
                .count()).isEqualTo(2);
        assertThat(captor.getAllValues().stream()
                .filter(transaction -> transaction.getStatus() == PaymentStatus.PENDING)
                .filter(transaction -> transaction.getDueDate().isBefore(LocalDate.of(2026, 10, 31)))
                .count()).isEqualTo(4);

        clearInvocations(transactionRepository);
        seeder.run(new DefaultApplicationArguments(new String[0]));
        verify(transactionRepository, times(13)).save(any());
        assertThat(persistedTransactions).hasSize(13);
    }
}
