package ar.edu.uade.medlink.recetademo;

import ar.edu.uade.medlink.common.error.ApiException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;
import static ar.edu.uade.medlink.recetademo.RecetaDemoDocumento.*;

@Component
class RecetaDemoPdf {
    private static final Color TINTA=new Color(20,42,65);
    private static final Color ROJO=new Color(155,24,38);
    private static final Color FONDO_VERIFICACION=new Color(236,243,249);

    byte[] generar(List<RecetaDemo> recetas) {
        return generar(recetas,false);
    }

    byte[] generarSimulacion(RecetaDemo receta) {
        return generar(List.of(receta),true);
    }

    private byte[] generar(List<RecetaDemo> recetas, boolean simulacion) {
        try (var doc=new PDDocument(); var out=new ByteArrayOutputStream();
             var logo=RecetaDemoPdf.class.getResourceAsStream("/recetas-demo/medilink-logo.png")) {
            var imagen=PDImageXObject.createFromByteArray(doc,logo.readAllBytes(),"MediLink");
            var regular=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            var negrita=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            doc.getDocumentInformation().setTitle("MediLink | Recetas simuladas");
            for (var receta:recetas) pagina(doc,receta,imagen,regular,negrita,simulacion);
            doc.save(out);
            return out.toByteArray();
        } catch (IOException | WriterException e) {
            throw new IllegalStateException("No se pudo generar el PDF",e);
        }
    }

    private void pagina(PDDocument doc, RecetaDemo receta, PDImageXObject logo,
                        PDType1Font regular, PDType1Font negrita, boolean simulacion) throws IOException, WriterException {
        var page=new PDPage(PDRectangle.A4);
        doc.addPage(page);
        try (var canvas=new PDPageContentStream(doc,page)) {
            canvas.beginText();
            canvas.setFont(negrita,61);
            canvas.setNonStrokingColor(new Color(236,241,245));
            canvas.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(34),67,315));
            canvas.showText("DEMOSTRACIÓN");
            canvas.endText();

