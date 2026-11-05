package ar.edu.uade.medlink.recetademo;

import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static ar.edu.uade.medlink.recetademo.RecetaDemoDtos.*;

@RestController
@RequestMapping("/api/admin/recetas-demo")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class RecetaDemoAdminController {
    private final RecetaDemoService recetas;
    private final RecetaDemoPdf pdf;
    private final boolean pdfAlteradoHabilitado;

    RecetaDemoAdminController(RecetaDemoService recetas,RecetaDemoPdf pdf,
                             @Value("${medlink.recetas-demo.pdf-alterado.enabled:false}") boolean pdfAlteradoHabilitado) {
        this.recetas=recetas;
        this.pdf=pdf;
        this.pdfAlteradoHabilitado=pdfAlteradoHabilitado;
    }

    @PostMapping("/lotes")
    public ResponseEntity<Lote> generar(@RequestBody @Valid GenerarLote entrada,
        @AuthenticationPrincipal CurrentAccount actor,HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(recetas.generar(entrada,actor,request));
    }

    @GetMapping(value="/{id}/pdf",produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> individual(@PathVariable long id,@AuthenticationPrincipal CurrentAccount actor) {
        return descargar("receta-demo-"+id+".pdf",pdf.generar(recetas.paraImprimir(id,actor)));
    }

    @GetMapping(value="/{id}/pdf-alterado",produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> simulacion(@PathVariable long id,@AuthenticationPrincipal CurrentAccount actor) {
        if (!pdfAlteradoHabilitado) throw ApiException.notFound();
        var receta=recetas.paraImprimir(id,actor).getFirst();
        if (receta.codigoVerificacion()==null)
            throw ApiException.validation("Generá una receta con código secundario para descargar la simulación.");
        return descargar("receta-demo-"+id+"-alterada.pdf",pdf.generarSimulacion(receta));
    }

    @GetMapping(value="/lotes/{lote}/pdf",produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> lote(@PathVariable UUID lote,@AuthenticationPrincipal CurrentAccount actor) {
        return descargar("lote-demo-"+lote+".pdf",pdf.generar(recetas.loteParaImprimir(lote,actor)));
    }

    private ResponseEntity<byte[]> descargar(String archivo,byte[] bytes) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(archivo).build().toString())
            .body(bytes);
    }
}
