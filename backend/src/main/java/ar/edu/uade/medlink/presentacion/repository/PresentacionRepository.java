package ar.edu.uade.medlink.presentacion.repository;

import ar.edu.uade.medlink.presentacion.entity.PresentacionMedicamento;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PresentacionRepository extends JpaRepository<PresentacionMedicamento,Long>, JpaSpecificationExecutor<PresentacionMedicamento> {
    // ManyToOne fetch is safe for pageable results; no fetch-join of collections.
    @Override
    @EntityGraph(attributePaths = "medicamento")
    Page<PresentacionMedicamento> findAll(Specification<PresentacionMedicamento> spec, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PresentacionMedicamento p where p.id=:id")
    Optional<PresentacionMedicamento> findLocked(@Param("id") long id);
}
