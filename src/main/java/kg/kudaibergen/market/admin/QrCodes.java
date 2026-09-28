package kg.kudaibergen.market.admin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/** PNG с QR-кодом. Уровень коррекции M — наклейки на рынке пачкаются. */
public final class QrCodes {

   private QrCodes() {
   }

   public static byte[] png(String content, int size) {
      try {
         BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size,
               Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         MatrixToImageWriter.writeToStream(matrix, "PNG", out);
         return out.toByteArray();
      } catch (WriterException e) {
         throw new IllegalArgumentException("Не удалось построить QR-код", e);
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }
}
