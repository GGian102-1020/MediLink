package ar.edu.uade.medlink.medicamento.service;

import ar.edu.uade.medlink.medicamento.dto.MedicamentoRequestDTO;
import ar.edu.uade.medlink.medicamento.entity.Medicamento;
import ar.edu.uade.medlink.medicamento.repository.MedicamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional
public class MedicamentoService {

    private final MedicamentoRepository
        medicamentoRepository;

    public MedicamentoService(
        MedicamentoRepository repo) {
        this.medicamentoRepository = repo;
    }

    public Medicamento registrar(
        MedicamentoRequestDTO dto) {

        Medicamento m = new Medicamento(
            dto.nombre(),
            dto.principioActivo()
        );

        return medicamentoRepository.save(m);
    }

    @Transactional(readOnly = true)
    public List<Medicamento> buscar(
        String nombre) {
        return medicamentoRepository
            .findByNombreContaining(nombre);
    }

    public void desactivar(Long id) {
        Medicamento m = medicamentoRepository
            .findById(id)
            .orElseThrow(() ->
                new RuntimeException(
                    "Medicamento no encontrado"));

        m.desactivar();

        medicamentoRepository.save(m);
    }
}