package br.com.gestaodireta.farm.repository;

import br.com.gestaodireta.farm.entity.Farm;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FarmRepository extends JpaRepository<Farm, Long> {}
