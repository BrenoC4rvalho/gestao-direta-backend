package br.com.gestaodireta.shared.demo;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.entity.FarmUser;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.enumeration.ProductionType;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
@ConditionalOnProperty(prefix = "app.demo-seed", name = "enabled", havingValue = "true")
public class DemoFinancialDataSeeder implements ApplicationRunner {

    private static final String FARM_NAME = "Fazenda Vale Verde";
    private static final String MARKER = "[DEMO-SEED:VALE-VERDE-2026-10]";

    private final FarmRepository farmRepository;
    private final FarmUserRepository farmUserRepository;
    private final UserRepository userRepository;
    private final FinancialCategoryRepository categoryRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final String userEmail;

    public DemoFinancialDataSeeder(
            FarmRepository farmRepository,
            FarmUserRepository farmUserRepository,
            UserRepository userRepository,
            FinancialCategoryRepository categoryRepository,
            FinancialTransactionRepository transactionRepository,
            @Value("${app.demo-seed.user-email}") String userEmail) {
        this.farmRepository = farmRepository;
        this.farmUserRepository = farmUserRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.userEmail = userEmail;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException(
                        "Usuário demo ativo não encontrado: " + userEmail));

        Farm farm = farmRepository.findByNameIgnoreCase(FARM_NAME).orElseGet(this::createFarm);
        if (farm.getStatus() != FarmStatus.ACTIVE) {
            throw new IllegalStateException("A Fazenda Vale Verde precisa estar ativa no ambiente demo");
        }
        ensureFarmAccess(farm, user);

        FinancialCategory coffee = category(farm, "Vendas agrícolas", TransactionType.INCOME);
        FinancialCategory soybean = category(farm, "Recebimento de produção", TransactionType.INCOME);
        FinancialCategory fertilizer = category(farm, "Fertilizantes", TransactionType.EXPENSE);
        FinancialCategory supplies = category(farm, "Defensivos agrícolas", TransactionType.EXPENSE);
        FinancialCategory fuel = category(farm, "Combustíveis", TransactionType.EXPENSE);
        FinancialCategory labor = category(farm, "Mão de obra", TransactionType.EXPENSE);
        FinancialCategory maintenance = category(farm, "Manutenção de máquinas", TransactionType.EXPENSE);

