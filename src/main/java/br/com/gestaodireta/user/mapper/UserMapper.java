package br.com.gestaodireta.user.mapper;

import br.com.gestaodireta.user.dto.UserResponse;
import br.com.gestaodireta.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getDocument(),
                user.getUserType(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
