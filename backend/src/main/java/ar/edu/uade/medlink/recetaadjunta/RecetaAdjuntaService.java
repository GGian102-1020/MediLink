package ar.edu.uade.medlink.recetaadjunta;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.prescripcion.dto.PrescripcionCreateRequest;
import ar.edu.uade.medlink.prescripcion.service.PrescripcionService;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Receta que el paciente adjunta (PDF o imagen, incluida una foto/escaneo) al reservar un
 * medicamento BAJO_RECETA. Con ella se crea una Prescripción propia del paciente para esa
 * presentación y cantidad; la app usa su código en la reserva habitual. La farmacia verifica
 * el documento en el dashboard antes de entregar.
 */
@Service
public class RecetaAdjuntaService {
    static final long MAX_BYTES = 8L * 1024 * 1024;
    static final int VIGENCIA_DIAS = 30;
    static final int MAX_UNIDADES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Archivo(String nombre, String contentType, byte[] contenido) { }
    public record RecetaAdjuntaDTO(String codigoPrescripcion, long prescripcionId, String nombreArchivo,
                                   String contentType, long tamanoBytes, LocalDate fechaVencimiento) { }

    private final JdbcTemplate db;
    private final PrescripcionService prescripciones;
    private final AccountRepository accounts;
    private final Clock clock;

    public RecetaAdjuntaService(JdbcTemplate db, PrescripcionService prescripciones,
                                AccountRepository accounts, Clock clock) {
        this.db = db; this.prescripciones = prescripciones; this.accounts = accounts; this.clock = clock;
    }

    @Transactional
    public RecetaAdjuntaDTO adjuntar(MultipartFile archivo, Long presentacionId, Integer cantidad,
                                     CurrentAccount actor, HttpServletRequest request) {
        if (actor == null || !"USUARIO".equals(actor.rol())) throw ApiException.forbidden();
        if (presentacionId == null || presentacionId < 1) throw ApiException.validation("Presentación inválida.");
        if (cantidad == null || cantidad < 1 || cantidad > MAX_UNIDADES)
            throw ApiException.validation("Cantidad inválida (entre 1 y " + MAX_UNIDADES + ").");
        String condicion = db.query("SELECT condicion_dispensa::text FROM presentacion_medicamento WHERE id_presentacion = ?",
            (r, n) -> r.getString(1), presentacionId).stream().findFirst().orElseThrow(ApiException::notFound);
        if (!"BAJO_RECETA".equals(condicion))
            throw ApiException.validation("Esta presentación es de venta libre: no necesita receta.");
        Archivo valido = validar(archivo);

        long usuario = db.query("SELECT id_usuario FROM usuario WHERE id_cuenta = ?", (r, n) -> r.getLong(1), actor.id())
            .stream().findFirst().orElseThrow(ApiException::forbidden);
        LocalDate hoy = LocalDate.now(clock.withZone(ApiClock.ZONE));
        LocalDate vence = hoy.plusDays(VIGENCIA_DIAS);
        var prescripcion = prescripciones.crearDesdeRecetaAdjunta(new PrescripcionCreateRequest(nuevoCodigo(), usuario,
            hoy, vence, List.of(new PrescripcionCreateRequest.Item(presentacionId, cantidad))), actor, request);
        db.update("INSERT INTO receta_adjunta (id_prescripcion, nombre_archivo, content_type, tamano_bytes, contenido, " +
                "sha256, fecha_hora_carga) VALUES (?, ?, ?, ?, ?, ?, ?)",
            prescripcion.id(), valido.nombre(), valido.contentType(), valido.contenido().length, valido.contenido(),
            sha256(valido.contenido()), Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MILLIS)));
        return new RecetaAdjuntaDTO(prescripcion.codigoExterno(), prescripcion.id(), valido.nombre(),
            valido.contentType(), valido.contenido().length, vence);
    }

    /** Archivo de la receta adjunta a una reserva de la farmacia del operador. */
    @Transactional(readOnly = true)
    public Archivo deReservaDeFarmacia(long farmacia, String codigoReserva, CurrentAccount actor) {
        if (actor == null || !"OPERADOR".equals(actor.rol())) throw ApiException.forbidden();
        if (!Objects.equals(accounts.perfil(actor.id()).farmaciaId(), farmacia)) throw ApiException.forbidden();
        return db.query("""
            SELECT ra.nombre_archivo, ra.content_type, ra.contenido
            FROM reserva r
            JOIN item_prescripcion i ON i.id_item_prescripcion = r.id_item_prescripcion
            JOIN receta_adjunta ra ON ra.id_prescripcion = i.id_prescripcion
            WHERE r.id_farmacia = ? AND r.codigo_visible = upper(?)
            """, (r, n) -> new Archivo(r.getString(1), r.getString(2), r.getBytes(3)), farmacia, codigoReserva)
            .stream().findFirst().orElseThrow(ApiException::notFound);
    }

    /** Acepta PDF, JPG, PNG y WebP reconociendo el contenido real (no la extensión). */
    static Archivo validar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty())
            throw ApiException.validation("Adjuntá la receta en PDF o como imagen (JPG, PNG o WebP).");
        if (archivo.getSize() > MAX_BYTES) throw demasiadoGrande();
        byte[] contenido;
        try (var entrada = archivo.getInputStream()) {
            contenido = entrada.readNBytes((int) MAX_BYTES + 1);
        } catch (IOException e) {
            throw ApiException.validation("No se pudo leer el archivo.");
        }
        if (contenido.length > MAX_BYTES) throw demasiadoGrande();
        String tipo = tipo(contenido);
        if (tipo == null) throw ApiException.validation("Formato no admitido: usá PDF, JPG, PNG o WebP.");
        return new Archivo(nombre(archivo.getOriginalFilename(), tipo), tipo, contenido);
    }

    static String tipo(byte[] b) {
        if (empieza(b, "%PDF-".getBytes(StandardCharsets.US_ASCII))) return "application/pdf";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (empieza(b, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) return "image/png";
        if (b.length >= 12 && empieza(b, "RIFF".getBytes(StandardCharsets.US_ASCII))
            && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }

    private static boolean empieza(byte[] b, byte[] prefijo) {
        if (b.length < prefijo.length) return false;
        for (int i = 0; i < prefijo.length; i++) if (b[i] != prefijo[i]) return false;
        return true;
    }

    /** Nombre seguro para mostrar y descargar: sin rutas ni caracteres de control. */
    static String nombre(String original, String tipo) {
        String ext = switch (tipo) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String base = original == null ? "" : original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N} ._()-]", "").strip();
        int punto = base.lastIndexOf('.');
        if (punto > 0) base = base.substring(0, punto);
        if (base.isBlank()) base = "receta";
        if (base.length() > 100) base = base.substring(0, 100);
        return base + ext;
    }

    private static String nuevoCodigo() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return "ADJ-" + HexFormat.of().withUpperCase().formatHex(bytes);
    }

    private static String sha256(byte[] contenido) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static ApiException demasiadoGrande() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "ARCHIVO_DEMASIADO_GRANDE",
            "El archivo supera los 8 MB. Probá con una foto de menor resolución.");
    }
}
