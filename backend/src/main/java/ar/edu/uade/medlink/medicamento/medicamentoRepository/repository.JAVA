package ar.edu.uade.medlink.medicamento.repository;

import ar.edu.uade.medlink.medicamento.entity.Medicamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MedicamentoRepository
    extends JpaRepository<Medicamento, Long> {

    List<Medicamento> findByNombreContaining(
        String nombre);

    List<Medicamento> findByActivoTrue();
}