package ar.edu.uade.medlink.stock.repository;

import ar.edu.uade.medlink.stock.entity.Stock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<Stock,Long>, JpaSpecificationExecutor<Stock> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.farmaciaId=:farmacia and s.presentacionId=:presentacion")
    Optional<Stock> findLocked(@Param("farmacia") long farmacia, @Param("presentacion") long presentacion);
}
