package kg.kudaibergen.media;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Длительность MP4 / MOV (ISO BMFF) из заголовка moov/mvhd — без ffmpeg. Файл читается потоком, крупные
 * блоки (mdat) пропускаются. Не MP4 или повреждён — пусто.
 */
public final class Mp4Duration {

   private static final int MAX_BOXES = 10_000;

   private Mp4Duration() {
   }

   public static Optional<Double> read(InputStream input) {
      try {
         DataInputStream in = new DataInputStream(input);
         for (int i = 0; i < MAX_BOXES; i++) {
            long[] header = header(in);
            if (header == null) {
               return Optional.empty();
            }
            long payload = header[0];
            String type = type(header[1]);
            if (type.equals("moov")) {
               return movieHeader(in, payload);
            }
            if (payload < 0) {
               return Optional.empty();
            }
            in.skipNBytes(payload);
         }
         return Optional.empty();
      } catch (IOException | RuntimeException e) {
         return Optional.empty();
      }
   }

   /** Внутри moov ищем mvhd: version 0 — поля по 4 байта, version 1 — время и длительность по 8. */
   private static Optional<Double> movieHeader(DataInputStream in, long moovPayload) throws IOException {
      long left = moovPayload;
      while (left > 8) {
         long[] header = header(in);
         if (header == null) {
            return Optional.empty();
         }
         long payload = header[0];
         if (payload < 0) {
            return Optional.empty();
         }
         left -= payload + header[2];
         if (type(header[1]).equals("mvhd")) {
            int version = in.readUnsignedByte();
            in.skipNBytes(3);
            long timescale;
            long duration;
            if (version == 1) {
               in.skipNBytes(16);
               timescale = Integer.toUnsignedLong(in.readInt());
               duration = in.readLong();
            } else {
               in.skipNBytes(8);
               timescale = Integer.toUnsignedLong(in.readInt());
               duration = Integer.toUnsignedLong(in.readInt());
            }
            return timescale == 0 ? Optional.empty() : Optional.of((double) duration / timescale);
         }
         in.skipNBytes(payload);
      }
      return Optional.empty();
   }

   /** {размер данных без заголовка, тип, размер заголовка}; конец файла — null; размер 0 (до конца) — -1. */
   private static long[] header(DataInputStream in) throws IOException {
      long size;
      try {
         size = Integer.toUnsignedLong(in.readInt());
      } catch (EOFException end) {
         return null;
      }
      long type = Integer.toUnsignedLong(in.readInt());
      long headerSize = 8;
      if (size == 1) {
         size = in.readLong();
         headerSize = 16;
      } else if (size == 0) {
         return new long[]{-1, type, headerSize};
      }
      if (size < headerSize) {
         throw new IOException("Повреждённый блок MP4");
      }
      return new long[]{size - headerSize, type, headerSize};
   }

   private static String type(long value) {
      byte[] bytes = {(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value};
      return new String(bytes, StandardCharsets.ISO_8859_1);
   }
}
