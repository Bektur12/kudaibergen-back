package kg.kudaibergen.common;

import java.util.HashSet;
import java.util.Set;

import kg.kudaibergen.common.web.PublicIds;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicIdsTest {

   @Test
   void десятьСимволовBase62БезПовторов() {
      Set<String> seen = new HashSet<>();
      for (int i = 0; i < 10_000; i++) {
         String id = PublicIds.next();
         assertThat(id).matches("[0-9A-Za-z]{10}");
         seen.add(id);
      }
      assertThat(seen).hasSize(10_000);
   }
}
