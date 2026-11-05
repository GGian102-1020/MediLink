package ar.edu.uade.medlink.recetademo;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecetaDemoPdfTest {
    @Test void validaTextoDelCatalogoAntesDeEmitirUnaRecetaImposibleDeEscanear() {
        assertEquals("Levotiroxina 100 µg",RecetaDemoPdf.textoImprimible("Levotiroxina  100 µg"));
        assertThrows(ar.edu.uade.medlink.common.error.ApiException.class,()->RecetaDemoPdf.textoImprimible("Nombre 🧪"));
    }

    @Test void pdfA4ImprimeDatosPersistidosYUnQrLegible() throws Exception {
        var codigo=RecetaDemoService.nuevoCodigo();
        String secundario="MED-8K29-P7Q4-ABCD";
        var receta=new RecetaDemo(13,UUID.randomUUID(),codigo,secundario,"DISPONIBLE",LocalDate.of(2026,11,5),
            LocalDate.of(2026,12,4),9,"Levotiroxina","100 mcg","Comprimidos - Caja x 50",1,null,null);
        var pdf=new RecetaDemoPdf();
        var bytes=pdf.generar(List.of(receta));
        try (var doc=Loader.loadPDF(bytes)) {
            assertEquals(1,doc.getNumberOfPages());
            assertEquals(PDRectangle.A4.getHeight(),doc.getPage(0).getMediaBox().getHeight());
            var texto=new PDFTextStripper().getText(doc);
            for (var dato:List.of("RECETA ELECTRÓNICA SIMULADA","SIN VALIDEZ MÉDICA","DEMOSTRACIÓN",
                "Titular pendiente de asignación","Levotiroxina","100 mcg","Comprimidos - Caja x 50",
                "2026-11-05","2026-12-04","VERIFICACIÓN DE DOCUMENTO MEDILINK",codigo,secundario))
                assertTrue(texto.contains(dato),dato);
            var imagen=new PDFRenderer(doc).renderImageWithDPI(0,144);
            assertEquals(codigo,leerQr(imagen));
            Path target=Path.of("target","receta-demo-muestra.png");
            ImageIO.write(imagen,"png",target.toFile());
            Files.write(target.resolveSibling("receta-demo-muestra.pdf"),bytes);
        }
        try (var doc=Loader.loadPDF(pdf.generarSimulacion(receta))) {
            var imagen=new PDFRenderer(doc).renderImageWithDPI(0,144);
            assertEquals(codigo,leerQr(imagen));
            ImageIO.write(imagen,"png",Path.of("target","receta-demo-muestra-alterada.png").toFile());
        }
    }

    private String leerQr(BufferedImage imagen) throws ReaderException {
        var pixeles=imagen.getRGB(0,0,imagen.getWidth(),imagen.getHeight(),null,0,imagen.getWidth());
        var binario=new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(imagen.getWidth(),imagen.getHeight(),pixeles)));
        return new QRCodeReader().decode(binario).getText();
    }
}
