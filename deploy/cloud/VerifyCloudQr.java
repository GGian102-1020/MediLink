import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.common.GlobalHistogramBinarizer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import javax.imageio.ImageIO;

/** Decode the QR from an actual landing screenshot using the backend's existing ZXing dependency. */
class VerifyCloudQr {
    public static void main(String[] args) throws Exception {
        BufferedImage image = ImageIO.read(new File(args[0]));
        System.out.println("Screenshot dimensions: " + image.getWidth() + "x" + image.getHeight());
        if (args.length == 6) image = image.getSubimage(Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]));
        byte[] pixels = new byte[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int rgb = image.getRGB(x, y);
            pixels[y * image.getWidth() + x] = (byte) ((((rgb >> 16) & 255) + 2 * ((rgb >> 8) & 255) + (rgb & 255)) / 4);
        }
        LuminanceSource source = new LuminanceSource(image.getWidth(), image.getHeight()) {
            public byte[] getRow(int y, byte[] row) {
                if (row == null || row.length < getWidth()) row = new byte[getWidth()];
                System.arraycopy(pixels, y * getWidth(), row, 0, getWidth());
                return row;
            }
            public byte[] getMatrix() { return pixels; }
        };
        var hints = new java.util.EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, true);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, java.util.List.of(BarcodeFormat.QR_CODE));
        if (args.length == 6) hints.put(DecodeHintType.PURE_BARCODE, true);
        Result result;
        try { result = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)), hints); }
        catch (NotFoundException failure) { result = new MultiFormatReader().decode(new BinaryBitmap(new GlobalHistogramBinarizer(source)), hints); }
        if (!result.getText().equals(args[1])) throw new IllegalStateException("QR does not match the APK URL");
        System.out.println("QR decoded from screenshot; exact APK URL verified: " + result.getText());
    }
}
