package br.com.gestaodireta.financial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.financial.dto.FinancialCategoryRequest;
import br.com.gestaodireta.financial.dto.FinancialCategoryResponse;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
class FinancialCategoryServiceTest extends PostgresIntegrationTest {

    private static final String DUPLICATE_CATEGORY_MESSAGE =
            "A category with this name already exists.";

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
    void shouldCreateFarmCategoryAndInactivateCategory() {
        Farm farm = saveFarm("Farm");

        FinancialCategoryResponse response =
                financialCategoryService.create(
                        new FinancialCategoryRequest(
                                "Insumos",
                                TransactionType.EXPENSE,
                                "#ff0000",
                                "seedling",
                                farm.getId(),
                                false));

        assertThat(response.farmId()).isEqualTo(farm.getId());
        assertThat(response.status()).isEqualTo(FinancialCategoryStatus.ACTIVE);

        financialCategoryService.inactivate(response.id());

        FinancialCategory savedCategory =
                financialCategoryRepository.findById(response.id()).orElseThrow();
        assertThat(savedCategory.getStatus()).isEqualTo(FinancialCategoryStatus.INACTIVE);
    }

    @Test
    void shouldCreateDefaultCategoryWithoutFarm() {
        FinancialCategoryResponse response =
                financialCategoryService.create(
                        new FinancialCategoryRequest(
                                "Venda de producao",
                                TransactionType.INCOME,
                                null,
                                null,
                                null,
                                true));

        assertThat(response.farmId()).isNull();
        assertThat(response.isDefault()).isTrue();
    }

    @Test
    void shouldActivateInactiveDefaultCategory() {
        FinancialCategory category =
                saveCategory("Venda de producao", null, true, FinancialCategoryStatus.INACTIVE);

        FinancialCategoryResponse response = financialCategoryService.activate(category.getId());

        assertThat(response.status()).isEqualTo(FinancialCategoryStatus.ACTIVE);
        assertThat(response.farmId()).isNull();
        assertThat(response.isDefault()).isTrue();
        assertThat(financialCategoryRepository.findById(category.getId()).orElseThrow().getStatus())
                .isEqualTo(FinancialCategoryStatus.ACTIVE);
    }

    @Test
    void shouldActivateInactiveFarmCategory() {
        Farm farm = saveFarm("Farm");
        FinancialCategory category =
                saveCategory("Insumos", farm, false, FinancialCategoryStatus.INACTIVE);

        FinancialCategoryResponse response = financialCategoryService.activate(category.getId());

        assertThat(response.status()).isEqualTo(FinancialCategoryStatus.ACTIVE);
        assertThat(response.farmId()).isEqualTo(farm.getId());
        assertThat(response.isDefault()).isFalse();
        assertThat(financialCategoryRepository.findById(category.getId()).orElseThrow().getStatus())
                .isEqualTo(FinancialCategoryStatus.ACTIVE);
    }

    @Test
    void shouldReturnSuccessWhenActivatingAlreadyActiveCategory() {
        FinancialCategory category =
                saveCategory("Venda de producao", null, true, FinancialCategoryStatus.ACTIVE);

        FinancialCategoryResponse response = financialCategoryService.activate(category.getId());

        assertThat(response.status()).isEqualTo(FinancialCategoryStatus.ACTIVE);
        assertThat(response.id()).isEqualTo(category.getId());
    }

