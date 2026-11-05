package ar.edu.uade.medlink.escaneo;

import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.recetademo.RecetaDemoDtos.Vinculacion;
import ar.edu.uade.medlink.recetademo.RecetaDemoService;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RecetaDemoEscaneoService {
    private final PrescriptionImageExtractor extractor;
    private final ArchivoReceta archivos;
    private final RecetaDemoService recetas;
    private final Clock clock;
    private final Semaphore cupos=new Semaphore(3);
    private final Map<Long,Integer> porCuenta=new HashMap<>();
    private long minuto=Long.MIN_VALUE;
    private int intentos;

    RecetaDemoEscaneoService(PrescriptionImageExtractor extractor,ArchivoReceta archivos,
                             RecetaDemoService recetas,Clock clock) {
        this.extractor=extractor;
        this.archivos=archivos;
        this.recetas=recetas;
        this.clock=clock;
    }

    // External I/O and file preparation happen before opening the claim transaction.
    public Vinculacion escanear(MultipartFile archivo,CurrentAccount actor,HttpServletRequest request) {
        if (actor==null || !"USUARIO".equals(actor.rol())) throw ApiException.forbidden();
        if (!extractor.disponible())
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"ESCANEO_NO_CONFIGURADO",
                "El escaneo con IA no está configurado. Podés ingresar el código.");
        if (!cupos.tryAcquire()) throw limite();
        try {
            registrarIntento(actor.id());
            var lectura=extractor.extraer(archivos.preparar(archivo));
            return recetas.verificarYVincular(lectura,actor,request);
        } finally {
            cupos.release();
        }
    }

    private synchronized void registrarIntento(long cuenta) {
        long actual=Math.floorDiv(clock.instant().getEpochSecond(),60);
        if (actual!=minuto) {
            minuto=actual;
            intentos=0;
            porCuenta.clear();
        }
        if (intentos>=60 || porCuenta.getOrDefault(cuenta,0)>=6) throw limite();
        intentos++;
        porCuenta.merge(cuenta,1,Integer::sum);
    }

    private static ApiException limite() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS,"ESCANEO_LIMITE",
            "Se alcanzó el límite de escaneos. Probá nuevamente en unos segundos.");
    }
}
