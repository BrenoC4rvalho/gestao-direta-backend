package br.com.gestaodireta.harvest.repository;

import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;

public interface HarvestSeasonAccessProjection {

    Long getFarmId();

    HarvestSeasonStatus getStatus();
}
