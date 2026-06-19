package br.com.gestaodireta.financial.service;

import static org.assertj.core.api.Assertions.assertThat;

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

    private Farm saveFarm(String name) {
        Farm farm = new Farm();
        farm.setName(name);
        farm.setStatus(FarmStatus.ACTIVE);

        return farmRepository.save(farm);
    }
}
