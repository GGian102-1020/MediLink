package ar.edu.uade.medlink.prescripcion.repository;

import ar.edu.uade.medlink.prescripcion.entity.ItemPrescripcion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ItemPrescripcionRepository extends JpaRepository<ItemPrescripcion,Long> {
    // Lock the parent Prescripción first (10.2), then the item.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ItemPrescripcion i where i.prescripcion.id=:prescripcion and i.presentacion.id=:presentacion")
    Optional<ItemPrescripcion> findLocked(@Param("prescripcion") long prescripcionId,
                                          @Param("presentacion") long presentacionId);
}
