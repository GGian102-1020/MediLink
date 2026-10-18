package ar.edu.uade.medlink.repository;

import ar.edu.uade.medlink.model.PresentacionMedicamento;
import ar.edu.uade.medlink.model.enums.CondicionDispensa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PresentacionRepository
    extends JpaRepository<PresentacionMedicamento, Long> {

    List<PresentacionMedicamento>
    findByMedicamentoIdMedicamento(
        Long idMedicamento);

    List<PresentacionMedicamento>
    findByCondicionDispensa(
        CondicionDispensa condicion);

    List<PresentacionMedicamento>
    findByActivoTrue();
}