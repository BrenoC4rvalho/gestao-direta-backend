package br.com.gestaodireta.user.security;

import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.shared.security.SecurityUtils;
import br.com.gestaodireta.user.dto.UserCreateRequest;
import br.com.gestaodireta.user.enumeration.UserType;
import org.springframework.stereotype.Component;

@Component("userAccess")
public class UserAccess {

    private final FarmUserRepository farmUserRepository;

    public UserAccess(FarmUserRepository farmUserRepository) {
        this.farmUserRepository = farmUserRepository;
    }

    public boolean canCreateUser(UserCreateRequest request) {
        if (SecurityUtils.isAdmin()) {
            return true;
        }

        if (!UserType.USER.equals(request.userType())) {
            return false;
        }

        return isAuthenticatedUserActiveProducer();
    }

    public boolean canSearchUserByEmail() {
        if (SecurityUtils.isAdmin()) {
            return true;
        }

        return isAuthenticatedUserActiveProducer();
    }

    private boolean isAuthenticatedUserActiveProducer() {
        if (!SecurityUtils.isUser()) {
            return false;
        }

        return farmUserRepository.existsActiveProducerByUserId(
                SecurityUtils.getAuthenticatedUserId());
    }
}
