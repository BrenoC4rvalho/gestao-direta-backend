alter table pending_financial_transactions
    alter column type drop not null,
    alter column amount drop not null,
    alter column transaction_date drop not null,
    alter column description drop not null;

