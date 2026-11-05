package ar.edu.uade.medlink.recetademo;

import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.escaneo.RecetaDemoEscaneoService;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static ar.edu.uade.medlink.recetademo.RecetaDemoDtos.*;

@RestController
@RequestMapping("/api/me/recetas-demo")
@PreAuthorize("hasRole('USUARIO')")
public class RecetaDemoUsuarioController {
    private final RecetaDemoService recetas;
    private final RecetaDemoEscaneoService escaneo;

    RecetaDemoUsuarioController(RecetaDemoService recetas,RecetaDemoEscaneoService escaneo) {
        this.recetas=recetas;
        this.escaneo=escaneo;
    }

    @PostMapping("/vinculacion")
    public ResponseEntity<Vinculacion> vincular(@RequestBody @Valid Vincular entrada,
        @AuthenticationPrincipal CurrentAccount actor,HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(recetas.vincular(entrada.codigoReceta(),actor,request));
    }

    @PostMapping(value="/escaneo",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Vinculacion> escanear(@RequestPart(value="archivo",required=false) MultipartFile archivo,
        @AuthenticationPrincipal CurrentAccount actor,HttpServletRequest request) {
        if (archivo==null) throw ApiException.validation("Adjuntá el archivo de la receta.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(escaneo.escanear(archivo,actor,request));
    }
}
