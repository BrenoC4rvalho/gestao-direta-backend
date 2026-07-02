package br.com.gestaodireta.harvest.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class PaginationParamsForOptions {

    static final Pageable PAGEABLE = PageRequest.of(0, 500, Sort.by("name").ascending());

    private PaginationParamsForOptions() {}
}
