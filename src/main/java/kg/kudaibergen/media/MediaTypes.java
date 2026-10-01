package kg.kudaibergen.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

/**
 * Тип файла, если клиент его не прислал. React Native часто отдаёт в multipart «application/octet-stream»
 * или пустой тип — тогда тип определяется по первым байтам файла, а если не вышло — по расширению.
 */
public final class MediaTypes {

   private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
         Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("png", "image/png"),
         Map.entry("webp", "image/webp"), Map.entry("gif", "image/gif"), Map.entry("heic", "image/heic"),
         Map.entry("heif", "image/heif"),
         Map.entry("mp4", "video/mp4"), Map.entry("m4v", "video/mp4"), Map.entry("mov", "video/quicktime"),
         Map.entry("3gp", "video/3gpp"), Map.entry("webm", "video/webm"), Map.entry("mkv", "video/x-matroska"),
         Map.entry("m4a", "audio/mp4"), Map.entry("aac", "audio/aac"), Map.entry("mp3", "audio/mpeg"),
         Map.entry("ogg", "audio/ogg"), Map.entry("opus", "audio/ogg"), Map.entry("wav", "audio/wav"),
         Map.entry("amr", "audio/amr"));

   private MediaTypes() {
   }

   /**
    * Тип файла с нужным префиксом (image/, video/, audio/). Присланный клиентом — как есть; если он пустой или
    * «application/octet-stream» — определяется по содержимому, иначе по расширению. Не вышло — присланный.
    */
   public static String resolve(MultipartFile file, String prefix) {
      String declared = file.getContentType();
      if (declared != null && declared.startsWith(prefix) || !generic(declared)) {
         return declared;
      }
      String sniffed = sniff(file, prefix);
      if (sniffed != null && sniffed.startsWith(prefix)) {
         return sniffed;
      }
      String byName = byExtension(file.getOriginalFilename(), prefix);
      return byName != null ? byName : declared;
   }

   /** Тип, по которому ничего не понять: пустой, octet-stream, «любой». Явный другой тип не переопределяется. */
   static boolean generic(String type) {
      if (type == null || type.isBlank() || !type.contains("/")) {
         return true;
      }
      String value = type.toLowerCase(Locale.ROOT).strip();
      return value.startsWith("application/octet-stream") || value.startsWith("binary/") || value.equals("*/*")
            || value.equals("application/unknown");
   }

   static String byExtension(String name, String prefix) {
      if (name == null || name.lastIndexOf('.') < 0) {
         return null;
      }
      String type = BY_EXTENSION.get(name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT));
      if (type == null) {
         return null;
      }
      // .3gp / .mp4 / .webm бывают и голосовыми: контейнер тот же, тип по тому, что ждём
      if (prefix.equals("audio/") && type.startsWith("video/")) {
         return "audio/" + type.substring("video/".length());
      }
      return type;
   }

   static String sniff(MultipartFile file, String prefix) {
      byte[] head = new byte[16];
      int read;
      try (InputStream in = file.getInputStream()) {
         read = in.readNBytes(head, 0, head.length);
      } catch (IOException e) {
         return null;
      }
      return sniff(head, read, prefix);
   }

   static String sniff(byte[] b, int n, String prefix) {
      if (n >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
         return "image/jpeg";
      }
      if (n >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
         return "image/png";
      }
      if (n >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
         return "image/gif";
      }
      if (n >= 12 && ascii(b, 0, 4).equals("RIFF")) {
         String kind = ascii(b, 8, 4);
         return kind.equals("WEBP") ? "image/webp" : kind.equals("WAVE") ? "audio/wav" : null;
      }
      if (n >= 12 && ascii(b, 4, 4).equals("ftyp")) {
         String brand = ascii(b, 8, 4).toLowerCase(Locale.ROOT);
         if (brand.startsWith("hei") || brand.startsWith("mif") || brand.startsWith("hev")) {
            return "image/heic";
         }
         if (brand.startsWith("qt")) {
            return "video/quicktime";
         }
         if (brand.startsWith("m4a") || prefix.equals("audio/")) {
            return brand.startsWith("3g") ? "audio/3gpp" : "audio/mp4";
         }
         return brand.startsWith("3g") ? "video/3gpp" : "video/mp4";
      }
      if (n >= 4 && (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45 && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3) {
         return prefix.equals("audio/") ? "audio/webm" : "video/webm";
      }
      if (n >= 4 && ascii(b, 0, 4).equals("OggS")) {
         return "audio/ogg";
      }
      if (n >= 3 && ascii(b, 0, 3).equals("ID3")) {
         return "audio/mpeg";
      }
      if (n >= 5 && ascii(b, 0, 5).equals("#!AMR")) {
         return "audio/amr";
      }
      return null;
   }

   private static String ascii(byte[] bytes, int from, int length) {
      return new String(bytes, from, length, StandardCharsets.ISO_8859_1);
   }
}
