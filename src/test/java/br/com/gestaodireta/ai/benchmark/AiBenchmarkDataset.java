package br.com.gestaodireta.ai.benchmark;

import br.com.gestaodireta.financial.enumeration.TransactionType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class AiBenchmarkDataset {
    List<AiBenchmarkCase> load() {
        List<AiBenchmarkCase> cases = new ArrayList<>();
        addCompleteEntries(cases);
        addCompleteExits(cases);
        addInvalid(cases);
        addIncomplete(cases);
        return List.copyOf(cases);
    }

    private void addCompleteEntries(List<AiBenchmarkCase> cases) {
        String[] texts = {
            "Recebi R$ %s pela venda de café.",
            "Entrou %s reais da venda de milho.",
            "Pix recebido de João no valor de R$ %s pela venda de ovos.",
            "Venda de leite, recebi R$ %s.",
            "Foi creditado R$ %s da venda de soja.",
            "Caiu %s na conta com a venda de bezerros.",
            "Dinheiro da venda de hortaliças: R$ %s.",
            "Recebimento por transferência de R$ %s pelo café.",
            "Hoje entrou R$ %s de venda de mel.",
            "Recebi %s pela produção de queijo."
        };
        addComplete(
                cases,
                "ENTRY",
                AiBenchmarkGroup.ENTRY,
                TransactionType.INCOME,
                "Venda",
                texts,
                new String[] {"1250", "980", "350,50", "2430", "7800"});
    }

    private void addCompleteExits(List<AiBenchmarkCase> cases) {
        String[] texts = {
            "Paguei R$ %s de adubo.",
            "Comprei sementes por R$ %s.",
            "Saiu R$ %s para abastecer o trator.",
            "Pix de %s para pagar energia.",
            "Gastei %s reais com fertilizante.",
            "Paguei R$ %s de diesel para a colheitadeira.",
            "Transferência de R$ %s para manutenção da bomba.",
            "Comprei ração por %s reais.",
            "Ontem paguei R$ %s de água da fazenda.",
            "Dinheiro gasto: R$ %s com conserto do trator."
        };
        addComplete(
                cases,
                "EXIT",
                AiBenchmarkGroup.EXIT,
                TransactionType.EXPENSE,
                null,
                texts,
                new String[] {"850", "1240,50", "320", "175,90", "2300"});
    }

    private void addComplete(
            List<AiBenchmarkCase> cases,
            String prefix,
            AiBenchmarkGroup group,
            TransactionType type,
            String description,
            String[] templates,
            String[] amounts) {
        int id = 1;
        for (String template : templates) {
            for (String amount : amounts) {
                cases.add(
                        new AiBenchmarkCase(
                                prefix + "-" + String.format("%02d", id++),
                                template.formatted(amount),
                                group,
                                AiBenchmarkExpectedOutcome.CREATE_PENDING,
                                type,
                                brl(amount),
                                description,
                                null,
                                null,
                                true,
                                true,
                                List.of()));
            }
        }
    }

    private void addInvalid(List<AiBenchmarkCase> cases) {
        String[] texts = {
            "O céu está bonito hoje.",
            "banana janela foguete",
            "Qual a capital do Japão?",
            "Acho que vai chover amanhã.",
            "Meu cachorro dormiu no sofá.",
            "Ligue para mim depois.",
            "A lavoura está verde.",
            "Como plantar café?",
            "Bom dia pessoal.",
            "O trator está parado.",
            "Preciso olhar a cerca.",
            "A reunião é na terça.",
            "Qual foi a chuva de ontem?",
            "12345",
            "Obrigado pela ajuda.",
            "A porteira ficou aberta.",
            "Vamos colher amanhã.",
            "O milho cresceu bem.",
            "Me envie uma foto.",
            "Sem comentários."
        };
        for (int index = 0; index < texts.length; index++) {
            cases.add(
                    caseOf(
                            "INVALID-" + String.format("%02d", index + 1),
                            texts[index],
                            AiBenchmarkGroup.INVALID,
                            AiBenchmarkExpectedOutcome.REJECT_INVALID,
                            false));
        }
    }

    private void addIncomplete(List<AiBenchmarkCase> cases) {
        String[] texts = {
            "Paguei o adubo.",
            "Recebi pela venda de café.",
            "Gastei R$ 800.",
            "Recebi R$ 1.200.",
            "Comprei sementes.",
            "Paguei ontem.",
            "Venda de café.",
            "paguei isso",
            "recebi aquilo",
            "Gastei",
            "R$ 500",
            "Adubo",
            "entrada",
            "saída",
            "paguei 5 mil",
            "recebi 1,5 mil",
            "gastei 2k",
            "entrou mil reais",
            "paguei R$",
            "recebi R$",
            "Paguei 100 e",
            "Recebi 2 mil do café e paguei 500 de combustível.",
            "Paguei 300 de diesel e recebi 900 de leite.",
            "Paguei hoje R$ 200",
            "Vendi ontem",
            "Pix para",
            "Transferência recebida",
            "R$ 100 de",
            "Paguei o",
            "Recebi pela"
        };
        for (int index = 0; index < texts.length; index++) {
            cases.add(
                    caseOf(
                            "INCOMPLETE-" + String.format("%02d", index + 1),
                            texts[index],
                            AiBenchmarkGroup.INCOMPLETE,
                            AiBenchmarkExpectedOutcome.BLOCK_INCOMPLETE,
                            false));
        }
    }

    private AiBenchmarkCase caseOf(
            String id,
            String text,
            AiBenchmarkGroup group,
            AiBenchmarkExpectedOutcome outcome,
            boolean shouldCreate) {
        return new AiBenchmarkCase(
                id,
                text,
                group,
                outcome,
                null,
                null,
                null,
                null,
                null,
                shouldCreate,
                false,
                List.of("amount", "description"));
    }

    private BigDecimal brl(String value) {
        return new BigDecimal(value.replace(".", "").replace(',', '.'));
    }
}
