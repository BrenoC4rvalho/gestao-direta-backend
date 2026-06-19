package br.com.gestaodireta.farm.mapper;

import br.com.gestaodireta.farm.dto.FarmUserResponse;
import br.com.gestaodireta.farm.entity.FarmUser;
import org.springframework.stereotype.Component;

@Component
public class FarmUserMapper {

    public FarmUserResponse toResponse(FarmUser farmUser) {
        return new FarmUserResponse(
                farmUser.getId(),
                farmUser.getFarm().getId(),
                farmUser.getFarm().getName(),
                farmUser.getUser().getId(),
                farmUser.getUser().getName(),
                farmUser.getUser().getEmail(),
                farmUser.getRole(),
                farmUser.getCreatedAt(),
                farmUser.getUpdatedAt());
    }
}
