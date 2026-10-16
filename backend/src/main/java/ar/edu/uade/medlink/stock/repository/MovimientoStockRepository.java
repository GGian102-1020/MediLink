package ar.edu.uade.medlink.stock.repository;

import ar.edu.uade.medlink.stock.entity.MovimientoStock;
import org.springframework.data.jpa.repository.*;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock,Long>, JpaSpecificationExecutor<MovimientoStock> { }
