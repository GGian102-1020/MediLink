package ar.edu.uade.medlink.common;

import ar.edu.uade.medlink.admin.AdminDtos.PageDTO;
import ar.edu.uade.medlink.common.error.ApiException;
import java.util.*;
import org.springframework.data.domain.*;

public final class PageRequests {
    private PageRequests() { }
    public static Pageable of(int page, int size, List<String> specifications,
                              Set<String> allowed, Sort defaults) {
        if (page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE)
            throw ApiException.validation("Paginación inválida. page>=0 y 1<=size<=100.");
        if (specifications==null || specifications.isEmpty()) return PageRequest.of(page,size,defaults);
        List<Sort.Order> orders=new ArrayList<>();
        for (String specification:specifications) {
            String[] parts=specification.split(",",-1);
            if (parts.length!=2 || !allowed.contains(parts[0]) ||
                !(parts[1].equalsIgnoreCase("asc")||parts[1].equalsIgnoreCase("desc")))
                throw ApiException.validation("Ordenamiento inválido.");
            orders.add(new Sort.Order(Sort.Direction.fromString(parts[1]),parts[0]));
        }
        if (orders.stream().noneMatch(o->o.getProperty().equals("id"))) orders.add(Sort.Order.asc("id"));
        return PageRequest.of(page,size,Sort.by(orders));
    }
    public static <T> PageDTO<T> dto(Page<T> page) {
        return PageDTO.from(page.getContent(),page.getNumber(),page.getSize(),page.getTotalElements());
    }
}
