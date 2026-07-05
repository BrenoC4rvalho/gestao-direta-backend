package br.com.gestaodireta.financial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.financial.dto.FinancialCategoryCreateRequest;
import br.com.gestaodireta.financial.dto.FinancialCategoryResponse;
import br.com.gestaodireta.financial.dto.FinancialCategoryUpdateRequest;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
class FinancialCategoryServiceTest extends PostgresIntegrationTest {

    private static final String DUPLICATE_CATEGORY_MESSAGE =
            "A category with this name and type already exists for this farm.";

    @Autowired private FinancialCategoryService financialCategoryService;

    @Autowired private FinancialCategoryRepository financialCategoryRepository;

    @Autowired private FinancialTransactionRepository financialTransactionRepository;

    @Autowired private FarmUserRepository farmUserRepository;

    @Autowired private FarmRepository farmRepository;

    @Autowired private UserRepository userRepository;

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
    void shouldCreateFarmCategoryAndSaveTrimmedName() {
        Farm farm = saveFarm("Farm");

        FinancialCategoryResponse response =
                financialCategoryService.create(
                        createRequest(farm, "  Insumos  ", TransactionType.EXPENSE));

        assertThat(response.name()).isEqualTo("Insumos");
        assertThat(response.farmId()).isEqualTo(farm.getId());
        assertThat(response.farmName()).isEqualTo(farm.getName());
        assertThat(response.status()).isEqualTo(FinancialCategoryStatus.ACTIVE);
    }

