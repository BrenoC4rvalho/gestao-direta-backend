package br.com.gestaodireta.shared.pagination;

import br.com.gestaodireta.shared.constant.AppConstants;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PaginationParams {

    private int page = 0;

    private int size = AppConstants.DEFAULT_PAGE_SIZE;

    private String sort = "id";

    private Sort.Direction direction = Sort.Direction.ASC;

    public Pageable toPageable() {
        return PageRequest.of(normalizedPage(), normalizedSize(), Sort.by(direction, sort));
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public Sort.Direction getDirection() {
        return direction;
    }

    public void setDirection(Sort.Direction direction) {
        this.direction = direction;
    }

    private int normalizedPage() {
        return Math.max(page, 0);
    }

    private int normalizedSize() {
        if (size <= 0) {
            return AppConstants.DEFAULT_PAGE_SIZE;
        }

        return Math.min(size, AppConstants.MAX_PAGE_SIZE);
    }
}
