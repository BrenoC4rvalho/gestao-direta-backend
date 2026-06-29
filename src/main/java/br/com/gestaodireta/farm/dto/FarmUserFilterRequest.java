package br.com.gestaodireta.farm.dto;

import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import java.util.List;

public record FarmUserFilterRequest(String search, FarmUserRole role, List<FarmUserRole> roles) {

    public FarmUserFilterRequest(String search, FarmUserRole role) {
        this(search, role, null);
    }
}
