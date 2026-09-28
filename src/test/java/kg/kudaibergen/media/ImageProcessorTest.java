package kg.kudaibergen.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import kg.kudaibergen.common.error.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

   @Test
   void большоеФотоУменьшаетсяДо1080И320() throws IOException {
      ImageProcessor.Result result = ImageProcessor.process(png(2000, 1500, BufferedImage.TYPE_INT_ARGB));
      BufferedImage large = ImageIO.read(new ByteArrayInputStream(result.large()));
      BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(result.thumb()));
      assertThat(large.getWidth()).isEqualTo(1080);
      assertThat(large.getHeight()).isEqualTo(810);
      assertThat(thumb.getWidth()).isEqualTo(320);
      assertThat(result.width()).isEqualTo(1080);
      // JPEG: FF D8
      assertThat(result.large()[0]).isEqualTo((byte) 0xFF);
      assertThat(result.large()[1]).isEqualTo((byte) 0xD8);
   }

   @Test
   void маленькоеНеУвеличивается() throws IOException {
      ImageProcessor.Result result = ImageProcessor.process(png(200, 100, BufferedImage.TYPE_INT_RGB));
      assertThat(result.width()).isEqualTo(200);
      assertThat(result.height()).isEqualTo(100);
   }

   @Test
   void неКартинкаОтклоняется() {
      assertThatThrownBy(() -> ImageProcessor.process("это не фото".getBytes()))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("JPG или PNG");
   }

   private static byte[] png(int width, int height, int type) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(new BufferedImage(width, height, type), "png", out);
      return out.toByteArray();
   }
}
