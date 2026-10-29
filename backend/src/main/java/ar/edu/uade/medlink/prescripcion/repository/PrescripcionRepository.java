package ar.edu.uade.medlink.prescripcion.repository;

import ar.edu.uade.medlink.prescripcion.entity.Prescripcion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PrescripcionRepository extends JpaRepository<Prescripcion,Long>, JpaSpecificationExecutor<Prescripcion> {
    boolean existsByCodigoExterno(String codigoExterno);

    // Read immutable owner without locking Prescripcion; acquire user guard first (10.2).
    @Query(value="SELECT id_usuario FROM prescripcion WHERE id_prescripcion=:id", nativeQuery=true)
    Optional<Long> findOwnerId(@Param("id") long id);

    // Reservation acquires Usuario before Prescripcion. Annulment must do likewise
    // before inserting auditoria (whose usuario_relacionado_id FK takes a KEY SHARE).
    @Query(value="SELECT id_usuario FROM usuario WHERE id_usuario=:id FOR UPDATE", nativeQuery=true)
    Optional<Long> lockOwner(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Prescripcion p where p.id=:id")
    Optional<Prescripcion> findLocked(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Prescripcion p where p.codigoExterno=:codigo")
    Optional<Prescripcion> findLockedByCodigo(@Param("codigo") String codigo);

    // Usuario has no JPA entity yet; only its identifier is needed here.
    @Query(value="SELECT EXISTS (SELECT 1 FROM usuario WHERE id_usuario=:id)",nativeQuery=true)
    boolean existeUsuario(@Param("id") long id);

}
