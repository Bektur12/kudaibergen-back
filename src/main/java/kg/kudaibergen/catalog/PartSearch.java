package kg.kudaibergen.catalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import kg.kudaibergen.catalog.dto.CarFilter;
import kg.kudaibergen.catalog.dto.PartSort;
import kg.kudaibergen.request.entity.PartCondition;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Поиск запчастей (ТЗ 5.1). SQL собирается из заданных фильтров — пустые условия не попадают в запрос.
 * <ul>
 *   <li>Текст: полнотекстовый поиск (русская морфология + как есть — для кыргызского и латиницы)
 *   с синонимами («стойка» = «амортизатор»), опечатки — через сходство триграмм, номер детали — по началу
 *   нормализованного OEM.</li>
 *   <li>Машина: хотя бы одна «подходящая» строка part_fitments (марка, модель или «все модели», годы).
 *   Точное совпадение модели — первым в выдаче.</li>
 *   <li>«Ближе ко мне»: порядок магазинов по расстоянию считает вызывающий, сюда приходит готовый список.</li>
 * </ul>
 * Страницы — по смещению: сортировок несколько, а выдача короткая.
 */
@Component
public class PartSearch {

   private static final int MAX_TOKENS = 8;
   private static final int MAX_TOKEN_LENGTH = 30;
   /** Порог сходства слов для опечаток: «амартизатор» ≈ «амортизатор». */
   private static final double TYPO_SIMILARITY = 0.45;
   private static final int MIN_OEM_LENGTH = 4;

   /** Бокс открыт сейчас: тумблер, рабочий день и часы по Бишкеку (та же логика, что ShopHours). */
   static final String OPEN_NOW_SQL = """
         s.is_open
         and (now() at time zone 'Asia/Bishkek')::time >= s.open_from
         and (now() at time zone 'Asia/Bishkek')::time < s.open_to
         and (s.work_days & (1 << (extract(isodow from now() at time zone 'Asia/Bishkek')::int - 1))) > 0""";

   private final NamedParameterJdbcTemplate jdbc;
   private final SynonymRepository synonyms;

   public PartSearch(NamedParameterJdbcTemplate jdbc, SynonymRepository synonyms) {
      this.jdbc = jdbc;
      this.synonyms = synonyms;
   }

   /**
    * Фильтры выдачи. shopOrder — id магазинов от ближнего к дальнему (только для NEAREST).
    * inStock по умолчанию true, openOnly — «только открытые боксы».
    */
   public record Query(String text, CarFilter car, Long categoryId, PartCondition condition, Integer priceMin,
                       Integer priceMax, boolean inStock, boolean openOnly, Long shopId, PartSort sort,
                       List<Long> shopOrder) {
   }

   /** Найденная запчасть: exact — подходит именно к модели машины. */
   public record Hit(Long partId, boolean exact) {
   }

   public List<Hit> find(Query query, int offset, int limit) {
      Sql sql = build(query);
      if (sql == null) {
         return List.of();
      }
      String exact = query.car() != null && query.car().modelId() != null ? fitmentExists(query.car(), true) : "false";
      StringBuilder select = new StringBuilder("select p.id, ").append(exact).append(" as exact from parts p ")
            .append("join shops s on s.id = p.shop_id ");
      if (query.sort() == PartSort.NEAREST && !query.shopOrder().isEmpty()) {
         select.append("left join (values ").append(nearestValues(query.shopOrder()))
               .append(") d(shop_id, rank) on d.shop_id = p.shop_id ");
      }
      select.append("where ").append(sql.where).append(" order by ");
      if (!"false".equals(exact)) {
         select.append("exact desc, ");
      }
      select.append(orderBy(query)).append(" limit :limit offset :offset");
      sql.params.addValue("limit", limit).addValue("offset", offset);
      return jdbc.query(select.toString(), sql.params, (rs, n) -> new Hit(rs.getLong(1), rs.getBoolean(2)));
   }

   public long count(Query query) {
      Sql sql = build(query);
      if (sql == null) {
         return 0;
      }
      Long total = jdbc.queryForObject("select count(*) from parts p join shops s on s.id = p.shop_id where "
            + sql.where, sql.params, Long.class);
      return total == null ? 0 : total;
   }

   /** null — запрос заведомо пустой (текст из одних знаков препинания). */
   private Sql build(Query query) {
      MapSqlParameterSource params = new MapSqlParameterSource();
      List<String> where = new ArrayList<>();
      where.add("p.status = 'ACTIVE'");
      where.add("s.status = 'ACTIVE'");
      if (query.shopId() != null) {
         where.add("p.shop_id = :shopId");
         params.addValue("shopId", query.shopId());
      }
      if (query.categoryId() != null) {
         where.add("p.category_id = :categoryId");
         params.addValue("categoryId", query.categoryId());
      }
      if (query.condition() != null) {
         where.add("p.condition = :condition");
         params.addValue("condition", query.condition().name());
      }
      if (query.priceMin() != null) {
         where.add("p.price >= :priceMin");
         params.addValue("priceMin", query.priceMin());
      }
      if (query.priceMax() != null) {
         where.add("p.price <= :priceMax");
         params.addValue("priceMax", query.priceMax());
      }
      if (query.inStock()) {
         where.add("p.quantity > 0");
      }
      if (query.openOnly()) {
         where.add("(" + OPEN_NOW_SQL + ")");
      }
      if (query.car() != null) {
         where.add(fitmentExists(query.car(), false));
         params.addValue("carBrandId", query.car().brandId());
         if (query.car().modelId() != null) {
            params.addValue("carModelId", query.car().modelId());
         }
         if (query.car().year() != null) {
            params.addValue("carYear", query.car().year());
         }
      }
      if (query.text() != null && !query.text().isBlank()) {
         String condition = textCondition(query.text(), params);
         if (condition == null) {
            return null;
         }
         where.add(condition);
      }
      return new Sql(String.join(" and ", where), params);
   }

