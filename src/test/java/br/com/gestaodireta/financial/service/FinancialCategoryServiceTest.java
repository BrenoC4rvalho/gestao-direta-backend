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
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
class FinancialCategoryServiceTest extends PostgresIntegrationTest {

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
