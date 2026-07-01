package br.com.gestaodireta.financial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.entity.FarmUser;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.dto.UserOptionResponse;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FinancialUserOptionServiceTest extends PostgresIntegrationTest {

    @Autowired private FinancialUserOptionService financialUserOptionService;

    @Autowired private FinancialTransactionRepository financialTransactionRepository;

    @Autowired private FinancialCategoryRepository financialCategoryRepository;

    @Autowired private FarmUserRepository farmUserRepository;

    @Autowired private FarmRepository farmRepository;

    @Autowired private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        financialTransactionRepository.deleteAll();
        financialCategoryRepository.deleteAll();
        farmUserRepository.deleteAll();
        farmRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldListEligibleUserOptionsForTransactionFilterOrderedByName() {
        Farm farm = saveFarm("Farm");
        Farm otherFarm = saveFarm("Other Farm");
        User activeLinked = saveUser("B Active Linked", "active-linked@example.com");
        User duplicate = saveUser("A Duplicate", "duplicate@example.com");
        User inactiveLinked = saveUser("Inactive Linked", "inactive-linked@example.com");
        User blockedLinked = saveUser("Blocked Linked", "blocked-linked@example.com");
        User inactiveCreator = saveUser("C Inactive Creator", "inactive-creator@example.com");
        User blockedCreator = saveUser("D Blocked Creator", "blocked-creator@example.com");
        User inactiveRoleCreator =
                saveUser("E Inactive Role Creator", "inactive-role-creator@example.com");
        User deletedOnly = saveUser("Deleted Only", "deleted-only@example.com");
        User otherFarmCreator = saveUser("Other Farm Creator", "other-farm@example.com");
        User inactiveRoleOnly = saveUser("Inactive Role Only", "inactive-role@example.com");

        inactiveLinked.setStatus(UserStatus.INACTIVE);
        blockedLinked.setStatus(UserStatus.BLOCKED);
        inactiveCreator.setStatus(UserStatus.INACTIVE);
        blockedCreator.setStatus(UserStatus.BLOCKED);
        userRepository.save(inactiveLinked);
        userRepository.save(blockedLinked);
        userRepository.save(inactiveCreator);
        userRepository.save(blockedCreator);

        saveFarmUser(farm, activeLinked, FarmUserRole.PRODUCER);
        saveFarmUser(farm, duplicate, FarmUserRole.EMPLOYEE);
        saveFarmUser(farm, inactiveLinked, FarmUserRole.PRODUCER);
        saveFarmUser(farm, blockedLinked, FarmUserRole.ACCOUNTANT);
        saveFarmUser(farm, inactiveRoleCreator, FarmUserRole.INACTIVE);
        saveFarmUser(farm, inactiveRoleOnly, FarmUserRole.INACTIVE);

        saveTransaction(farm, duplicate, FinancialRecordStatus.ACTIVE);
        saveTransaction(farm, inactiveCreator, FinancialRecordStatus.ACTIVE);
        saveTransaction(farm, blockedCreator, FinancialRecordStatus.ACTIVE);
        saveTransaction(farm, inactiveRoleCreator, FinancialRecordStatus.ACTIVE);
        saveTransaction(farm, deletedOnly, FinancialRecordStatus.DELETED);
        saveTransaction(otherFarm, otherFarmCreator, FinancialRecordStatus.ACTIVE);

        var response = financialUserOptionService.findTransactionFilterOptions(farm.getId());

        assertThat(response)
                .extracting(UserOptionResponse::name)
                .containsExactly(
                        "A Duplicate",
                        "B Active Linked",
                        "C Inactive Creator",
                        "D Blocked Creator",
                        "E Inactive Role Creator");
        assertThat(response)
                .extracting(UserOptionResponse::id)
                .doesNotContain(
                        inactiveLinked.getId(),
                        blockedLinked.getId(),
                        deletedOnly.getId(),
                        otherFarmCreator.getId(),
                        inactiveRoleOnly.getId());
        assertThat(response).filteredOn(option -> option.id().equals(duplicate.getId())).hasSize(1);
    }

    @Test
    void shouldThrowResourceNotFoundWhenListingOptionsForMissingFarm() {
        assertThatThrownBy(() -> financialUserOptionService.findTransactionFilterOptions(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Farm not found");
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

    private Farm saveFarm(String name) {
        Farm farm = new Farm();
        farm.setName(name);
        farm.setStatus(FarmStatus.ACTIVE);

        return farmRepository.save(farm);
    }

    private FarmUser saveFarmUser(Farm farm, User user, FarmUserRole role) {
        FarmUser farmUser = new FarmUser();
        farmUser.setFarm(farm);
        farmUser.setUser(user);
        farmUser.setRole(role);

        return farmUserRepository.save(farmUser);
    }

    private FinancialTransaction saveTransaction(
            Farm farm, User user, FinancialRecordStatus recordStatus) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setDescription("Transaction " + user.getName());
        transaction.setAmount(BigDecimal.TEN);
        transaction.setType(TransactionType.EXPENSE);
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setTransactionDate(LocalDate.now());
        transaction.setFarm(farm);
        transaction.setCreatedByUser(user);
        transaction.setRecordStatus(recordStatus);

        return financialTransactionRepository.save(transaction);
    }
}
