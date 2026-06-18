package br.com.gestaodireta.shared.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.shared.constant.AppConstants;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PaginationParamsTest {

    @Test
    void shouldUseDefaultValues() {
        PaginationParams params = new PaginationParams();

        Pageable pageable = params.toPageable();

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(AppConstants.DEFAULT_PAGE_SIZE);
        assertThat(pageable.getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void shouldLimitSizeToMaxPageSize() {
        PaginationParams params = new PaginationParams();
        params.setSize(500);

        Pageable pageable = params.toPageable();

        assertThat(pageable.getPageSize()).isEqualTo(AppConstants.MAX_PAGE_SIZE);
    }

    @Test
    void shouldUseDefaultSizeWhenSizeIsInvalid() {
        PaginationParams params = new PaginationParams();
        params.setSize(0);

        Pageable pageable = params.toPageable();

        assertThat(pageable.getPageSize()).isEqualTo(AppConstants.DEFAULT_PAGE_SIZE);
    }

    @Test
    void shouldUseConfiguredSortAndDirection() {
        PaginationParams params = new PaginationParams();
        params.setSort("name");
        params.setDirection(Sort.Direction.DESC);

        Pageable pageable = params.toPageable();

        assertThat(pageable.getSort().getOrderFor("name")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("name").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }
}
