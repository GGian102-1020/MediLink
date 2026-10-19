package ar.edu.uade.medlink.presentacion;

import ar.edu.uade.medlink.presentacion.repository.PresentacionRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PresentacionRepositoryMappingTest {
    @Test void pageFetchesOnlyToOneMedicationAssociation() throws Exception {
        var method=PresentacionRepository.class.getMethod("findAll",Specification.class,Pageable.class);
        var graph=method.getAnnotation(EntityGraph.class);
        assertNotNull(graph);
        assertArrayEquals(new String[]{"medicamento"},graph.attributePaths());
    }
}
