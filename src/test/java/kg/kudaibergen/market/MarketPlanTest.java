package kg.kudaibergen.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.market.dto.MapDto;
import kg.kudaibergen.market.geo.Point;
import org.junit.jupiter.api.Test;

/** Карта рынка без контейнеров: схема Геологии из миграции V43 собирается в MapDto и ложится на GPS. */
class MarketPlanTest {

   private final ObjectMapper mapper = new ObjectMapper();

   private JsonNode geologiya() throws Exception {
      try (var in = getClass().getResourceAsStream("/db/migration/V43__market_plans.sql")) {
         String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
         var matcher = Pattern.compile("\\$plan\\$(.*?)\\$plan\\$", Pattern.DOTALL).matcher(sql);
         assertThat(matcher.find()).isTrue();
         return mapper.readTree(matcher.group(1));
      }
   }

   @Test
   void geologiyaBuildsAndAnchorsFit() throws Exception {
      JsonNode data = geologiya();
      MapDto map = MarketPlanService.build(1, data, mapper);

      assertThat(map.rows()).hasSize(7);
      assertThat(map.rows()).allSatisfy(row -> {
         assertThat(row.id()).isNegative();
         assertThat(row.containers()).isEmpty();
      });
      assertThat(map.blocks()).allSatisfy(block -> assertThat(block.has("rect") || block.has("polygon")).isTrue());
      // линейка Яндекса — 0,4387 м/px; матрица по опорным точкам должна дать то же
      assertThat(map.metersPerPx()).isBetween(0.40, 0.48);
      // центр рынка по OSM попадает внутрь снимка
      Point center = map.geo().toMap(42.886242, 74.520709);
      assertThat(center.x()).isBetween(0.0, 1084.0);
      assertThat(center.y()).isBetween(0.0, 771.0);
   }

   @Test
   void badAnchorIsRejected() throws Exception {
      ObjectNode data = (ObjectNode) geologiya().deepCopy();
      ObjectNode anchor = (ObjectNode) data.get("anchors").get(0);
      anchor.put("lat", anchor.get("lat").asDouble() + 0.001); // ~110 м мимо
      assertThatThrownBy(() -> MarketPlanService.build(1, data, mapper)).isInstanceOf(BadRequestException.class);
   }

   @Test
   void noBoundaryIsRejected() throws Exception {
      ObjectNode data = (ObjectNode) geologiya().deepCopy();
      data.set("boundary", mapper.createArrayNode());
      assertThatThrownBy(() -> MarketPlanService.build(1, data, mapper)).isInstanceOf(BadRequestException.class);
      ObjectNode noAnchors = (ObjectNode) geologiya().deepCopy();
      noAnchors.set("anchors", (ArrayNode) mapper.createArrayNode());
      assertThatThrownBy(() -> MarketPlanService.build(1, noAnchors, mapper)).isInstanceOf(BadRequestException.class);
   }
}
