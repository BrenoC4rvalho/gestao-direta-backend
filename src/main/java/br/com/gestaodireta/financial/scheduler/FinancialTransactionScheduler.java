package br.com.gestaodireta.financial.scheduler;

import br.com.gestaodireta.financial.service.FinancialTransactionService;
import br.com.gestaodireta.shared.config.SchedulingConfig;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FinancialTransactionScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(FinancialTransactionScheduler.class);

    private final FinancialTransactionService financialTransactionService;

    private final Clock clock;

    public FinancialTransactionScheduler(
            FinancialTransactionService financialTransactionService, Clock clock) {
        this.financialTransactionService = financialTransactionService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = SchedulingConfig.APPLICATION_ZONE_ID)
    public void markOverdueTransactions() {
        LocalDate referenceDate = LocalDate.now(clock);
        LOGGER.info("Starting overdue financial transactions job for date {}", referenceDate);

        int updatedTransactions = financialTransactionService.markOverdueTransactions();

        LOGGER.info(
                "Finished overdue financial transactions job. Updated transactions: {}",
                updatedTransactions);
    }
}
