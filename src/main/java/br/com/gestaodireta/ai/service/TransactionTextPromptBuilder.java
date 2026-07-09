package br.com.gestaodireta.ai.service;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class TransactionTextPromptBuilder {

    public String build(String text, LocalDate currentDate) {
        return """
                Voce transforma textos de produtores rurais em JSON de movimentacao financeira.

                Retorne apenas JSON valido.
                Nao use markdown.
                Nao explique.
                Nao coloque texto antes ou depois do JSON.

                Idioma do texto: pt-BR.
                Data atual: %s.

                Use exatamente estes enums:
                type: INCOME ou EXPENSE
                paymentStatus: PENDING, PAID, OVERDUE
                paymentMethod: PIX, CASH, CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, BOLETO, CHECK, OTHER

                Regras de interpretacao:
                paguei, comprei, gastei, despesa, custo -> EXPENSE
                recebi, vendi, entrou, receita, venda -> INCOME
                ja paguei, paguei, recebi -> PAID
                vou pagar, vence, boleto, falta pagar -> PENDING
                atrasado, vencido -> OVERDUE
                pix -> PIX
                dinheiro -> CASH
                cartao de credito -> CREDIT_CARD
                cartao de debito -> DEBIT_CARD
                transferencia -> BANK_TRANSFER
                boleto -> BOLETO
                cheque -> CHECK
                nao informado -> OTHER
                hoje -> data atual
                ontem -> data atual menos 1 dia
                amanha -> data atual mais 1 dia, se fizer sentido
                sem data clara -> data atual e adicionar warning
                se houver vencimento, preencher dueDate; se nao houver, null

                Campos obrigatorios:
                type
                amount
                description
                transactionDate
                dueDate
                paymentStatus
                paymentMethod
                categoryName
                harvestSeasonName
                confidence
                missingFields
                warnings

                Formato exato:
                {
                  "type": "EXPENSE",
                  "amount": 250.00,
                  "description": "Adubo",
                  "transactionDate": "%s",
                  "dueDate": null,
                  "paymentStatus": "PAID",
                  "paymentMethod": "PIX",
                  "categoryName": "Insumos",
                  "harvestSeasonName": "Milho",
                  "confidence": 0.87,
                  "missingFields": [],
                  "warnings": []
                }

                Texto do usuario:
                %s
                """
                .formatted(currentDate, currentDate, text);
    }
}
