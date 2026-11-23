package ar.edu.uade.medlink.escaneo;

import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.recetaadjunta.RecetaAdjuntaService;
import ar.edu.uade.medlink.recetademo.RecetaDemoDtos.Vinculacion;
import ar.edu.uade.medlink.recetademo.RecetaDemoService;
import ar.edu.uade.medlink.prescripcion.service.PrescripcionService;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Vinculación, validación del medicamento y archivo original en una sola transacción. */
@Service
public class RecetaVerificadaService {
    private final RecetaDemoService recetas;
    private final RecetaAdjuntaService adjuntos;
    private final Clock clock;
    private final PrescripcionService prescripciones;
    public RecetaVerificadaService(RecetaDemoService recetas, RecetaAdjuntaService adjuntos, Clock clock,
        PrescripcionService prescripciones) {
        this.recetas=recetas; this.adjuntos=adjuntos; this.clock=clock; this.prescripciones=prescripciones;
    }
    public static void validarSeleccion(Long presentacionId,Integer cantidad) {
        if (presentacionId==null && cantidad==null) return;
        if (presentacionId==null || presentacionId<1 || cantidad==null || cantidad<1 || cantidad>10)
            throw ApiException.validation("Elegí una presentación y una cantidad entre 1 y 10.");
    }
    @Transactional
    public Vinculacion vincularDocumento(PrescriptionImageExtractor.Lectura lectura, MultipartFile archivo,
        Long presentacionId,Integer cantidad,CurrentAccount actor,HttpServletRequest request) {
        validarSeleccion(presentacionId,cantidad);
        var result=recetas.verificarYVincular(lectura,actor,request);
        validarParaReserva(result,presentacionId,cantidad);
        adjuntos.guardarVerificada(result.prescripcion().id(),archivo,actor);
        return result;
    }
    @Transactional
    public Vinculacion vincularCodigo(String codigo,Long presentacionId,Integer cantidad,
        CurrentAccount actor,HttpServletRequest request) {
        validarSeleccion(presentacionId,cantidad);
        var result=presentacionId!=null && !codigo.strip().toUpperCase(java.util.Locale.ROOT).startsWith("MLD-")
            ? new Vinculacion(false,prescripciones.porCodigoPropio(codigo,actor),null)
            : recetas.vincular(codigo,actor,request);
        validarParaReserva(result,presentacionId,cantidad);
        return result;
    }
    private void validarParaReserva(Vinculacion result,Long presentacionId,Integer cantidad) {
        if (presentacionId==null) return;
        var receta=result.prescripcion();
        var hoy=LocalDate.now(clock.withZone(ApiClock.ZONE));
        if (!"ACTIVA".equals(receta.estado().name()) || hoy.isBefore(receta.fechaEmision()) || hoy.isAfter(receta.fechaVencimiento()))
            throw ApiException.conflict("RECETA_DEMO_NO_VIGENTE","La receta está fuera de vigencia.");
        var item=receta.items().stream().filter(i->i.presentacionId()==presentacionId).findFirst()
            .orElseThrow(()->ApiException.conflict("RECETA_MEDICAMENTO_NO_COINCIDE",
                "La receta no corresponde a este medicamento y presentación. Adjuntá la receta correcta o elegí el medicamento indicado."));
        if (item.cantidadDisponible()<cantidad)
            throw ApiException.conflict("RECETA_CANTIDAD_INSUFICIENTE",
                "La receta tiene "+item.cantidadDisponible()+" unidades disponibles. Revisá la cantidad solicitada.");
    }
}
