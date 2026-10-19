package ar.edu.uade.medlink.common;

import java.util.List;

/** Shared REST pagination envelope; unchanged wire contract for all modules. */
public record PageDTO<T>(List<T> content, int page, int size, long totalElements,
                         int totalPages, boolean first, boolean last) {
    public static <T> PageDTO<T> from(List<T> content, int page, int size, long total) {
        int pages = (int) ((total + size - 1) / size);
        return new PageDTO<>(content, page, size, total, pages, page == 0, page >= pages - 1);
    }
}