   /**
    * Подходит ли запчасть машине: exact = false — модель указана и совпала или «все модели»;
    * exact = true — только совпадение модели.
    */
   private static String fitmentExists(CarFilter car, boolean exact) {
      StringBuilder sql = new StringBuilder("exists (select 1 from part_fitments f where f.part_id = p.id ")
            .append("and f.brand_id = :carBrandId");
      if (car.modelId() != null) {
         sql.append(exact ? " and f.model_id = :carModelId" : " and (f.model_id is null or f.model_id = :carModelId)");
      }
      if (car.year() != null) {
         sql.append(" and (f.year_from is null or f.year_from <= :carYear)")
               .append(" and (f.year_to is null or f.year_to >= :carYear)");
      }
      return sql.append(")").toString();
   }

   private String textCondition(String text, MapSqlParameterSource params) {
      List<String> tokens = tokens(text);
      if (tokens.isEmpty()) {
         return null;
      }
      Map<String, Set<String>> alternatives = new LinkedHashMap<>();
      tokens.forEach(token -> alternatives.put(token, new LinkedHashSet<>(List.of(token))));
      for (String[] pair : synonyms.synonymsOf(tokens)) {
         alternatives.computeIfAbsent(pair[0], key -> new LinkedHashSet<>()).addAll(tokens(pair[1]));
      }
      params.addValue("tsQuery", tsQuery(alternatives));
      params.addValue("typoText", String.join(" ", tokens));
      params.addValue("typoSimilarity", TYPO_SIMILARITY);
      List<String> any = new ArrayList<>();
      any.add("p.search_tsv @@ (to_tsquery('russian', :tsQuery) || to_tsquery('simple', :tsQuery))");
      any.add("word_similarity(:typoText, lower(p.title)) >= :typoSimilarity");
      String oem = oemPrefix(text);
      if (oem != null) {
         any.add("p.oem_norm like :oemPrefix");
         params.addValue("oemPrefix", oem + "%");
      }
      return "(" + String.join(" or ", any) + ")";
   }

   /** «стойки перед» → (стойки:* | амортизатор:*) & (перед:*). Слова уже очищены до букв и цифр. */
   static String tsQuery(Map<String, Set<String>> alternatives) {
      return alternatives.values().stream()
            .map(words -> words.stream().map(word -> word + ":*").collect(Collectors.joining(" | ", "(", ")")))
            .collect(Collectors.joining(" & "));
   }

   /** Нижний регистр, ё → е, всё кроме букв и цифр — разделитель; не больше 8 слов по 30 символов. */
   static List<String> tokens(String text) {
      String normalized = text.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
      if (normalized.isEmpty()) {
         return List.of();
      }
      return Arrays.stream(normalized.split(" "))
            .map(token -> token.length() > MAX_TOKEN_LENGTH ? token.substring(0, MAX_TOKEN_LENGTH) : token)
            .distinct()
            .limit(MAX_TOKENS)
            .toList();
   }

   /** Номер детали: «90919-01253» → 9091901253; слишком короткий или без цифр — не номер. */
   static String oemPrefix(String text) {
      String norm = text.replaceAll("[^\\p{L}\\p{N}]", "").toUpperCase(Locale.ROOT);
      return norm.length() >= MIN_OEM_LENGTH && norm.chars().anyMatch(Character::isDigit) ? norm : null;
   }

   private static String orderBy(Query query) {
      return switch (query.sort()) {
         case PRICE_ASC -> "p.price asc, p.id asc";
         case PRICE_DESC -> "p.price desc, p.id desc";
         case NEWEST -> "p.published_at desc nulls last, p.id desc";
         case NEAREST -> query.shopOrder().isEmpty() ? "p.price asc, p.id asc"
               : "d.rank asc nulls last, p.price asc, p.id asc";
      };
   }

   /** (id, ранг) магазинов — только числа, поэтому в SQL прямо, без параметров. */
   private static String nearestValues(List<Long> shopOrder) {
      StringBuilder values = new StringBuilder();
      for (int i = 0; i < shopOrder.size(); i++) {
         if (i > 0) {
            values.append(", ");
         }
         values.append('(').append(shopOrder.get(i).longValue()).append("::bigint, ").append(i).append(')');
      }
      return values.toString();
   }

   private record Sql(String where, MapSqlParameterSource params) {
   }
}
