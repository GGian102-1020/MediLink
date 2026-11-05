package ar.edu.uade.medlink.recetademo;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.escaneo.PrescriptionImageExtractor.Lectura;
import ar.edu.uade.medlink.medicamento.entity.EstadoMedicamento;
import ar.edu.uade.medlink.medicamento.service.MedicamentoService;
import ar.edu.uade.medlink.prescripcion.dto.PrescripcionCreateRequest;
import ar.edu.uade.medlink.prescripcion.service.PrescripcionService;
import ar.edu.uade.medlink.presentacion.entity.CondicionDispensa;
import ar.edu.uade.medlink.presentacion.entity.EstadoPresentacion;
import ar.edu.uade.medlink.presentacion.repository.PresentacionRepository;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static ar.edu.uade.medlink.recetademo.RecetaDemoDtos.*;

@Service
public class RecetaDemoService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern CODIGO = Pattern.compile("MLD-[0-9A-F]{32}");
    private static final String ALFABETO_VERIFICACION = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private final RecetaDemoRepository recetas;
    private final PresentacionRepository presentaciones;
    private final AccountRepository cuentas;
    private final PrescripcionService prescripciones;
    private final AuditService audit;
    private final Clock clock;

    RecetaDemoService(RecetaDemoRepository recetas, PresentacionRepository presentaciones,
                      AccountRepository cuentas, PrescripcionService prescripciones, AuditService audit, Clock clock) {
        this.recetas=recetas;
        this.presentaciones=presentaciones;
        this.cuentas=cuentas;
        this.prescripciones=prescripciones;
        this.audit=audit;
        this.clock=clock;
    }

    @Transactional
    public Lote generar(GenerarLote entrada, CurrentAccount actor, HttpServletRequest request) {
        MedicamentoService.admin(actor);
        var ahora=clock.instant();
        var emision=LocalDate.ofInstant(ahora,ApiClock.ZONE);
        if (entrada.fechaVencimiento().isBefore(emision) || entrada.fechaVencimiento().getYear()>9999)
            throw ApiException.validation("El vencimiento debe ser hoy o una fecha futura válida.");
        var presentacion=presentaciones.findById(entrada.presentacionId()).orElseThrow(ApiException::notFound);
        var medicamento=presentacion.getMedicamento();
        if (presentacion.getCondicionDispensa()!=CondicionDispensa.BAJO_RECETA
            || presentacion.getEstado()!=EstadoPresentacion.ACTIVA || medicamento.getEstado()!=EstadoMedicamento.ACTIVO)
            throw ApiException.validation("Elegí una presentación activa BAJO_RECETA.");
        String nombreImpreso=RecetaDemoPdf.textoImprimible(medicamento.getNombre());
        String concentracion=RecetaDemoPdf.textoImprimible(presentacion.getConcentracionTexto());
        String descripcion=RecetaDemoPdf.textoImprimible(presentacion.getFormaFarmaceutica()+" - "
            +presentacion.getUnidadExpendio()+" x "+presentacion.getCantidadEnvase());
        UUID lote=UUID.randomUUID();
        List<Receta> resultado=new ArrayList<>(entrada.cantidad());
        for (int i=0;i<entrada.cantidad();i++) {
            RecetaDemo receta=null;
            for (int intento=0;intento<5 && receta==null;intento++) {
                receta=recetas.insertar(lote,nuevoCodigo(),nuevoCodigoVerificacion(),emision,entrada.fechaVencimiento(),ahora,
                    presentacion.getId(),nombreImpreso,concentracion,descripcion)
                    .orElse(null);
            }
            if (receta==null) throw new IllegalStateException("No se pudo obtener un código único");
            audit.record(actor.id(),actor.rol(),"GENERAR_RECETA_DEMO","RECETA_DEMO",receta.id(),null,
                null,null,resumen(receta,"DISPONIBLE"),request);
            resultado.add(new Receta(receta.id(),receta.codigo(),receta.estado(),receta.fechaEmision(),
                receta.fechaVencimiento(),receta.presentacionId(),receta.medicamento(),receta.concentracion(),
                receta.presentacion(),receta.cantidadAutorizada()));
        }
        return new Lote(lote,List.copyOf(resultado));
    }

    @Transactional(readOnly=true)
    public List<RecetaDemo> paraImprimir(long id, CurrentAccount actor) {
        MedicamentoService.admin(actor);
        return List.of(recetas.porId(id).orElseThrow(ApiException::notFound));
    }

    @Transactional(readOnly=true)
    public List<RecetaDemo> loteParaImprimir(UUID lote, CurrentAccount actor) {
        MedicamentoService.admin(actor);
        var datos=recetas.porLote(lote);
        if (datos.isEmpty()) throw ApiException.notFound();
        return datos;
    }

    @Transactional
    public Vinculacion vincular(String codigo, CurrentAccount actor, HttpServletRequest request) {
        return reclamar(codigo.strip().toUpperCase(Locale.ROOT),null,actor,request);
    }

    @Transactional
    public Vinculacion verificarYVincular(Lectura lectura, CurrentAccount actor, HttpServletRequest request) {
        RecetaDemoDocumento.validarLecturaCompleta(lectura);
        return reclamar(lectura.codigoReceta(),lectura,actor,request);
    }

    private Vinculacion reclamar(String codigo, Lectura lectura, CurrentAccount actor, HttpServletRequest request) {
        if (actor==null || !"USUARIO".equals(actor.rol())) throw ApiException.forbidden();
        if (!CODIGO.matcher(codigo).matches()) throw ApiException.notFound();
        // Same account guard as reservations and deactivation. No external I/O in this transaction.
        var cuenta=cuentas.byIdLocked(actor.id());
        if (!"ACTIVA".equals(cuenta.estado()))
            throw new ApiException(HttpStatus.FORBIDDEN,"ACCOUNT_DISABLED","La cuenta está deshabilitada.");
        if (cuenta.tokenVersion()!=actor.tokenVersion() || !"USUARIO".equals(cuenta.rol()))
            throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_TOKEN","La sesión ya no es válida.");
        long usuario=recetas.bloquearUsuario(actor.id()).orElseThrow(ApiException::notFound);
        var receta=recetas.bloquear(codigo).orElseThrow(ApiException::notFound);
        if (receta.usuarioId()!=null && receta.usuarioId()!=usuario) throw ApiException.notFound();
        Verificacion verificacion=lectura==null?null:RecetaDemoDocumento.verificar(receta,lectura);
        if (receta.prescripcionId()!=null)
            return new Vinculacion(false,prescripciones.detallePropio(receta.prescripcionId(),usuario),verificacion);

        var ahora=clock.instant();
        var hoy=LocalDate.ofInstant(ahora,ApiClock.ZONE);
        if (hoy.isBefore(receta.fechaEmision()) || hoy.isAfter(receta.fechaVencimiento()))
            throw ApiException.conflict("RECETA_DEMO_NO_VIGENTE","La receta de demostración está fuera de vigencia.");
        var entrada=new PrescripcionCreateRequest(receta.codigo(),usuario,receta.fechaEmision(),
            receta.fechaVencimiento(),List.of(new PrescripcionCreateRequest.Item(receta.presentacionId(),receta.cantidadAutorizada())));
        var prescripcion=prescripciones.crearDesdeRecetaDemo(entrada,actor,request);
        recetas.asignar(receta.id(),usuario,prescripcion.id(),ahora);
        audit.record(actor.id(),actor.rol(),"VINCULAR_RECETA_DEMO","RECETA_DEMO",receta.id(),usuario,
            null,Map.of("estado","DISPONIBLE"),resumen(receta,"ASIGNADA"),request);
        return new Vinculacion(true,prescripcion,verificacion);
    }

    static String nuevoCodigo() {
        byte[] bytes=new byte[16];
        RANDOM.nextBytes(bytes);
        return "MLD-"+HexFormat.of().withUpperCase().formatHex(bytes);
    }

    private static String nuevoCodigoVerificacion() {
        var codigo=new StringBuilder("MED");
        for (int bloque=0;bloque<3;bloque++) {
            codigo.append('-');
            for (int i=0;i<4;i++) codigo.append(ALFABETO_VERIFICACION.charAt(RANDOM.nextInt(ALFABETO_VERIFICACION.length())));
        }
        return codigo.toString();
    }

    private static Map<String,Object> resumen(RecetaDemo receta,String estado) {
        return Map.of("estado",estado,"presentacionId",receta.presentacionId(),
            "fechaEmision",receta.fechaEmision().toString(),"fechaVencimiento",receta.fechaVencimiento().toString());
    }
}
