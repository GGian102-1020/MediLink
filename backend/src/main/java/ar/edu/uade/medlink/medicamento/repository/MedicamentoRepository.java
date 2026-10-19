package ar.edu.uade.medlink.medicamento.repository;

import ar.edu.uade.medlink.medicamento.entity.Medicamento;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MedicamentoRepository extends JpaRepository<Medicamento,Long>, JpaSpecificationExecutor<Medicamento> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Medicamento m where m.id=:id")
    Optional<Medicamento> findLocked(@Param("id") long id);
}
