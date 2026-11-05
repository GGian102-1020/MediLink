package ar.edu.uade.medlink.escaneo;

import ar.edu.uade.medlink.common.error.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class ArchivoRecetaTest {
    private final ArchivoReceta archivos=new ArchivoReceta(1024*1024);

    @Test void detectaFormatoPorContenidoYNormalizaJpgPngWebp() throws Exception {
        for (String formato:new String[]{"jpeg","png"}) {
            var salida=new ByteArrayOutputStream();
            assertTrue(ImageIO.write(new BufferedImage(3,2,BufferedImage.TYPE_INT_RGB),formato,salida));
            comprobar(salida.toByteArray(),3,2);
        }
        comprobar(Base64.getDecoder().decode("UklGRiQAAABXRUJQVlA4IBgAAAAwAQCdASoDAAIAAUAmJaQAA3AA/vz0AAA="),3,2);
    }

    @Test void rasterizaSoloPdfDeUnaPagina() throws Exception {
        var imagen=archivos.preparar(archivo(pdf(1)));
        assertEquals("image/jpeg",imagen.tipo());
        assertNotNull(ImageIO.read(new ByteArrayInputStream(imagen.bytes())));
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,()->archivos.preparar(archivo(pdf(2)))).codigo());
    }

    @Test void compruebaResolucionAntesDeProcesarLaFoto() throws Exception {
        var salida=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4100,8,BufferedImage.TYPE_INT_RGB),"png",salida);
        assertEquals(413,assertThrows(ApiException.class,()->archivos.preparar(archivo(salida.toByteArray()))).status().value());
    }

    private void comprobar(byte[] bytes,int ancho,int alto) throws Exception {
        var resultado=archivos.preparar(archivo(bytes));
        assertEquals("image/jpeg",resultado.tipo());
        var foto=ImageIO.read(new ByteArrayInputStream(resultado.bytes()));
        assertEquals(ancho,foto.getWidth());
        assertEquals(alto,foto.getHeight());
    }

    private MockMultipartFile archivo(byte[] bytes) {
        return new MockMultipartFile("archivo","cualquier-extension.txt","text/plain",bytes);
    }

    private byte[] pdf(int paginas) throws Exception {
        try (var doc=new PDDocument(); var salida=new ByteArrayOutputStream()) {
            for (int i=0;i<paginas;i++) doc.addPage(new PDPage());
            doc.save(salida);
            return salida.toByteArray();
        }
    }
}
