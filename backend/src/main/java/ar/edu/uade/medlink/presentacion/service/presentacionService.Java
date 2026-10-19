package ar.edu.uade.medlink.presentacion.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ar.edu.uade.medlink.entity.PresentacionMedicamento;
import ar.edu.uade.medlink.entity.Medicamento;
import ar.edu.uade.medlink.repository.PresentacionRepository;
import ar.edu.uade.medlink.repository.MedicamentoRepository;
import ar.edu.uade.medlink.dto.PresentacionRequestDTO;
import ar.edu.uade.medlink.model.enums.CondicionDispensa;
import java.util.List;

@Service
@Transactional
public class presentacionService{
    private final PresentacionRepository presentacionReposotory;
    private final MedicamentoRepository medicamentoRepository;

    public PresentacionService( PresentacionRepository pRepo, MedicamentoRepository mRepo){
            this.pRepo = pRepo;
            this.mRepo = mRepo;
    }
    public PresentacionMedicamento registrar(PresentacionRequestDTO dto){
        Medicamento m = medicamentoRepository.findById(dto.idMedicamento())
        .orElseThrow(() -> new RunTimeException("Medicamento no Encontrado"));
        PresentacionMedicamento p = new PresentacionMedicamento(m,dto.concentracion(),dto.formaFarmaceutica(),dto.cantidadEnvase(),dto.unidadExpendio(),dto.condicionDispensa());
        return presentacionReposotory.save(p);
    }
    @Transactional(readonly = true)
    public List<PresentacionMedicamento> buscarPresentaciones(Long idMedicamento){
        return presentacionReposotory.findByMedicamentoId(idMedicamento);
    }
    public void desactivar(Long id){
        PresentacionMedicamento p = presentacionReposotory.findById(id).orElseThrow(() ->
                new RuntimeException(
                    "Presentacion no encontrada"));
        p.desactivar();
        presentacionReposotory.save(p);
    }
}