            float escala=Math.min(56f/logo.getHeight(),145f/logo.getWidth());
            canvas.drawImage(logo,48,733,logo.getWidth()*escala,logo.getHeight()*escala);
            texto(canvas,negrita,21,209,761,TINTA,"MediLink");
            texto(canvas,regular,11,210,742,TINTA,"Demostración universitaria");
            texto(canvas,negrita,23,48,697,TINTA,TITULO);
            canvas.setNonStrokingColor(ROJO);
            canvas.addRect(48,635,499,36);
            canvas.fill();
            texto(canvas,negrita,16,60,647,Color.WHITE,LEYENDA);
            texto(canvas,regular,12,48,602,TINTA,"Profesional ficticio: "+PROFESIONAL);
            texto(canvas,regular,12,48,581,TINTA,"Institución ficticia: "+INSTITUCION);
            texto(canvas,regular,12,48,560,TINTA,"Paciente: "+PACIENTE);
            texto(canvas,regular,12,48,521,TINTA,"Emisión: "+receta.fechaEmision());
            texto(canvas,regular,12,310,521,TINTA,"Vencimiento: "+receta.fechaVencimiento());
            texto(canvas,negrita,14,48,481,TINTA,"Detalle de la receta simulada");
            float y=lineas(canvas,regular,462,"Medicamento: "+receta.medicamento());
            String concentracion=simulacion?concentracionSimulada(receta.concentracion()):receta.concentracion();
            y=lineas(canvas,regular,y,"Concentración: "+concentracion);
            y=lineas(canvas,regular,y,"Presentación: "+receta.presentacion());
            y=lineas(canvas,regular,y,"Cantidad autorizada: "+receta.cantidadAutorizada());
            lineas(canvas,regular,y-9,"Indicaciones simuladas: "+INDICACIONES);
            verificacion(doc,canvas,receta,regular,negrita);
            if (simulacion)
                texto(canvas,negrita,13,48,98,ROJO,"SIMULACIÓN — DOCUMENTO ALTERADO");
            texto(canvas,regular,11,48,76,TINTA,"Uso académico exclusivo. Datos y documento ficticios.");
            texto(canvas,negrita,11,48,56,ROJO,"No habilita dispensación, tratamiento ni retiro en una farmacia real.");
        }
    }

    private void verificacion(PDDocument doc, PDPageContentStream canvas, RecetaDemo receta,
                              PDType1Font regular, PDType1Font negrita) throws IOException, WriterException {
        canvas.setNonStrokingColor(FONDO_VERIFICACION);
        canvas.addRect(48,114,499,114);
        canvas.fill();
        canvas.drawImage(LosslessFactory.createFromImage(doc,qr(receta.codigo())),55,119,104,104);
        texto(canvas,negrita,13,169,212,TINTA,"VERIFICACIÓN DE DOCUMENTO MEDILINK");
        texto(canvas,regular,10,169,195,TINTA,"Código principal de receta");
        texto(canvas,negrita,12,169,180,TINTA,receta.codigo());
        if (receta.codigoVerificacion()==null) {
            texto(canvas,regular,11,169,155,TINTA,"Sin código secundario (emisión anterior)");
        } else {
            texto(canvas,regular,10,169,163,TINTA,"Código secundario de verificación");
            texto(canvas,negrita,14,169,145,TINTA,receta.codigoVerificacion());
        }
        texto(canvas,regular,10,169,127,TINTA,"Fotografiá la receta completa para verificar los datos en MediLink.");
    }

    private String concentracionSimulada(String original) {
        return original.equalsIgnoreCase("200 mcg")?"100 mcg":"200 mcg";
    }

    private float lineas(PDPageContentStream canvas, PDType1Font fuente, float y, String valor) throws IOException {
        // Wrap by rendered width, including catalog values with no spaces.
        StringBuilder linea=new StringBuilder();
        for (String palabra:valor.split(" ")) {
            if (!linea.isEmpty() && fuente.getStringWidth(linea+" "+palabra)*0.011f>490) {
                texto(canvas,fuente,11,48,y,TINTA,linea.toString());
                y-=15;
                linea.setLength(0);
            }
            if (!linea.isEmpty()) linea.append(' ');
            for (int i=0;i<palabra.length();i++) {
                if (!linea.isEmpty() && fuente.getStringWidth(linea.toString()+palabra.charAt(i))*0.011f>490) {
                    texto(canvas,fuente,11,48,y,TINTA,linea.toString());
                    y-=15;
                    linea.setLength(0);
                }
                linea.append(palabra.charAt(i));
            }
        }
        if (!linea.isEmpty()) texto(canvas,fuente,11,48,y,TINTA,linea.toString());
        return y-15;
    }

    static String textoImprimible(String valor) {
        String normalizado=Normalizer.normalize(valor,Normalizer.Form.NFC).strip().replaceAll("(?U)\\s+"," ");
        try {
            new PDType1Font(Standard14Fonts.FontName.HELVETICA).encode(normalizado);
            return normalizado;
        } catch (IllegalArgumentException | IOException ex) {
            throw ApiException.validation("La presentación contiene caracteres que no se pueden imprimir fielmente.");
        }
    }

    private void texto(PDPageContentStream canvas,PDType1Font fuente,float size,float x,float y,
                       Color color,String valor) throws IOException {
        canvas.beginText();
        canvas.setFont(fuente,size);
        canvas.setNonStrokingColor(color);
        canvas.newLineAtOffset(x,y);
        canvas.showText(valor);
        canvas.endText();
    }

    private BufferedImage qr(String codigo) throws WriterException {
        var matrix=new QRCodeWriter().encode(codigo,BarcodeFormat.QR_CODE,260,260);
        var image=new BufferedImage(matrix.getWidth(),matrix.getHeight(),BufferedImage.TYPE_BYTE_BINARY);
        for (int y=0;y<matrix.getHeight();y++)
            for (int x=0;x<matrix.getWidth();x++) image.setRGB(x,y,matrix.get(x,y)?0xff000000:0xffffffff);
        return image;
    }
}