        List<SeedTransaction> seeds = List.of(
                income("Venda de café", "REC-REAL-01", "100000.00", PaymentStatus.PAID,
                        PaymentMethod.BANK_TRANSFER, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 10),
                        LocalDate.of(2026, 10, 14), coffee),
                income("Venda de soja", "REC-REAL-02", "80000.00", PaymentStatus.PAID,
                        PaymentMethod.PIX, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18),
                        LocalDate.of(2026, 10, 17), soybean),
                income("Contrato de café para novembro", "REC-PROJ-01", "70000.00", PaymentStatus.PENDING,
                        PaymentMethod.BOLETO, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, coffee),
                income("Recebimento de produção agrícola", "REC-PROJ-02", "50000.00", PaymentStatus.PENDING,
                        PaymentMethod.BANK_TRANSFER, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 31), null, soybean),
                income("Parcela vencida da safra", "REC-OVER-01", "30000.00", PaymentStatus.PENDING,
                        PaymentMethod.BOLETO, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10), null, soybean),
                income("Venda de produção pendente", "REC-OVER-02", "15000.00", PaymentStatus.PENDING,
                        PaymentMethod.BANK_TRANSFER, LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 20), null, coffee),
                expense("Compra de fertilizantes", "DES-REAL-01", "40000.00", PaymentStatus.PAID,
                        PaymentMethod.BANK_TRANSFER, LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 8),
                        LocalDate.of(2026, 10, 8), fertilizer),
                expense("Aplicação de defensivos", "DES-REAL-02", "30000.00", PaymentStatus.PAID,
                        PaymentMethod.PIX, LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 15),
                        LocalDate.of(2026, 10, 18), supplies),
                expense("Abastecimento da frota", "DES-REAL-03", "25000.00", PaymentStatus.PAID,
                        PaymentMethod.CREDIT_CARD, LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 22),
                        LocalDate.of(2026, 10, 21), fuel),
                expense("Fertilizantes para plantio", "DES-PROJ-01", "35000.00", PaymentStatus.PENDING,
                        PaymentMethod.BOLETO, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 31), null, fertilizer),
                expense("Serviços de mão de obra", "DES-PROJ-02", "30000.00", PaymentStatus.PENDING,
                        PaymentMethod.BANK_TRANSFER, LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 31), null, labor),
                expense("Manutenção de colheitadeira", "DES-OVER-01", "15000.00", PaymentStatus.PENDING,
                        PaymentMethod.BOLETO, LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 12), null, maintenance),
                expense("Combustível em aberto", "DES-OVER-02", "10000.00", PaymentStatus.PENDING,
                        PaymentMethod.BOLETO, LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 22), null, fuel));

        Map<String, FinancialTransaction> existingByCode = transactionRepository
                .findByFarmIdAndNotesStartingWith(farm.getId(), MARKER)
                .stream()
                .collect(Collectors.toMap(this::seedCode, Function.identity()));
        seeds.forEach(seed -> {
            FinancialTransaction existing = existingByCode.get(seed.code());
            if (existing == null) {
                transactionRepository.save(transaction(farm, user, seed));
            } else {
                applySeed(existing, farm, user, seed);
                transactionRepository.save(existing);
            }
        });
        transactionRepository.flush();
    }

    private Farm createFarm() {
        Farm farm = new Farm();
        farm.setName(FARM_NAME);
        farm.setProductionType(ProductionType.AGRICULTURE);
        farm.setStatus(FarmStatus.ACTIVE);
        return farmRepository.saveAndFlush(farm);
    }

    private void ensureFarmAccess(Farm farm, User user) {
        if (!farmUserRepository.existsByFarmIdAndUserId(farm.getId(), user.getId())) {
            FarmUser farmUser = new FarmUser();
            farmUser.setFarm(farm);
            farmUser.setUser(user);
            farmUser.setRole(FarmUserRole.PRODUCER);
            farmUserRepository.save(farmUser);
        }
    }

    private FinancialCategory category(Farm farm, String name, TransactionType type) {
        return categoryRepository.findByFarmIdAndTypeAndNameIgnoreCase(farm.getId(), type, name)
                .orElseGet(() -> {
                    FinancialCategory category = new FinancialCategory();
                    category.setFarm(farm);
                    category.setName(name);
                    category.setType(type);
                    category.setStatus(FinancialCategoryStatus.ACTIVE);
                    return categoryRepository.save(category);
                });
    }

    private FinancialTransaction transaction(Farm farm, User user, SeedTransaction seed) {
        FinancialTransaction transaction = new FinancialTransaction();
        applySeed(transaction, farm, user, seed);
        return transaction;
    }

    private void applySeed(
            FinancialTransaction transaction, Farm farm, User user, SeedTransaction seed) {
        transaction.setFarm(farm);
        transaction.setCreatedByUser(user);
        transaction.setDescription(seed.description());
        transaction.setAmount(seed.amount());
        transaction.setType(seed.type());
        transaction.setStatus(seed.status());
        transaction.setPaymentMethod(seed.paymentMethod());
        transaction.setTransactionDate(seed.transactionDate());
        transaction.setDueDate(seed.dueDate());
        transaction.setPaidAt(seed.paidAt());
        transaction.setCategory(seed.category());
        transaction.setRecordStatus(FinancialRecordStatus.ACTIVE);
        transaction.setNotes(MARKER + " " + seed.code());
    }

    private String seedCode(FinancialTransaction transaction) {
        String prefix = MARKER + " ";
        String notes = transaction.getNotes();
        if (notes == null || !notes.startsWith(prefix) || notes.length() == prefix.length()) {
            throw new IllegalStateException("Registro demo sem código válido: " + transaction.getId());
        }
        return notes.substring(prefix.length());
    }

    private SeedTransaction income(
            String description, String code, String amount, PaymentStatus status, PaymentMethod paymentMethod,
            LocalDate transactionDate, LocalDate dueDate, LocalDate paidAt, FinancialCategory category) {
        return new SeedTransaction(description, code, new BigDecimal(amount), TransactionType.INCOME, status,
                paymentMethod, transactionDate, dueDate, paidAt, category);
    }

    private SeedTransaction expense(
            String description, String code, String amount, PaymentStatus status, PaymentMethod paymentMethod,
            LocalDate transactionDate, LocalDate dueDate, LocalDate paidAt, FinancialCategory category) {
        return new SeedTransaction(description, code, new BigDecimal(amount), TransactionType.EXPENSE, status,
                paymentMethod, transactionDate, dueDate, paidAt, category);
    }

    private record SeedTransaction(
            String description,
            String code,
            BigDecimal amount,
            TransactionType type,
            PaymentStatus status,
            PaymentMethod paymentMethod,
            LocalDate transactionDate,
            LocalDate dueDate,
            LocalDate paidAt,
            FinancialCategory category) {}
}