    @Test
    void shouldThrowResourceNotFoundWhenActivatingMissingCategory() {
        assertThatThrownBy(() -> financialCategoryService.activate(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Financial category not found");
    }

    @Test
    void shouldNotChangeCategoryDataWhenActivatingCategory() {
        Farm farm = saveFarm("Farm");
        FinancialCategory category =
                saveCategory("Insumos", farm, false, FinancialCategoryStatus.INACTIVE);
        category.setColor("#ff0000");
        category.setIcon("seedling");
        FinancialCategory savedCategory = financialCategoryRepository.save(category);

        FinancialCategoryResponse response =
                financialCategoryService.activate(savedCategory.getId());

        assertThat(response.name()).isEqualTo("Insumos");
        assertThat(response.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(response.farmId()).isEqualTo(farm.getId());
        assertThat(response.isDefault()).isFalse();
        assertThat(response.color()).isEqualTo("#ff0000");
        assertThat(response.icon()).isEqualTo("seedling");
    }

    @Test
    void shouldListOnlyActiveVisibleCategoriesByDefault() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        saveCategory("Global Active", null, true, FinancialCategoryStatus.ACTIVE);
        saveCategory("Farm Active", farm, false, FinancialCategoryStatus.ACTIVE);
        saveCategory("Global Inactive", null, true, FinancialCategoryStatus.INACTIVE);
        saveCategory("Farm Inactive", farm, false, FinancialCategoryStatus.INACTIVE);
        saveCategory("Other Farm Active", otherFarm, false, FinancialCategoryStatus.ACTIVE);

        var response =
                financialCategoryService.findAll(farm.getId(), false, new PaginationParams());

        assertThat(response.content())
                .extracting(FinancialCategoryResponse::name)
                .containsExactlyInAnyOrder("Global Active", "Farm Active");
    }

    @Test
    void shouldListActiveAndInactiveVisibleCategoriesWhenRequested() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        saveCategory("Global Active", null, true, FinancialCategoryStatus.ACTIVE);
        saveCategory("Farm Active", farm, false, FinancialCategoryStatus.ACTIVE);
        saveCategory("Global Inactive", null, true, FinancialCategoryStatus.INACTIVE);
        saveCategory("Farm Inactive", farm, false, FinancialCategoryStatus.INACTIVE);
        saveCategory("Other Farm Inactive", otherFarm, false, FinancialCategoryStatus.INACTIVE);

        var response = financialCategoryService.findAll(farm.getId(), true, new PaginationParams());

        assertThat(response.content())
                .extracting(FinancialCategoryResponse::name)
                .containsExactlyInAnyOrder(
                        "Global Active", "Farm Active", "Global Inactive", "Farm Inactive");
    }

    @Test
    void shouldCreateUniqueGlobalCategoryAndSaveTrimmedName() {
        FinancialCategoryResponse response =
                financialCategoryService.create(defaultCategoryRequest("  Venda de Safra  "));

        assertThat(response.name()).isEqualTo("Venda de Safra");
        assertThat(response.farmId()).isNull();
        assertThat(response.isDefault()).isTrue();
    }

    @Test
    void shouldRejectDuplicateGlobalCategoryByExactName() {
        financialCategoryService.create(defaultCategoryRequest("Insumos"));

        assertThatThrownBy(() -> financialCategoryService.create(defaultCategoryRequest("Insumos")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DUPLICATE_CATEGORY_MESSAGE);
    }

    @Test
    void shouldRejectDuplicateGlobalCategoryByCaseAndSpaces() {
        financialCategoryService.create(defaultCategoryRequest("Insumos"));

        assertThatThrownBy(
                        () -> financialCategoryService.create(defaultCategoryRequest(" insumos ")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DUPLICATE_CATEGORY_MESSAGE);
    }

    @Test
    void shouldRejectDuplicateGlobalCategoryWhenInactiveExists() {
        saveCategory("Insumos", null, true, FinancialCategoryStatus.INACTIVE);

        assertThatThrownBy(() -> financialCategoryService.create(defaultCategoryRequest("INSUMOS")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DUPLICATE_CATEGORY_MESSAGE);
    }

    @Test
    void shouldCreateUniqueFarmCategoryAndSaveTrimmedName() {
        Farm farm = saveFarm("Farm");

        FinancialCategoryResponse response =
                financialCategoryService.create(farmCategoryRequest("  Insumos  ", farm));

        assertThat(response.name()).isEqualTo("Insumos");
        assertThat(response.farmId()).isEqualTo(farm.getId());
        assertThat(response.isDefault()).isFalse();
    }

    @Test
    void shouldRejectDuplicateFarmCategoryInSameFarmByExactName() {
        Farm farm = saveFarm("Farm");
        financialCategoryService.create(farmCategoryRequest("Insumos", farm));

        assertThatThrownBy(
                        () -> financialCategoryService.create(farmCategoryRequest("Insumos", farm)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DUPLICATE_CATEGORY_MESSAGE);
    }

    @Test
    void shouldRejectDuplicateFarmCategoryInSameFarmByCaseAndSpaces() {
        Farm farm = saveFarm("Farm");
        financialCategoryService.create(farmCategoryRequest("Insumos", farm));

        assertThatThrownBy(
                        () ->
                                financialCategoryService.create(
                                        farmCategoryRequest(" insumos ", farm)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DUPLICATE_CATEGORY_MESSAGE);
    }

    @Test
    void shouldRejectDuplicateFarmCategoryWhenInactiveExists() {
        Farm farm = saveFarm("Farm");
        saveCategory("Insumos", farm, false, FinancialCategoryStatus.INACTIVE);

        assertThatThrownBy(
                        () -> financialCategoryService.create(farmCategoryRequest("INSUMOS", farm)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DUPLICATE_CATEGORY_MESSAGE);
    }

    @Test
    void shouldAllowSameCategoryNameInDifferentFarms() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        financialCategoryService.create(farmCategoryRequest("Insumos", farm));

        FinancialCategoryResponse response =
                financialCategoryService.create(farmCategoryRequest(" insumos ", otherFarm));

        assertThat(response.name()).isEqualTo("insumos");
        assertThat(response.farmId()).isEqualTo(otherFarm.getId());
    }

    @Test
    void shouldAllowSameCategoryNameBetweenGlobalAndFarmScopes() {
        Farm farm = saveFarm("Farm");
        financialCategoryService.create(defaultCategoryRequest("Insumos"));

        FinancialCategoryResponse response =
                financialCategoryService.create(farmCategoryRequest(" insumos ", farm));

        assertThat(response.name()).isEqualTo("insumos");
        assertThat(response.farmId()).isEqualTo(farm.getId());
    }

    @Test
    void shouldAllowUpdatingGlobalCategoryKeepingOwnNormalizedNameAndSaveTrimmedName() {
        FinancialCategory category =
                saveCategory("Venda", null, true, FinancialCategoryStatus.ACTIVE);

        FinancialCategoryResponse response =
                financialCategoryService.update(
                        category.getId(), defaultCategoryRequest("  venda  "));

        assertThat(response.id()).isEqualTo(category.getId());
        assertThat(response.name()).isEqualTo("venda");
    }

    @Test
    void shouldAllowUpdatingFarmCategoryKeepingOwnNormalizedNameAndSaveTrimmedName() {
        Farm farm = saveFarm("Farm");
        FinancialCategory category =
                saveCategory("Insumos", farm, false, FinancialCategoryStatus.ACTIVE);

        FinancialCategoryResponse response =
                financialCategoryService.update(
                        category.getId(), farmCategoryRequest("  insumos  ", farm));

        assertThat(response.id()).isEqualTo(category.getId());
        assertThat(response.name()).isEqualTo("insumos");
    }

    @Test
    void shouldRejectUpdatingGlobalCategoryToNameUsedByAnotherGlobalCategory() {
        FinancialCategory category =
                saveCategory("Insumos", null, true, FinancialCategoryStatus.ACTIVE);
        saveCategory("Venda", null, true, FinancialCategoryStatus.ACTIVE);

        assertThatThrownBy(
                        () ->
                                financialCategoryService.update(
                                        category.getId(), defaultCategoryRequest(" venda ")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DUPLICATE_CATEGORY_MESSAGE);
    }

    @Test
    void shouldRejectUpdatingFarmCategoryToNameUsedByAnotherCategoryInSameFarm() {
        Farm farm = saveFarm("Farm");
        FinancialCategory category =
                saveCategory("Insumos", farm, false, FinancialCategoryStatus.ACTIVE);
        saveCategory("Frete", farm, false, FinancialCategoryStatus.INACTIVE);

        assertThatThrownBy(
                        () ->
                                financialCategoryService.update(
                                        category.getId(), farmCategoryRequest(" frete ", farm)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DUPLICATE_CATEGORY_MESSAGE);
    }

    @Test
    void shouldAllowUpdatingFarmCategoryToNameUsedByOtherFarmOrGlobalScope() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        FinancialCategory category =
                saveCategory("Insumos", farm, false, FinancialCategoryStatus.ACTIVE);
        saveCategory("Frete", otherFarm, false, FinancialCategoryStatus.ACTIVE);
        saveCategory("Frete", null, true, FinancialCategoryStatus.ACTIVE);

        FinancialCategoryResponse response =
                financialCategoryService.update(
                        category.getId(), farmCategoryRequest(" frete ", farm));

        assertThat(response.name()).isEqualTo("frete");
        assertThat(response.farmId()).isEqualTo(farm.getId());
    }

    @Test
    void shouldRejectCategoryNameBlankAfterTrim() {
        assertThatThrownBy(() -> financialCategoryService.create(defaultCategoryRequest("   ")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Category name cannot be blank.");
    }

    private FinancialCategoryRequest defaultCategoryRequest(String name) {
        return new FinancialCategoryRequest(
                name, TransactionType.EXPENSE, "#ff0000", "package", null, true);
    }

    private FinancialCategoryRequest farmCategoryRequest(String name, Farm farm) {
        return new FinancialCategoryRequest(
                name, TransactionType.EXPENSE, "#ff0000", "package", farm.getId(), false);
    }

    private Farm saveFarm(String name) {
        Farm farm = new Farm();
        farm.setName(name);
        farm.setStatus(FarmStatus.ACTIVE);

        return farmRepository.save(farm);
    }

    private FinancialCategory saveCategory(
            String name, Farm farm, boolean defaultCategory, FinancialCategoryStatus status) {
        FinancialCategory category = new FinancialCategory();
        category.setName(name);
        category.setType(TransactionType.EXPENSE);
        category.setFarm(farm);
        category.setDefaultCategory(defaultCategory);
        category.setStatus(status);

        return financialCategoryRepository.save(category);
    }
}