    @Test
    void shouldRequireFarmWhenCreatingCategory() {
        FinancialCategoryCreateRequest request =
                new FinancialCategoryCreateRequest(
                        999L, "Insumos", TransactionType.EXPENSE, "#ff0000", "package");

        assertThatThrownBy(() -> financialCategoryService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Farm not found");
    }

    @Test
    void shouldRejectCreatingCategoryForInactiveFarm() {
        Farm farm = saveFarm("Farm");
        farm.setStatus(FarmStatus.INACTIVE);
        farmRepository.save(farm);

        assertThatThrownBy(
                        () ->
                                financialCategoryService.create(
                                        createRequest(farm, "Insumos", TransactionType.EXPENSE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Inactive farm cannot receive financial categories");
    }

    @Test
    void shouldActivateAndInactivateFarmCategory() {
        Farm farm = saveFarm("Farm");
        FinancialCategory category =
                saveCategory(
                        "Insumos", farm, TransactionType.EXPENSE, FinancialCategoryStatus.INACTIVE);

        FinancialCategoryResponse activated = financialCategoryService.activate(category.getId());
        assertThat(activated.status()).isEqualTo(FinancialCategoryStatus.ACTIVE);

        financialCategoryService.inactivate(category.getId());
        FinancialCategory savedCategory =
                financialCategoryRepository.findById(category.getId()).orElseThrow();
        assertThat(savedCategory.getStatus()).isEqualTo(FinancialCategoryStatus.INACTIVE);
    }

    @Test
    void shouldRejectActivatingCategoryForInactiveFarm() {
        Farm farm = saveFarm("Farm");
        FinancialCategory category =
                saveCategory(
                        "Insumos", farm, TransactionType.EXPENSE, FinancialCategoryStatus.INACTIVE);
        farm.setStatus(FarmStatus.INACTIVE);
        farmRepository.save(farm);

        assertThatThrownBy(() -> financialCategoryService.activate(category.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Inactive farm cannot receive financial categories");
    }

    @Test
    void shouldThrowResourceNotFoundWhenActivatingMissingCategory() {
        assertThatThrownBy(() -> financialCategoryService.activate(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Financial category not found");
    }

    @Test
    void shouldListOnlyActiveFarmCategoriesByDefault() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        saveCategory("Farm Active", farm, TransactionType.EXPENSE, FinancialCategoryStatus.ACTIVE);
        saveCategory(
                "Farm Inactive", farm, TransactionType.EXPENSE, FinancialCategoryStatus.INACTIVE);
        saveCategory(
                "Other Farm Active",
                otherFarm,
                TransactionType.EXPENSE,
                FinancialCategoryStatus.ACTIVE);

        var response =
                financialCategoryService.findAll(farm.getId(), false, new PaginationParams());

        assertThat(response.content())
                .extracting(FinancialCategoryResponse::name)
                .containsExactlyInAnyOrder("Farm Active");
    }

    @Test
    void shouldListActiveAndInactiveFarmCategoriesWhenRequested() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        saveCategory("Farm Active", farm, TransactionType.EXPENSE, FinancialCategoryStatus.ACTIVE);
        saveCategory(
                "Farm Inactive", farm, TransactionType.EXPENSE, FinancialCategoryStatus.INACTIVE);
        saveCategory(
                "Other Farm Inactive",
                otherFarm,
                TransactionType.EXPENSE,
                FinancialCategoryStatus.INACTIVE);

        var response = financialCategoryService.findAll(farm.getId(), true, new PaginationParams());

        assertThat(response.content())
                .extracting(FinancialCategoryResponse::name)
                .containsExactlyInAnyOrder("Farm Active", "Farm Inactive");
    }

    @Test
    void shouldListOnlyCategoriesUsedInActiveTransactionsByFarmOrderedByName() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        User user = saveUser("User", "user@example.com");
        FinancialCategory farmUsed =
                saveCategory(
                        "A Farm Used",
                        farm,
                        TransactionType.EXPENSE,
                        FinancialCategoryStatus.ACTIVE);
        FinancialCategory inactiveUsed =
                saveCategory(
                        "B Inactive Used",
                        farm,
                        TransactionType.EXPENSE,
                        FinancialCategoryStatus.INACTIVE);
        FinancialCategory unusedActive =
                saveCategory(
                        "C Unused Active",
                        farm,
                        TransactionType.EXPENSE,
                        FinancialCategoryStatus.ACTIVE);
        FinancialCategory deletedOnly =
                saveCategory(
                        "D Deleted Only",
                        farm,
                        TransactionType.EXPENSE,
                        FinancialCategoryStatus.ACTIVE);
        FinancialCategory otherFarmUsed =
                saveCategory(
                        "E Other Farm Used",
                        otherFarm,
                        TransactionType.EXPENSE,
                        FinancialCategoryStatus.ACTIVE);

        saveTransaction(farm, farmUsed, user, FinancialRecordStatus.ACTIVE);
        saveTransaction(farm, farmUsed, user, FinancialRecordStatus.ACTIVE);
        saveTransaction(farm, inactiveUsed, user, FinancialRecordStatus.ACTIVE);
        saveTransaction(farm, deletedOnly, user, FinancialRecordStatus.DELETED);
        saveTransaction(otherFarm, otherFarmUsed, user, FinancialRecordStatus.ACTIVE);

        var response = financialCategoryService.findUsedInTransactions(farm.getId());

        assertThat(response)
                .extracting(FinancialCategoryResponse::name)
                .containsExactly("A Farm Used", "B Inactive Used");
        assertThat(response)
                .extracting(FinancialCategoryResponse::id)
                .doesNotContain(unusedActive.getId(), deletedOnly.getId(), otherFarmUsed.getId());
    }

    @Test
    void shouldThrowResourceNotFoundWhenListingUsedCategoriesForMissingFarm() {
        assertThatThrownBy(() -> financialCategoryService.findUsedInTransactions(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Farm not found");
    }

    @Test
    void shouldRejectDuplicateFarmCategoryByNameAndTypeInSameFarm() {
        Farm farm = saveFarm("Farm");
        financialCategoryService.create(createRequest(farm, "Insumos", TransactionType.EXPENSE));

        assertThatThrownBy(
                        () ->
                                financialCategoryService.create(
                                        createRequest(farm, " insumos ", TransactionType.EXPENSE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DUPLICATE_CATEGORY_MESSAGE);
    }

    @Test
    void shouldRejectDuplicateFarmCategoryWhenInactiveExists() {
        Farm farm = saveFarm("Farm");
        saveCategory("Insumos", farm, TransactionType.EXPENSE, FinancialCategoryStatus.INACTIVE);

        assertThatThrownBy(
                        () ->
                                financialCategoryService.create(
                                        createRequest(farm, "INSUMOS", TransactionType.EXPENSE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DUPLICATE_CATEGORY_MESSAGE);
    }

    @Test
    void shouldAllowSameCategoryNameInSameFarmWhenTypeIsDifferent() {
        Farm farm = saveFarm("Farm");
        financialCategoryService.create(createRequest(farm, "Frete", TransactionType.EXPENSE));

        FinancialCategoryResponse response =
                financialCategoryService.create(
                        createRequest(farm, " frete ", TransactionType.INCOME));

        assertThat(response.name()).isEqualTo("frete");
        assertThat(response.type()).isEqualTo(TransactionType.INCOME);
    }

    @Test
    void shouldAllowSameCategoryNameAndTypeInDifferentFarms() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        financialCategoryService.create(createRequest(farm, "Insumos", TransactionType.EXPENSE));

        FinancialCategoryResponse response =
                financialCategoryService.create(
                        createRequest(otherFarm, " insumos ", TransactionType.EXPENSE));

        assertThat(response.name()).isEqualTo("insumos");
        assertThat(response.farmId()).isEqualTo(otherFarm.getId());
    }

    @Test
    void shouldUpdateCategoryKeepingFarmAndSaveTrimmedName() {
        Farm farm = saveFarm("Farm");
        FinancialCategory category =
                saveCategory(
                        "Insumos", farm, TransactionType.EXPENSE, FinancialCategoryStatus.ACTIVE);

        FinancialCategoryResponse response =
                financialCategoryService.update(
                        category.getId(), updateRequest("  venda  ", TransactionType.INCOME));

        assertThat(response.id()).isEqualTo(category.getId());
        assertThat(response.name()).isEqualTo("venda");
        assertThat(response.type()).isEqualTo(TransactionType.INCOME);
        assertThat(response.farmId()).isEqualTo(farm.getId());
    }

    @Test
    void shouldRejectUpdatingFarmCategoryToDuplicateNameAndType() {
        Farm farm = saveFarm("Farm");
        FinancialCategory category =
                saveCategory(
                        "Insumos", farm, TransactionType.EXPENSE, FinancialCategoryStatus.ACTIVE);
        saveCategory("Frete", farm, TransactionType.EXPENSE, FinancialCategoryStatus.INACTIVE);

        assertThatThrownBy(
                        () ->
                                financialCategoryService.update(
                                        category.getId(),
                                        updateRequest(" frete ", TransactionType.EXPENSE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DUPLICATE_CATEGORY_MESSAGE);
    }

    @Test
    void shouldRejectCategoryNameBlankAfterTrim() {
        Farm farm = saveFarm("Farm");

        assertThatThrownBy(
                        () ->
                                financialCategoryService.create(
                                        createRequest(farm, "   ", TransactionType.EXPENSE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Category name cannot be blank.");
    }

    @Test
    void shouldValidateFilterCategoryIdsBelongToFarm() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        FinancialCategory category =
                saveCategory(
                        "Insumos", farm, TransactionType.EXPENSE, FinancialCategoryStatus.ACTIVE);
        FinancialCategory otherCategory =
                saveCategory(
                        "Frete",
                        otherFarm,
                        TransactionType.EXPENSE,
                        FinancialCategoryStatus.ACTIVE);

        financialCategoryService.ensureCategoriesBelongToFarm(
                List.of(category.getId()), farm.getId());

        assertThatThrownBy(
                        () ->
                                financialCategoryService.ensureCategoriesBelongToFarm(
                                        List.of(category.getId(), otherCategory.getId()),
                                        farm.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Financial category does not belong to farm");
    }

    private FinancialCategoryCreateRequest createRequest(
            Farm farm, String name, TransactionType type) {
        return new FinancialCategoryCreateRequest(farm.getId(), name, type, "#ff0000", "package");
    }

    private FinancialCategoryUpdateRequest updateRequest(String name, TransactionType type) {
        return new FinancialCategoryUpdateRequest(name, type, "#ff0000", "package");
    }

    private User saveUser(String name, String email) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("Strong1!");
        user.setUserType(UserType.USER);
        user.setStatus(UserStatus.ACTIVE);

        return userRepository.save(user);
    }

    private FinancialTransaction saveTransaction(
            Farm farm, FinancialCategory category, User user, FinancialRecordStatus recordStatus) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Transaction " + category.getName());
        transaction.setAmount(BigDecimal.TEN);
        transaction.setType(category.getType());
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setTransactionDate(LocalDate.now());
        transaction.setFarm(farm);
        transaction.setCategory(category);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(recordStatus);

        return financialTransactionRepository.save(transaction);
    }

    private Farm saveFarm(String name) {
        Farm farm = new Farm();
        farm.setName(name);
        farm.setStatus(FarmStatus.ACTIVE);

        return farmRepository.save(farm);
    }

    private FinancialCategory saveCategory(
            String name, Farm farm, TransactionType type, FinancialCategoryStatus status) {
        FinancialCategory category = new FinancialCategory();
        category.setName(name);
        category.setType(type);
        category.setFarm(farm);
        category.setStatus(status);

        return financialCategoryRepository.save(category);
    }
}
