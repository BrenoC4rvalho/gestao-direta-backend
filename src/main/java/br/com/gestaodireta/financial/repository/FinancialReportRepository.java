package br.com.gestaodireta.financial.repository;

import br.com.gestaodireta.financial.dto.FinancialCashFlowOpeningResponse;
import br.com.gestaodireta.financial.dto.FinancialCategorySummaryGroupResponse;
import br.com.gestaodireta.financial.dto.FinancialCategorySummaryResponse;
import br.com.gestaodireta.financial.dto.FinancialEvolutionPointResponse;
import br.com.gestaodireta.financial.dto.FinancialHarvestSummaryResponse;
import br.com.gestaodireta.financial.dto.FinancialReportCommitmentsResponse;
import br.com.gestaodireta.financial.dto.FinancialReportFilter;
import br.com.gestaodireta.financial.dto.FinancialReportSummaryResponse;
import br.com.gestaodireta.financial.dto.FinancialReportSummaryWithComparison;
import br.com.gestaodireta.financial.dto.FinancialReportTransactionResponse;
import br.com.gestaodireta.financial.dto.FinancialReportUnallocatedResponse;
import br.com.gestaodireta.financial.enumeration.FinancialReportGranularity;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.shared.response.PageResponse;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FinancialReportRepository {

    private static final String FILTERED_TRANSACTIONS =
            """
            with filtered_transactions as (
              select transaction.id, transaction.description, transaction.amount, transaction.type,
                     transaction.status, transaction.transaction_date, transaction.due_date,
                     transaction.paid_at, transaction.category_id, category.name as category_name,
                     transaction.harvest_season_id, season.name as harvest_season_name,
                     case
                       when :basis = 'ACCRUAL' then transaction.transaction_date
                       when transaction.status = 'PAID'
                         then coalesce(transaction.paid_at, transaction.transaction_date)
                       when transaction.status in ('PENDING', 'OVERDUE') then transaction.due_date
                     end as reference_date
              from financial_transactions transaction
              left join financial_categories category on category.id = transaction.category_id
              left join harvest_seasons season on season.id = transaction.harvest_season_id
              where transaction.farm_id = :farmId
                and transaction.record_status = 'ACTIVE'
                and transaction.status in ('PAID', 'PENDING', 'OVERDUE')
                and transaction.type in ('INCOME', 'EXPENSE')
                and (:filterHarvestSeasonIds = false
                  or transaction.harvest_season_id in (:harvestSeasonIds))
                and (:filterCategoryIds = false or transaction.category_id in (:categoryIds))
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FinancialReportRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public FinancialReportSummaryWithComparison summarize(
            FinancialReportFilter filter, LocalDate previousStartDate, LocalDate previousEndDate) {
        String sql =
                FILTERED_TRANSACTIONS
                        + """
                          , classified_transactions as (
                            select *,
                                   case
                                     when status = 'PAID'
                                       and coalesce(paid_at, transaction_date) <= :endDate
                                       then 'REALIZED'
                                     else 'PROJECTED'
                                   end as financial_state
                            from filtered_transactions
                          )
                          select
                            coalesce(sum(case when type = 'INCOME'
                              and reference_date between :startDate and :endDate then amount else 0 end), 0) as total_income,
                            coalesce(sum(case when type = 'EXPENSE'
                              and reference_date between :startDate and :endDate then amount else 0 end), 0) as total_expense,
                            coalesce(sum(case when type = 'INCOME' and financial_state = 'REALIZED'
                              and reference_date between :startDate and :endDate then amount else 0 end), 0) as realized_income,
                            coalesce(sum(case when type = 'EXPENSE' and financial_state = 'REALIZED'
                              and reference_date between :startDate and :endDate then amount else 0 end), 0) as realized_expense,
                            coalesce(sum(case when type = 'INCOME' and financial_state = 'PROJECTED'
                              and reference_date between :startDate and :endDate then amount else 0 end), 0) as projected_income,
                            coalesce(sum(case when type = 'EXPENSE' and financial_state = 'PROJECTED'
                              and reference_date between :startDate and :endDate then amount else 0 end), 0) as projected_expense,
                            coalesce(sum(case
                              when type = 'INCOME'
                                and status = 'PAID'
                                and coalesce(paid_at, transaction_date) <= :previousEndDate
                                and reference_date between :previousStartDate and :previousEndDate
                                then amount else 0 end), 0) as previous_realized_income,
                            coalesce(sum(case
                              when type = 'EXPENSE'
                                and status = 'PAID'
                                and coalesce(paid_at, transaction_date) <= :previousEndDate
                                and reference_date between :previousStartDate and :previousEndDate
                                then amount else 0 end), 0) as previous_realized_expense,
                            count(case
                              when status = 'PAID'
                                and coalesce(paid_at, transaction_date) <= :previousEndDate
                                and reference_date between :previousStartDate and :previousEndDate
                                then 1 end) as previous_realized_transaction_count
                          from classified_transactions
                          where reference_date between :previousStartDate and :endDate
                          """;
        MapSqlParameterSource parameters = parameters(filter);
        parameters.addValue("previousStartDate", previousStartDate);
        parameters.addValue("previousEndDate", previousEndDate);
        return jdbcTemplate.queryForObject(
                sql,
                parameters,
                (resultSet, rowNum) -> {
                    BigDecimal income = decimal(resultSet.getBigDecimal("total_income"));
                    BigDecimal expense = decimal(resultSet.getBigDecimal("total_expense"));
                    BigDecimal balance = income.subtract(expense);
                    return new FinancialReportSummaryWithComparison(
                            new FinancialReportSummaryResponse(
                                    income,
                                    expense,
                                    balance,
                                    percentage(balance, income),
                                    decimal(resultSet.getBigDecimal("realized_income")),
                                    decimal(resultSet.getBigDecimal("realized_expense")),
                                    decimal(resultSet.getBigDecimal("projected_income")),
                                    decimal(resultSet.getBigDecimal("projected_expense"))),
                            decimal(resultSet.getBigDecimal("previous_realized_income")),
                            decimal(resultSet.getBigDecimal("previous_realized_expense")),
                            resultSet.getLong("previous_realized_transaction_count"));
                });
    }

    public FinancialReportCommitmentsResponse summarizeCommitments(
            FinancialReportFilter filter,
            LocalDate cutoffDate,
            LocalDate today,
            LocalDate next30Days,
            boolean next30DaysAvailable) {
        String sql =
                FILTERED_TRANSACTIONS
                        + """
                          , cutoff_commitments as (
                            select *
                            from filtered_transactions
                            where coalesce(due_date, transaction_date) <= :cutoffDate
                              and (
                                status in ('PENDING', 'OVERDUE')
                                or (
                                  status = 'PAID'
                                  and coalesce(paid_at, transaction_date) > :cutoffDate
                                )
                              )
                          )
                          select
                            coalesce(sum(case when type = 'INCOME' then amount else 0 end), 0) as accounts_receivable,
                            coalesce(sum(case when type = 'EXPENSE' then amount else 0 end), 0) as accounts_payable,
                            coalesce(sum(case when type = 'INCOME' and due_date < :cutoffDate then amount else 0 end), 0) as overdue_receivable_amount,
                            count(case when type = 'INCOME' and due_date < :cutoffDate then 1 end) as overdue_receivable_count,
                            coalesce(sum(case when type = 'EXPENSE' and due_date < :cutoffDate then amount else 0 end), 0) as overdue_payable_amount,
                            count(case when type = 'EXPENSE' and due_date < :cutoffDate then 1 end) as overdue_payable_count,
                            (
                              select coalesce(sum(case when type = 'INCOME' then amount else 0 end), 0)
                              from filtered_transactions
                              where status in ('PENDING', 'OVERDUE')
                                and due_date between :today and :next30Days
                            ) as next30_days_receivable,
                            (
                              select coalesce(sum(case when type = 'EXPENSE' then amount else 0 end), 0)
                              from filtered_transactions
                              where status in ('PENDING', 'OVERDUE')
                                and due_date between :today and :next30Days
                            ) as next30_days_payable
                          from cutoff_commitments
                          """;
        MapSqlParameterSource parameters = parameters(filter);
        parameters.addValue("cutoffDate", cutoffDate);
        parameters.addValue("today", today);
        parameters.addValue("next30Days", next30Days);
        return jdbcTemplate.queryForObject(
                sql,
                parameters,
                (resultSet, rowNum) ->
                        new FinancialReportCommitmentsResponse(
                                decimal(resultSet.getBigDecimal("accounts_receivable")),
                                decimal(resultSet.getBigDecimal("accounts_payable")),
                                decimal(resultSet.getBigDecimal("overdue_receivable_amount")),
                                resultSet.getLong("overdue_receivable_count"),
                                decimal(resultSet.getBigDecimal("overdue_payable_amount")),
                                resultSet.getLong("overdue_payable_count"),
                                next30DaysAvailable,
                                next30DaysAvailable
                                        ? decimal(resultSet.getBigDecimal("next30_days_receivable"))
                                        : null,
                                next30DaysAvailable
                                        ? decimal(resultSet.getBigDecimal("next30_days_payable"))
                                        : null));
    }

    public FinancialReportCommitmentsResponse summarizeFilteredCommitments(
            FinancialReportFilter filter, LocalDate cutoffDate) {
        String sql =
                FILTERED_TRANSACTIONS
                        + """
                          , as_of_transactions as (
                            select *,
                                   (
                                     status in ('PENDING', 'OVERDUE')
                                     or (
                                       status = 'PAID'
                                       and coalesce(paid_at, transaction_date) > :cutoffDate
                                     )
                                   ) as open_at_cutoff
                            from filtered_transactions
                          )
                          select
                            coalesce(sum(case when type = 'INCOME' then amount else 0 end), 0) as accounts_receivable,
                            coalesce(sum(case when type = 'EXPENSE' then amount else 0 end), 0) as accounts_payable,
                            coalesce(sum(case when type = 'INCOME' and due_date < :cutoffDate then amount else 0 end), 0) as overdue_receivable_amount,
                            count(case when type = 'INCOME' and due_date < :cutoffDate then 1 end) as overdue_receivable_count,
                            coalesce(sum(case when type = 'EXPENSE' and due_date < :cutoffDate then amount else 0 end), 0) as overdue_payable_amount,
                            count(case when type = 'EXPENSE' and due_date < :cutoffDate then 1 end) as overdue_payable_count
                          from as_of_transactions
                          where reference_date between :startDate and :cutoffDate
                            and coalesce(due_date, transaction_date) <= :cutoffDate
                            and open_at_cutoff
                          """;
        MapSqlParameterSource parameters = parameters(filter);
        parameters.addValue("cutoffDate", cutoffDate);
        return jdbcTemplate.queryForObject(
                sql,
                parameters,
                (resultSet, rowNum) ->
                        new FinancialReportCommitmentsResponse(
                                decimal(resultSet.getBigDecimal("accounts_receivable")),
                                decimal(resultSet.getBigDecimal("accounts_payable")),
                                decimal(resultSet.getBigDecimal("overdue_receivable_amount")),
                                resultSet.getLong("overdue_receivable_count"),
                                decimal(resultSet.getBigDecimal("overdue_payable_amount")),
                                resultSet.getLong("overdue_payable_count"),
                                false,
                                null,
                                null));
    }

    public List<FinancialEvolutionPointResponse> findEvolution(
            FinancialReportFilter filter, LocalDate referenceDate) {
        String periodExpression =
                FinancialReportGranularity.QUARTERLY.equals(filter.granularity())
                        ? "date_trunc('quarter', bucket_date)::date"
                        : "date_trunc('month', bucket_date)::date";
        String sql =
                FILTERED_TRANSACTIONS
                        + """
                          , classified_transactions as (
                            select *,
                                   case
                                     when status = 'PAID'
                                       and coalesce(paid_at, transaction_date) <= :referenceDate
                                       then 'REALIZED'
                                     when due_date is not null and due_date < :referenceDate
                                       then 'OVERDUE'
                                     else 'PROJECTED'
                                   end as financial_state
                            from filtered_transactions
                          ), bucketed_transactions as (
                            select *,
                                   case financial_state
                                     when 'REALIZED' then
                                       case when :basis = 'ACCRUAL' then transaction_date
                                            else coalesce(paid_at, transaction_date) end
                                     when 'OVERDUE' then due_date
                                     else case when :basis = 'ACCRUAL' then transaction_date
                                               else due_date end
                                   end as bucket_date
                            from classified_transactions
                          )
                          select %s as period_start,
                                 coalesce(sum(case when type = 'INCOME' then amount else 0 end), 0) as income,
                                 coalesce(sum(case when type = 'EXPENSE' then amount else 0 end), 0) as expense,
                                 count(*) as transaction_count,
                                 coalesce(sum(case when type = 'INCOME' and financial_state = 'REALIZED' then amount else 0 end), 0) as realized_income,
                                 coalesce(sum(case when type = 'INCOME' and financial_state = 'PROJECTED' then amount else 0 end), 0) as projected_income,
                                 coalesce(sum(case when type = 'INCOME' and financial_state = 'OVERDUE' then amount else 0 end), 0) as overdue_income,
                                 count(case when type = 'INCOME' and financial_state = 'OVERDUE' then 1 end) as overdue_income_count,
                                 coalesce(sum(case when type = 'EXPENSE' and financial_state = 'REALIZED' then amount else 0 end), 0) as realized_expense,
                                 coalesce(sum(case when type = 'EXPENSE' and financial_state = 'PROJECTED' then amount else 0 end), 0) as projected_expense,
                                 coalesce(sum(case when type = 'EXPENSE' and financial_state = 'OVERDUE' then amount else 0 end), 0) as overdue_expense,
                                 count(case when type = 'EXPENSE' and financial_state = 'OVERDUE' then 1 end) as overdue_expense_count
                          from bucketed_transactions
                          where bucket_date between :startDate and :endDate
                          group by %s
                          order by period_start
                          """
                                .formatted(periodExpression, periodExpression);
        MapSqlParameterSource parameters = parameters(filter);
        parameters.addValue("referenceDate", referenceDate);
        return jdbcTemplate.query(
                sql,
                parameters,
                (resultSet, rowNum) -> {
                    LocalDate start = localDate(resultSet.getDate("period_start"));
                    BigDecimal income = decimal(resultSet.getBigDecimal("income"));
                    BigDecimal expense = decimal(resultSet.getBigDecimal("expense"));
                    BigDecimal realizedIncome = decimal(resultSet.getBigDecimal("realized_income"));
                    BigDecimal realizedExpense =
                            decimal(resultSet.getBigDecimal("realized_expense"));
                    return new FinancialEvolutionPointResponse(
                            start.toString(),
                            "",
                            start,
                            start,
                            income,
                            expense,
                            income.subtract(expense),
                            resultSet.getLong("transaction_count"),
                            realizedIncome,
                            decimal(resultSet.getBigDecimal("projected_income")),
                            decimal(resultSet.getBigDecimal("overdue_income")),
                            resultSet.getLong("overdue_income_count"),
                            realizedExpense,
                            decimal(resultSet.getBigDecimal("projected_expense")),
                            decimal(resultSet.getBigDecimal("overdue_expense")),
                            resultSet.getLong("overdue_expense_count"),
                            realizedIncome.subtract(realizedExpense),
                            false);
                });
    }

    public FinancialCashFlowOpeningResponse findCashFlowOpening(
            FinancialReportFilter filter, LocalDate referenceDate) {
        String sql =
                FILTERED_TRANSACTIONS
                        + """
                          , classified_transactions as (
                            select *,
                                   case
                                     when status = 'PAID'
                                       and coalesce(paid_at, transaction_date) <= :referenceDate
                                       then 'REALIZED'
                                     when due_date is not null and due_date < :referenceDate
                                       then 'OVERDUE'
                                     else 'PROJECTED'
                                   end as financial_state
                            from filtered_transactions
                          ), bucketed_transactions as (
                            select *,
                                   case financial_state
                                     when 'REALIZED' then
                                       case when :basis = 'ACCRUAL' then transaction_date
                                            else coalesce(paid_at, transaction_date) end
                                     when 'OVERDUE' then due_date
                                     else case when :basis = 'ACCRUAL' then transaction_date
                                               else due_date end
                                   end as bucket_date
                            from classified_transactions
                          )
                          select
                            coalesce(sum(case
                              when financial_state <> 'OVERDUE' and type = 'INCOME' then amount
                              when financial_state <> 'OVERDUE' and type = 'EXPENSE' then -amount
                              else 0 end), 0) as expected_balance,
                            coalesce(sum(case
                              when type = 'INCOME' then amount
                              when type = 'EXPENSE' then -amount
                              else 0 end), 0) as projected_balance
                          from bucketed_transactions
                          where bucket_date < :startDate
                          """;
        MapSqlParameterSource parameters = parameters(filter);
        parameters.addValue("referenceDate", referenceDate);
        return jdbcTemplate.queryForObject(
                sql,
                parameters,
                (resultSet, rowNum) ->
                        new FinancialCashFlowOpeningResponse(
                                decimal(resultSet.getBigDecimal("expected_balance")),
                                decimal(resultSet.getBigDecimal("projected_balance"))));
    }

    public List<FinancialEvolutionPointResponse> findPerformanceEvolution(
            FinancialReportFilter filter) {
        String sql =
                FILTERED_TRANSACTIONS
                        + """
                          select date_trunc('month', reference_date)::date as period_start,
                                 coalesce(sum(case when type = 'INCOME' then amount else 0 end), 0) as income,
                                 coalesce(sum(case when type = 'EXPENSE' then amount else 0 end), 0) as expense,
                                 count(*) as transaction_count
                          from filtered_transactions
                          where reference_date between :startDate and :endDate
                          group by date_trunc('month', reference_date)::date
                          order by period_start
                          """;
        return jdbcTemplate.query(
                sql,
                parameters(filter),
                (resultSet, rowNum) -> {
                    LocalDate start = localDate(resultSet.getDate("period_start"));
                    BigDecimal income = decimal(resultSet.getBigDecimal("income"));
                    BigDecimal expense = decimal(resultSet.getBigDecimal("expense"));
                    return new FinancialEvolutionPointResponse(
                            start.toString(),
                            "",
                            start,
                            start,
                            income,
                            expense,
                            income.subtract(expense),
                            resultSet.getLong("transaction_count"),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            0,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            0,
                            BigDecimal.ZERO,
                            false);
                });
    }

    public List<FinancialCategorySummaryGroupResponse> findCategories(
            FinancialReportFilter filter) {
        String sql =
                FILTERED_TRANSACTIONS
                        + """
                          , category_amounts as (
                            select category_id, coalesce(category_name, 'Sem categoria') as category_name, type,
                                   coalesce(sum(amount), 0) as amount, count(*) as transaction_count
                            from filtered_transactions
                            where reference_date between :startDate and :endDate
                            group by category_id, category_name, type
                          )
                          select category_id, category_name, type, amount, transaction_count,
                                 coalesce(sum(amount) over (partition by type), 0) as total_amount,
                                 coalesce(sum(transaction_count) over (partition by type), 0)
                                   as total_transaction_count,
                                 round(
                                   coalesce(
                                     amount * 100 / nullif(sum(amount) over (partition by type), 0),
                                     0
                                   ),
                                   2
                                 ) as percentage
                          from category_amounts
                          order by type asc, amount desc, category_name asc nulls last
                          """;
        Map<TransactionType, List<CategoryAmount>> itemsByType =
                new EnumMap<>(TransactionType.class);
        for (TransactionType type : TransactionType.values()) {
            itemsByType.put(type, new ArrayList<>());
        }

        jdbcTemplate.query(
                sql,
                parameters(filter),
                (resultSet, rowNum) -> {
                    TransactionType type = TransactionType.valueOf(resultSet.getString("type"));
                    itemsByType
                            .get(type)
                            .add(
                                    new CategoryAmount(
                                            nullableLong(resultSet, "category_id"),
                                            resultSet.getString("category_name"),
                                            type,
                                            decimal(resultSet.getBigDecimal("amount")),
                                            decimal(resultSet.getBigDecimal("percentage")),
                                            resultSet.getLong("transaction_count"),
                                            decimal(resultSet.getBigDecimal("total_amount")),
                                            resultSet.getLong("total_transaction_count")));
                    return null;
                });

        return List.of(TransactionType.EXPENSE, TransactionType.INCOME).stream()
                .map(type -> categoryGroup(type, itemsByType.get(type)))
                .toList();
    }

    public List<FinancialHarvestSummaryResponse> findHarvests(FinancialReportFilter filter) {
        String sql =
                FILTERED_TRANSACTIONS
                        + """
                          select harvest_season_id,
                                 coalesce(harvest_season_name, 'Sem safra') as harvest_season_name,
                                 coalesce(sum(case when type = 'INCOME' then amount else 0 end), 0) as income,
                                 coalesce(sum(case when type = 'EXPENSE' then amount else 0 end), 0) as expense,
                                 count(*) as transaction_count
                          from filtered_transactions
                          where reference_date between :startDate and :endDate
                          group by harvest_season_id, harvest_season_name
                          order by (coalesce(sum(case when type = 'INCOME' then amount else 0 end), 0)
                            - coalesce(sum(case when type = 'EXPENSE' then amount else 0 end), 0)) desc,
                            harvest_season_name asc
                          """;
        return jdbcTemplate.query(
                sql,
                parameters(filter),
                (resultSet, rowNum) -> {
                    BigDecimal income = decimal(resultSet.getBigDecimal("income"));
                    BigDecimal expense = decimal(resultSet.getBigDecimal("expense"));
                    BigDecimal profit = income.subtract(expense);
                    return new FinancialHarvestSummaryResponse(
                            nullableLong(resultSet, "harvest_season_id"),
                            resultSet.getString("harvest_season_name"),
                            income,
                            expense,
                            profit,
                            percentage(profit, income),
                            resultSet.getLong("transaction_count"),
                            null);
                });
    }

    public FinancialReportUnallocatedResponse findUnallocated(FinancialReportFilter filter) {
        String sql =
                FILTERED_TRANSACTIONS
                        + """
                          select coalesce(sum(case when type = 'INCOME' then amount else 0 end), 0) as income,
                                 coalesce(sum(case when type = 'EXPENSE' then amount else 0 end), 0) as expense,
                                 count(*) as transaction_count
                          from filtered_transactions
                          where reference_date is null
                          """;
        return jdbcTemplate.queryForObject(
                sql,
                parameters(filter),
                (resultSet, rowNum) ->
                        new FinancialReportUnallocatedResponse(
                                decimal(resultSet.getBigDecimal("income")),
                                decimal(resultSet.getBigDecimal("expense")),
                                resultSet.getLong("transaction_count")));
    }

    public PageResponse<FinancialReportTransactionResponse> findTransactions(
            FinancialReportFilter filter, int page, int size, String sort, String direction) {
        String orderBy = sortColumn(sort) + " " + direction;
        String dataSql =
                FILTERED_TRANSACTIONS
                        + """
                          select id, description, type, amount, status, transaction_date, due_date, paid_at,
                                 reference_date, category_id, category_name, harvest_season_id, harvest_season_name
                          from filtered_transactions
                          where reference_date between :startDate and :endDate
                          """
                        + " order by "
                        + orderBy
                        + ", id "
                        + direction
                        + " limit :limit offset :offset";
        MapSqlParameterSource parameters = parameters(filter);
        parameters.addValue("limit", size);
        parameters.addValue("offset", page * size);
        List<FinancialReportTransactionResponse> content =
                jdbcTemplate.query(
                        dataSql,
                        parameters,
                        (resultSet, rowNum) ->
                                new FinancialReportTransactionResponse(
                                        resultSet.getLong("id"),
                                        resultSet.getString("description"),
                                        TransactionType.valueOf(resultSet.getString("type")),
                                        decimal(resultSet.getBigDecimal("amount")),
                                        br.com.gestaodireta.financial.enumeration.PaymentStatus
                                                .valueOf(resultSet.getString("status")),
                                        localDate(resultSet.getDate("transaction_date")),
                                        localDate(resultSet.getDate("due_date")),
                                        localDate(resultSet.getDate("paid_at")),
                                        localDate(resultSet.getDate("reference_date")),
                                        nullableLong(resultSet, "category_id"),
                                        resultSet.getString("category_name"),
                                        nullableLong(resultSet, "harvest_season_id"),
                                        resultSet.getString("harvest_season_name")));
        Long total =
                jdbcTemplate.queryForObject(
                        FILTERED_TRANSACTIONS
                                + "select count(*) from filtered_transactions where reference_date between :startDate and :endDate",
                        parameters(filter),
                        Long.class);
        long totalElements = total == null ? 0 : total;
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(
                content, page, size, totalElements, totalPages, page == 0, page + 1 >= totalPages);
    }

    private FinancialCategorySummaryGroupResponse categoryGroup(
            TransactionType type, List<CategoryAmount> categoryAmounts) {
        CategoryAmount first = categoryAmounts.isEmpty() ? null : categoryAmounts.getFirst();
        List<FinancialCategorySummaryResponse> items =
                categoryAmounts.stream()
                        .map(
                                item ->
                                        new FinancialCategorySummaryResponse(
                                                item.id(),
                                                item.name(),
                                                item.type(),
                                                item.amount(),
                                                item.percentage(),
                                                item.transactionCount()))
                        .toList();
        return new FinancialCategorySummaryGroupResponse(
                type,
                first == null ? BigDecimal.ZERO : first.totalAmount(),
                first == null ? 0 : first.totalTransactionCount(),
                items);
    }

    private MapSqlParameterSource parameters(FinancialReportFilter filter) {
        return new MapSqlParameterSource()
                .addValue("farmId", filter.farmId())
                .addValue("startDate", filter.startDate())
                .addValue("endDate", filter.endDate())
                .addValue("basis", filter.basis().name())
                .addValue("filterHarvestSeasonIds", filter.harvestSeasonIds() != null)
                .addValue(
                        "harvestSeasonIds",
                        filter.harvestSeasonIds() == null
                                ? List.of(-1L)
                                : filter.harvestSeasonIds())
                .addValue("filterCategoryIds", filter.categoryIds() != null)
                .addValue(
                        "categoryIds",
                        filter.categoryIds() == null ? List.of(-1L) : filter.categoryIds());
    }

    private String sortColumn(String sort) {
        Map<String, String> columns = new HashMap<>();
        columns.put("id", "id");
        columns.put("description", "description");
        columns.put("amount", "amount");
        columns.put("transactionDate", "transaction_date");
        columns.put("dueDate", "due_date");
        columns.put("paidAt", "paid_at");
        columns.put("referenceDate", "reference_date");
        return columns.getOrDefault(sort, "reference_date");
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal percentage(BigDecimal value, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO;
        }

        return value.multiply(BigDecimal.valueOf(100))
                .divide(total, 2, java.math.RoundingMode.HALF_UP);
    }

    private LocalDate localDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private Long nullableLong(java.sql.ResultSet resultSet, String column)
            throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private String monthLabel(LocalDate date) {
        return switch (date.getMonthValue()) {
            case 1 -> "Jan";
            case 2 -> "Fev";
            case 3 -> "Mar";
            case 4 -> "Abr";
            case 5 -> "Mai";
            case 6 -> "Jun";
            case 7 -> "Jul";
            case 8 -> "Ago";
            case 9 -> "Set";
            case 10 -> "Out";
            case 11 -> "Nov";
            case 12 -> "Dez";
            default -> throw new IllegalArgumentException("Invalid month");
        };
    }

    private record CategoryAmount(
            Long id,
            String name,
            TransactionType type,
            BigDecimal amount,
            BigDecimal percentage,
            long transactionCount,
            BigDecimal totalAmount,
            long totalTransactionCount) {}
}
