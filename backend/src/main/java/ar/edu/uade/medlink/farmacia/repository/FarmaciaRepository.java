package ar.edu.uade.medlink.farmacia.repository;

import ar.edu.uade.medlink.farmacia.entity.Farmacia;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FarmaciaRepository extends JpaRepository<Farmacia,Long>, JpaSpecificationExecutor<Farmacia> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Farmacia f where f.id=:id")
    Optional<Farmacia> findLocked(@Param("id") long id);
}
