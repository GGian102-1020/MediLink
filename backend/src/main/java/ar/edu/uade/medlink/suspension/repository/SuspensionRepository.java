package ar.edu.uade.medlink.suspension.repository;

import ar.edu.uade.medlink.suspension.entity.Suspension;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** Read-only access to historical intervals already created by the automatic evaluator. */
public interface SuspensionRepository extends JpaRepository<Suspension, Long>, JpaSpecificationExecutor<Suspension> {
    @Query(value="SELECT EXISTS (SELECT 1 FROM usuario WHERE id_usuario=:usuarioId)", nativeQuery=true)
    boolean existeUsuario(@Param("usuarioId") long usuarioId);
}
