package br.com.gestaodireta.user.dto;

import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import java.util.List;

public record UserFilterRequest(
        String search, UserType userType, UserStatus status, List<UserStatus> statuses) {

    public UserFilterRequest(String search, UserType userType, UserStatus status) {
        this(search, userType, status, null);
    }
}
