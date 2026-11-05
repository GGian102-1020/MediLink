package ar.edu.uade.medlink.escaneo;

import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.escaneo.PrescriptionImageExtractor.Imagen;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
class ArchivoReceta {
    private static final int DIMENSION_MAXIMA=4096;
    private static final int PIXELES_MAXIMOS=12_000_000;
    private static final int LADO_PROVEEDOR=2400;
    private final long bytesMaximos;

    ArchivoReceta(@Value("${medlink.recetas-demo.scan.max-bytes:6291456}") long bytesMaximos) {
        if (bytesMaximos<1 || bytesMaximos>8*1024*1024)
            throw new IllegalArgumentException("El límite de escaneo debe estar entre 1 byte y 8 MiB");
        this.bytesMaximos=bytesMaximos;
    }

    Imagen preparar(MultipartFile archivo) {
        if (archivo.isEmpty()) throw invalido();
        if (archivo.getSize()>bytesMaximos) throw demasiadoGrande();
        try (var entrada=archivo.getInputStream()) {
            byte[] contenido=entrada.readNBytes((int)bytesMaximos+1);
            if (contenido.length>bytesMaximos) throw demasiadoGrande();
            var original=esPdf(contenido)?leerPdf(contenido):leerImagen(contenido);
            return new Imagen(normalizar(original),"image/jpeg");
        } catch (IOException | IllegalArgumentException ex) {
            throw invalido();
        }
    }

    private BufferedImage leerImagen(byte[] contenido) throws IOException {
        try (var datos=new MemoryCacheImageInputStream(new ByteArrayInputStream(contenido))) {
            var lectores=ImageIO.getImageReaders(datos);
            if (!lectores.hasNext()) throw invalido();
            var lector=lectores.next();
            try {
                String tipo=lector.getFormatName().toLowerCase(Locale.ROOT);
                if (!tipo.equals("jpeg") && !tipo.equals("png") && !tipo.equals("webp")) throw invalido();
                lector.setInput(datos,true,true);
                dimensiones(lector.getWidth(0),lector.getHeight(0));
                return lector.read(0);
            } finally {
                lector.dispose();
            }
        }
    }

    private BufferedImage leerPdf(byte[] contenido) throws IOException {
        try (var pdf=Loader.loadPDF(contenido)) {
            if (pdf.isEncrypted() || pdf.getNumberOfPages()!=1) throw invalido();
            var pagina=pdf.getPage(0);
            var area=pagina.getCropBox();
            dimensiones((int)Math.ceil(2*area.getWidth()),(int)Math.ceil(2*area.getHeight()));
            var renderer=new PDFRenderer(pdf);
            renderer.setSubsamplingAllowed(true);
            return renderer.renderImageWithDPI(0,144,ImageType.RGB);
        }
    }

    private boolean esPdf(byte[] b) {
        return b.length>=5 && b[0]=='%' && b[1]=='P' && b[2]=='D' && b[3]=='F' && b[4]=='-';
    }

    private void dimensiones(int ancho,int alto) {
        if (ancho<1 || alto<1 || ancho>DIMENSION_MAXIMA || alto>DIMENSION_MAXIMA
            || (long)ancho*alto>PIXELES_MAXIMOS) throw demasiadoGrande();
    }

    private byte[] normalizar(BufferedImage original) throws IOException {
        dimensiones(original.getWidth(),original.getHeight());
        double escala=Math.min(1d,(double)LADO_PROVEEDOR/Math.max(original.getWidth(),original.getHeight()));
        int ancho=Math.max(1,(int)Math.round(original.getWidth()*escala));
        int alto=Math.max(1,(int)Math.round(original.getHeight()*escala));
        var limpia=new BufferedImage(ancho,alto,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=limpia.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0,0,ancho,alto);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(original,0,0,ancho,alto,null);
        } finally {
            g.dispose();
            original.flush();
        }
        try (var salida=new ByteArrayOutputStream()) {
            if (!ImageIO.write(limpia,"jpeg",salida)) throw new IOException("No hay codificador JPEG");
            return salida.toByteArray();
        } finally {
            limpia.flush();
        }
    }

    static ApiException invalido() {
        return ApiException.validation("Adjuntá una imagen JPG, PNG o WebP válida, o un PDF de una sola página.");
    }

    static ApiException demasiadoGrande() {
        return new ApiException(HttpStatus.CONTENT_TOO_LARGE,"ARCHIVO_DEMASIADO_GRANDE",
            "El archivo supera el límite de tamaño o resolución.");
    }
}
