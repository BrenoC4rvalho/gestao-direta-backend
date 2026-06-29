package br.com.gestaodireta.financial.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.gestaodireta.financial.service.FinancialTransactionService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class FinancialTransactionSchedulerTest {

    @Test
    void shouldDelegateOverdueMarkingToFinancialTransactionService() {
        FinancialTransactionService financialTransactionService =
                mock(FinancialTransactionService.class);
        Clock clock =
                Clock.fixed(Instant.parse("2026-06-29T03:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        FinancialTransactionScheduler scheduler =
                new FinancialTransactionScheduler(financialTransactionService, clock);
        when(financialTransactionService.markOverdueTransactions()).thenReturn(3);

        scheduler.markOverdueTransactions();

        verify(financialTransactionService).markOverdueTransactions();
    }
}
