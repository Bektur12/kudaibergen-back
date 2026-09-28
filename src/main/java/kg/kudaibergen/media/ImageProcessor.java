package kg.kudaibergen.media;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import kg.kudaibergen.common.error.BadRequestException;

/**
 * Пережатие фото в JPEG двух размеров. Декодирование и новая запись выбрасывают все метаданные —
 * EXIF и геометка не попадают в хранилище (ТЗ 14). Размер кадра проверяется до декодирования,
 * чтобы «бомба» в пару килобайт не развернулась в гигабайты памяти.
 */
final class ImageProcessor {

   static final int LARGE = 1080;
   static final int THUMB = 320;
   private static final long MAX_PIXELS = 50_000_000L;
   private static final float JPEG_QUALITY = 0.85f;

   private ImageProcessor() {
   }

   record Result(byte[] large, byte[] thumb, int width, int height) {
   }

   static Result process(byte[] bytes) {
      BufferedImage source = decode(bytes);
      BufferedImage large = fit(source, LARGE);
      return new Result(jpeg(large), jpeg(fit(source, THUMB)), large.getWidth(), large.getHeight());
   }

   private static BufferedImage decode(byte[] bytes) {
      try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
         Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
         if (readers == null || !readers.hasNext()) {
            throw unsupported();
         }
         ImageReader reader = readers.next();
         try {
            reader.setInput(input, true, true);
            if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
               throw new BadRequestException("IMAGE_TOO_LARGE", "Слишком большое разрешение фото");
            }
            return reader.read(0);
         } finally {
            reader.dispose();
         }
      } catch (IOException e) {
         throw unsupported();
      }
   }

   /** Уменьшить так, чтобы длинная сторона была не больше max; маленькие не увеличиваем. Прозрачность — на белом. */
   static BufferedImage fit(BufferedImage source, int max) {
      int width = source.getWidth();
      int height = source.getHeight();
      double scale = Math.min(1.0, (double) max / Math.max(width, height));
      int targetWidth = Math.max(1, (int) Math.round(width * scale));
      int targetHeight = Math.max(1, (int) Math.round(height * scale));
      BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = target.createGraphics();
      try {
         graphics.setColor(Color.WHITE);
         graphics.fillRect(0, 0, targetWidth, targetHeight);
         graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
         graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
         graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
      } finally {
         graphics.dispose();
      }
      return target;
   }

   private static byte[] jpeg(BufferedImage image) {
      ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
         writer.setOutput(output);
         ImageWriteParam param = writer.getDefaultWriteParam();
         param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
         param.setCompressionQuality(JPEG_QUALITY);
         writer.write(null, new IIOImage(image, null, null), param);
      } catch (IOException e) {
         throw new UncheckedIOException("Не удалось сжать фото", e);
      } finally {
         writer.dispose();
      }
      return out.toByteArray();
   }

   private static BadRequestException unsupported() {
      return new BadRequestException("UNSUPPORTED_IMAGE", "Не удалось прочитать фото — отправьте JPG или PNG");
   }
}
