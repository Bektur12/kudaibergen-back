package kg.kudaibergen.admin.dictionaries;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.AdminBrandDto;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.AdminCategoryDto;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.AdminHintDto;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.AdminModelDto;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.AdminServiceTypeDto;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.AdminSynonymDto;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.BrandCounts;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.CategoryCounts;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.CreateBrandRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.CreateCategoryRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.CreateHintRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.CreateModelRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.CreateServiceTypeRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.CreateSynonymRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.HintCounts;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.ModelCounts;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.ServiceTypeCounts;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.SynonymCounts;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.UpdateBrandRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.UpdateCategoryRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.UpdateHintRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.UpdateModelRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.UpdateServiceTypeRequest;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.master.entity.ServiceDuration;
import kg.kudaibergen.media.MediaPurpose;
import kg.kudaibergen.media.MediaService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Справочники [A5, A9]. Правки — SQL напрямую (справочники маленькие, кэшируются в приложении): после каждой
 * растёт версия справочников и перечитываются кэши (DictionaryChanges). Используемое удалить нельзя —
 * 409 IN_USE, его скрывают (active = false).
 */
@Service
public class AdminDictionaryService {

   private final NamedParameterJdbcTemplate jdbc;
   private final DictionaryChanges changes;
   private final MediaService media;

   public AdminDictionaryService(NamedParameterJdbcTemplate jdbc, DictionaryChanges changes, MediaService media) {
      this.jdbc = jdbc;
      this.changes = changes;
      this.media = media;
   }

   // ─────────────────────── марки ───────────────────────

   private static final String BRANDS = """
         select b.id, b.slug, b.name, b.short_name, b.logo_url, b.logo_media_id, b.placeholder, b.color, b.popular,
                b.sort_order, b.aliases, b.is_active,
                (select count(*) from models m where m.brand_id = b.id) as models,
                (select count(*) from shop_brands sb where sb.brand_id = b.id) as sellers,
                (select count(*) from master_brands mb where mb.brand_id = b.id) as masters,
                (select count(*) from cars c where c.brand_id = b.id) as cars
           from brands b
         """;

   @Transactional(readOnly = true)
   public AdminPage<AdminBrandDto, BrandCounts> brands(String q) {
      MapSqlParameterSource params = like(q);
      List<AdminBrandDto> items = jdbc.query(BRANDS + """
             where cast(:like as text) is null or lower(b.name) like :like or lower(b.slug) like :like
                or exists (select 1 from unnest(b.aliases) a where lower(a) like :like)
             order by b.popular desc, b.sort_order, lower(b.name)""", params, AdminDictionaryService::brand);
      BrandCounts counts = jdbc.queryForObject("""
            select count(*) as all_count, count(*) filter (where is_active) as active,
                   count(*) filter (where not is_active) as hidden, count(*) filter (where popular) as popular
            from brands""", Map.of(), (rs, n) -> new BrandCounts(rs.getLong("all_count"), rs.getLong("active"),
            rs.getLong("hidden"), rs.getLong("popular")));
      return new AdminPage<>(items, null, counts);
   }

   @Transactional(readOnly = true)
   public AdminBrandDto brand(Long id) {
      return jdbc.query(BRANDS + " where b.id = :id", Map.of("id", id), AdminDictionaryService::brand).stream()
            .findFirst().orElseThrow(() -> new NotFoundException("BRAND_NOT_FOUND", "Марка не найдена"));
   }

   @Transactional
   public AdminBrandDto createBrand(CreateBrandRequest request) {
      Long id = insert("""
            insert into brands (slug, name, short_name, placeholder, color, popular, sort_order, aliases)
            values (:slug, :name, :shortName, :placeholder, :color, :popular, :sortOrder, cast(:aliases as text[]))
            returning id""", new MapSqlParameterSource()
            .addValue("slug", request.slug().strip().toLowerCase(Locale.ROOT))
            .addValue("name", request.name().strip())
            .addValue("shortName", request.shortName().strip())
            .addValue("placeholder", request.placeholder().strip())
            .addValue("color", request.color())
            .addValue("popular", request.popular())
            .addValue("sortOrder", request.sortOrder() == null ? 100 : request.sortOrder())
            .addValue("aliases", aliases(request.aliases())), "BRAND_EXISTS", "Марка с таким slug уже есть");
      changes.changed();
      return brand(id);
   }

   @Transactional
   public AdminBrandDto updateBrand(Long id, UpdateBrandRequest request) {
      brand(id);
      Update update = new Update("brands", id)
            .set("name", strip(request.name()))
            .set("short_name", strip(request.shortName()))
            .set("placeholder", strip(request.placeholder()))
            .set("color", request.color())
            .set("popular", request.popular())
            .set("sort_order", request.sortOrder())
            .setArray("aliases", request.aliases() == null ? null : aliases(request.aliases()))
            .set("is_active", request.active());
      apply(update);
      return brand(id);
   }

   @Transactional
   public AdminBrandDto setLogo(Long id, Long mediaId, Long adminId) {
      brand(id);
      media.requireUsable(List.of(mediaId), List.of(adminId), Set.of(MediaPurpose.BRAND));
      jdbc.update("update brands set logo_media_id = :media where id = :id", Map.of("media", mediaId, "id", id));
      changes.changed();
      return brand(id);
   }

   @Transactional
   public AdminBrandDto removeLogo(Long id) {
      brand(id);
      jdbc.update("update brands set logo_media_id = null where id = :id", Map.of("id", id));
      changes.changed();
      return brand(id);
   }

   @Transactional
   public void deleteBrand(Long id) {
      AdminBrandDto brand = brand(id);
      if (brand.modelsCount() + brand.sellersCount() + brand.mastersCount() + brand.carsCount() > 0) {
         throw inUse("Марка используется: моделей " + brand.modelsCount() + ", продавцов " + brand.sellersCount()
               + ", мастеров " + brand.mastersCount() + ", машин " + brand.carsCount() + ". Её можно только скрыть");
      }
      delete("delete from brands where id = :id", id, "Марка используется в запросах или каталоге");
   }

   /** Порядок марок: id по порядку → sort_order 1, 2, 3… (популярные плитки — в этом порядке). */
   @Transactional
   public AdminPage<AdminBrandDto, BrandCounts> reorderBrands(List<Long> ids) {
      reorder("brands", "id", ids);
      return brands(null);
   }

   // ─────────────────────── модели ───────────────────────

   private static final String MODELS = """
         select m.id, m.brand_id, m.name, m.generation, m.display_name, m.year_from, m.year_to, m.sort_order,
                m.aliases, m.is_active,
                (select count(*) from cars c where c.model_id = m.id) as cars,
                (select count(*) from part_fitments f where f.model_id = m.id) as parts
           from models m
         """;

   @Transactional(readOnly = true)
   public AdminPage<AdminModelDto, ModelCounts> models(Long brandId, String q) {
      brand(brandId);
      MapSqlParameterSource params = like(q).addValue("brandId", brandId);
      List<AdminModelDto> items = jdbc.query(MODELS + """
             where m.brand_id = :brandId
               and (cast(:like as text) is null or lower(m.name) like :like or lower(coalesce(m.generation, '')) like :like
                    or lower(coalesce(m.display_name, '')) like :like)
             order by m.sort_order, m.name, m.generation nulls first""", params, AdminDictionaryService::model);
      ModelCounts counts = jdbc.queryForObject("""
            select count(*) as all_count, count(*) filter (where is_active) as active,
                   count(*) filter (where not is_active) as hidden
            from models where brand_id = :brandId""", params,
            (rs, n) -> new ModelCounts(rs.getLong("all_count"), rs.getLong("active"), rs.getLong("hidden")));
      return new AdminPage<>(items, null, counts);
   }

   @Transactional(readOnly = true)
   public AdminModelDto model(Long id) {
      return jdbc.query(MODELS + " where m.id = :id", Map.of("id", id), AdminDictionaryService::model).stream()
            .findFirst().orElseThrow(() -> new NotFoundException("MODEL_NOT_FOUND", "Модель не найдена"));
   }

   @Transactional
   public AdminModelDto createModel(Long brandId, CreateModelRequest request) {
      brand(brandId);
      years(request.yearFrom(), request.yearTo());
      Long id = insert("""
            insert into models (brand_id, name, generation, display_name, year_from, year_to, sort_order, aliases)
            values (:brandId, :name, :generation, :displayName, :yearFrom, :yearTo, :sortOrder, cast(:aliases as text[]))
            returning id""", new MapSqlParameterSource()
            .addValue("brandId", brandId)
            .addValue("name", request.name().strip())
            .addValue("generation", blankToNull(request.generation()), Types.VARCHAR)
            .addValue("displayName", blankToNull(request.displayName()), Types.VARCHAR)
            .addValue("yearFrom", request.yearFrom(), Types.SMALLINT)
            .addValue("yearTo", request.yearTo(), Types.SMALLINT)
            .addValue("sortOrder", request.sortOrder() == null ? 100 : request.sortOrder())
            .addValue("aliases", aliases(request.aliases())), "MODEL_EXISTS", "Такая модель с поколением уже есть");
      changes.changed();
      return model(id);
   }

   @Transactional
   public AdminModelDto updateModel(Long id, UpdateModelRequest request) {
      AdminModelDto current = model(id);
      years(request.yearFrom() != null ? request.yearFrom() : current.yearFrom(),
            request.yearTo() != null ? request.yearTo() : current.yearTo());
      Update update = new Update("models", id)
            .set("name", strip(request.name()))
            .setClearable("generation", request.generation())
            .setClearable("display_name", request.displayName())
            .set("year_from", request.yearFrom())
            .set("year_to", request.yearTo())
            .set("sort_order", request.sortOrder())
            .setArray("aliases", request.aliases() == null ? null : aliases(request.aliases()))
            .set("is_active", request.active());
      try {
         apply(update);
      } catch (DuplicateKeyException e) {
         throw new ConflictException("MODEL_EXISTS", "Такая модель с поколением уже есть");
      }
      return model(id);
   }

   @Transactional
   public void deleteModel(Long id) {
      AdminModelDto model = model(id);
      if (model.carsCount() + model.partsCount() > 0) {
         throw inUse("Модель используется: машин " + model.carsCount() + ", запчастей " + model.partsCount()
               + ". Её можно только скрыть");
      }
      delete("delete from models where id = :id", id, "Модель используется в запросах или заявках");
   }

   // ─────────────────────── категории ───────────────────────

   private static final String CATEGORIES = """
         select c.id, c.slug, c.name_ru, c.name_kg, c.sort_order, c.is_active,
                (select count(*) from parts p where p.category_id = c.id) as parts,
                (select count(*) from shop_categories sc where sc.category_id = c.id) as shops
           from categories c
         """;

   @Transactional(readOnly = true)
   public AdminPage<AdminCategoryDto, CategoryCounts> categories() {
      List<AdminCategoryDto> items = jdbc.query(CATEGORIES + " order by c.sort_order, c.id", Map.of(),
            AdminDictionaryService::category);
      return new AdminPage<>(items, null, new CategoryCounts(items.size(),
            items.stream().filter(AdminCategoryDto::active).count(),
            items.stream().filter(category -> !category.active()).count()));
   }

   @Transactional(readOnly = true)
   public AdminCategoryDto category(Long id) {
      return jdbc.query(CATEGORIES + " where c.id = :id", Map.of("id", id), AdminDictionaryService::category).stream()
            .findFirst().orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Категория не найдена"));
   }

   @Transactional
   public AdminCategoryDto createCategory(CreateCategoryRequest request) {
      Long id = insert("""
            insert into categories (slug, name_ru, name_kg, sort_order)
            values (:slug, :nameRu, :nameKg, (select coalesce(max(sort_order), 0) + 1 from categories))
            returning id""", new MapSqlParameterSource()
            .addValue("slug", request.slug().strip().toLowerCase(Locale.ROOT))
            .addValue("nameRu", request.nameRu().strip())
            .addValue("nameKg", request.nameKg().strip()), "CATEGORY_EXISTS", "Категория с таким slug уже есть");
      changes.changed();
      return category(id);
   }

   @Transactional
   public AdminCategoryDto updateCategory(Long id, UpdateCategoryRequest request) {
      category(id);
      apply(new Update("categories", id)
            .set("name_ru", strip(request.nameRu()))
            .set("name_kg", strip(request.nameKg()))
            .set("is_active", request.active()));
      return category(id);
   }

   @Transactional
   public void deleteCategory(Long id) {
      AdminCategoryDto category = category(id);
      if (category.partsCount() + category.shopsCount() > 0) {
         throw inUse("Категория используется: запчастей " + category.partsCount() + ", магазинов "
               + category.shopsCount() + ". Её можно только скрыть");
      }
      delete("delete from categories where id = :id", id, "Категория используется в запросах или подсказках");
   }

   @Transactional
   public AdminPage<AdminCategoryDto, CategoryCounts> reorderCategories(List<Long> ids) {
      reorder("categories", "id", ids);
      return categories();
   }

   // ─────────────────────── услуги ───────────────────────

   private static final String SERVICE_TYPES = """
         select t.code, t.name_ru, t.name_kg, t.icon, t.sort_order, t.needs_location, t.urgent, t.duration_min,
                t.default_radius_km, t.is_active,
                (select count(*) from master_services ms where ms.service = t.code) as masters,
                (select count(*) from service_requests r where r.service = t.code
                   and r.created_at > now() - interval '30 days') as requests
           from service_types t
         """;

   @Transactional(readOnly = true)
   public AdminPage<AdminServiceTypeDto, ServiceTypeCounts> serviceTypes() {
      List<AdminServiceTypeDto> items = jdbc.query(SERVICE_TYPES + " order by t.sort_order, t.code", Map.of(),
            AdminDictionaryService::serviceType);
      return new AdminPage<>(items, null, new ServiceTypeCounts(items.size(),
            items.stream().filter(AdminServiceTypeDto::active).count(),
            items.stream().filter(type -> !type.active()).count()));
   }

   @Transactional(readOnly = true)
   public AdminServiceTypeDto serviceType(String code) {
      return jdbc.query(SERVICE_TYPES + " where t.code = :code", Map.of("code", code),
                  AdminDictionaryService::serviceType).stream().findFirst()
            .orElseThrow(() -> new NotFoundException("SERVICE_NOT_FOUND", "Услуга не найдена"));
   }

   @Transactional
   public AdminServiceTypeDto createServiceType(CreateServiceTypeRequest request) {
      insert("""
            insert into service_types (code, name_ru, name_kg, icon, sort_order, needs_location, urgent, duration_min,
                                       default_radius_km)
            values (:code, :nameRu, :nameKg, :icon, (select coalesce(max(sort_order), 0) + 1 from service_types),
                    :needsLocation, :urgent, :duration, :radius)
            returning 0""", new MapSqlParameterSource()
            .addValue("code", request.code())
            .addValue("nameRu", request.nameRu().strip())
            .addValue("nameKg", request.nameKg().strip())
            .addValue("icon", request.icon().strip())
            .addValue("needsLocation", request.needsLocation())
            .addValue("urgent", request.urgent())
            .addValue("duration", minutes(request.defaultDuration() == null ? ServiceDuration.MIN_30
                  : request.defaultDuration()))
            .addValue("radius", request.defaultRadiusKm() == null ? 5 : request.defaultRadiusKm()),
            "SERVICE_EXISTS", "Услуга с таким кодом уже есть");
      changes.changed();
      return serviceType(request.code());
   }

   @Transactional
   public AdminServiceTypeDto updateServiceType(String code, UpdateServiceTypeRequest request) {
      serviceType(code);
      List<String> sets = new ArrayList<>();
      MapSqlParameterSource params = new MapSqlParameterSource("code", code);
      addSet(sets, params, "name_ru", strip(request.nameRu()));
      addSet(sets, params, "name_kg", strip(request.nameKg()));
      addSet(sets, params, "icon", strip(request.icon()));
      addSet(sets, params, "needs_location", request.needsLocation());
      addSet(sets, params, "urgent", request.urgent());
      addSet(sets, params, "duration_min", request.defaultDuration() == null ? null : minutes(request.defaultDuration()));
      addSet(sets, params, "default_radius_km", request.defaultRadiusKm());
      addSet(sets, params, "is_active", request.active());
      if (!sets.isEmpty()) {
         jdbc.update("update service_types set " + String.join(", ", sets) + " where code = :code", params);
         changes.changed();
      }
      return serviceType(code);
   }

   @Transactional
   public void deleteServiceType(String code) {
      AdminServiceTypeDto type = serviceType(code);
      if (type.mastersCount() > 0) {
         throw inUse("Услугу выбрали мастера: " + type.mastersCount() + ". Её можно только скрыть");
      }
      try {
         jdbc.update("delete from service_types where code = :code", Map.of("code", code));
      } catch (DataIntegrityViolationException e) {
         throw inUse("По услуге есть заявки. Её можно только скрыть");
      }
      changes.changed();
   }

   /** Порядок плиток перетаскиванием: коды по порядку. */
   @Transactional
   public AdminPage<AdminServiceTypeDto, ServiceTypeCounts> reorderServiceTypes(List<String> codes) {
      List<String> known = jdbc.queryForList("select code from service_types", Map.of(), String.class);
      if (!Set.copyOf(codes).equals(Set.copyOf(known)) || codes.size() != known.size()) {
         throw new BadRequestException("BAD_ORDER", "Передайте все коды услуг ровно по одному разу");
      }
      for (int i = 0; i < codes.size(); i++) {
         jdbc.update("update service_types set sort_order = :sort where code = :code",
               Map.of("sort", i + 1, "code", codes.get(i)));
      }
      changes.changed();
      return serviceTypes();
   }

   // ─────────────────────── синонимы ───────────────────────

   @Transactional(readOnly = true)
   public AdminPage<AdminSynonymDto, SynonymCounts> synonyms(String q) {
      List<AdminSynonymDto> items = jdbc.query("""
            select id, term, synonym from search_synonyms
            where cast(:like as text) is null or term like :like or synonym like :like
            order by term, synonym""", like(q), (rs, n) -> new AdminSynonymDto(rs.getLong("id"),
            rs.getString("term"), rs.getString("synonym")));
      Long all = jdbc.queryForObject("select count(*) from search_synonyms", Map.of(), Long.class);
      return new AdminPage<>(items, null, new SynonymCounts(all == null ? 0 : all));
   }

   /** Пара (и обратная, если bidirectional): слова в нижнем регистре; уже существующая пара не дублируется. */
   @Transactional
   public List<AdminSynonymDto> createSynonym(CreateSynonymRequest request) {
      String term = request.term().strip().toLowerCase(Locale.ROOT);
      String synonym = request.synonym().strip().toLowerCase(Locale.ROOT);
      if (term.equals(synonym)) {
         throw new BadRequestException("SAME_WORDS", "Слово и синоним совпадают");
      }
      List<String[]> pairs = new ArrayList<>();
      pairs.add(new String[]{term, synonym});
      if (!Boolean.FALSE.equals(request.bidirectional())) {
         pairs.add(new String[]{synonym, term});
      }
      for (String[] pair : pairs) {
         jdbc.update("insert into search_synonyms (term, synonym) values (:t, :s) on conflict do nothing",
               Map.of("t", pair[0], "s", pair[1]));
      }
      changes.changed();
      return jdbc.query("""
            select id, term, synonym from search_synonyms
            where (term = :a and synonym = :b) or (term = :b and synonym = :a) order by id""",
            Map.of("a", term, "b", synonym), (rs, n) -> new AdminSynonymDto(rs.getLong("id"), rs.getString("term"),
                  rs.getString("synonym")));
   }

   @Transactional
   public AdminSynonymDto deleteSynonym(Long id) {
      AdminSynonymDto found = jdbc.query("select id, term, synonym from search_synonyms where id = :id",
                  Map.of("id", id), (rs, n) -> new AdminSynonymDto(rs.getLong("id"), rs.getString("term"),
                        rs.getString("synonym"))).stream().findFirst()
            .orElseThrow(() -> new NotFoundException("SYNONYM_NOT_FOUND", "Синоним не найден"));
      jdbc.update("delete from search_synonyms where id = :id", Map.of("id", id));
      changes.changed();
      return found;
   }

   // ─────────────────────── подсказки ───────────────────────

   private static final String HINTS = """
         select h.id, h.text_ru, h.text_kg, h.category_id, h.popularity, h.is_active,
                (select count(*) from part_requests r where r.hint_id = h.id) as requests
           from part_hints h
         """;

   @Transactional(readOnly = true)
   public AdminPage<AdminHintDto, HintCounts> hints() {
      List<AdminHintDto> items = jdbc.query(HINTS + " order by h.popularity desc, h.id", Map.of(),
            AdminDictionaryService::hint);
      return new AdminPage<>(items, null, new HintCounts(items.size(),
            items.stream().filter(AdminHintDto::active).count(), items.stream().filter(h -> !h.active()).count()));
   }

   @Transactional(readOnly = true)
   public AdminHintDto hint(Long id) {
      return jdbc.query(HINTS + " where h.id = :id", Map.of("id", id), AdminDictionaryService::hint).stream()
            .findFirst().orElseThrow(() -> new NotFoundException("HINT_NOT_FOUND", "Подсказка не найдена"));
   }

   @Transactional
   public AdminHintDto createHint(CreateHintRequest request) {
      if (request.categoryId() != null) {
         category(request.categoryId());
      }
      Long id = insert("""
            insert into part_hints (text_ru, text_kg, category_id, popularity)
            values (:ru, :kg, :category, :popularity) returning id""", new MapSqlParameterSource()
            .addValue("ru", request.textRu().strip())
            .addValue("kg", request.textKg().strip())
            .addValue("category", request.categoryId(), Types.BIGINT)
            .addValue("popularity", request.popularity() == null ? 0 : request.popularity()), "HINT_EXISTS",
            "Такая подсказка уже есть");
      changes.changed();
      return hint(id);
   }

   @Transactional
   public AdminHintDto updateHint(Long id, UpdateHintRequest request) {
      hint(id);
      if (request.categoryId() != null && request.categoryId() != 0) {
         category(request.categoryId());
      }
      List<String> sets = new ArrayList<>();
      MapSqlParameterSource params = new MapSqlParameterSource("id", id);
      addSet(sets, params, "text_ru", strip(request.textRu()));
      addSet(sets, params, "text_kg", strip(request.textKg()));
      addSet(sets, params, "popularity", request.popularity());
      addSet(sets, params, "is_active", request.active());
      if (request.categoryId() != null) {
         sets.add("category_id = :category_id");
         params.addValue("category_id", request.categoryId() == 0 ? null : request.categoryId(), Types.BIGINT);
      }
      if (!sets.isEmpty()) {
         jdbc.update("update part_hints set " + String.join(", ", sets) + " where id = :id", params);
         changes.changed();
      }
      return hint(id);
   }

   /** Подсказка, по которой уже были запросы, не удаляется — только скрывается. */
   @Transactional
   public void deleteHint(Long id) {
      AdminHintDto hint = hint(id);
      if (hint.requestsCount() > 0) {
         throw inUse("По подсказке было запросов: " + hint.requestsCount() + ". Её можно только скрыть");
      }
      delete("delete from part_hints where id = :id", id, "Подсказка используется");
   }

   // ─────────────────────── общее ───────────────────────

   /** UPDATE только переданных полей (null — не менять). */
   private final class Update {
      private final String table;
      private final Long id;
      private final List<String> sets = new ArrayList<>();
      private final MapSqlParameterSource params;

      Update(String table, Long id) {
         this.table = table;
         this.id = id;
         this.params = new MapSqlParameterSource("id", id);
      }

      Update set(String column, Object value) {
         addSet(sets, params, column, value);
         return this;
      }

      /** Пустая строка — стереть значение. */
      Update setClearable(String column, String value) {
         if (value != null) {
            sets.add(column + " = :" + column);
            params.addValue(column, value.isBlank() ? null : value.strip(), Types.VARCHAR);
         }
         return this;
      }

      Update setArray(String column, String[] value) {
         if (value != null) {
            sets.add(column + " = cast(:" + column + " as text[])");
            params.addValue(column, value);
         }
         return this;
      }
   }

   private void apply(Update update) {
      if (update.sets.isEmpty()) {
         return;
      }
      jdbc.update("update " + update.table + " set " + String.join(", ", update.sets) + " where id = :id",
            update.params);
      changes.changed();
   }

   private static void addSet(List<String> sets, MapSqlParameterSource params, String column, Object value) {
      if (value != null) {
         sets.add(column + " = :" + column);
         params.addValue(column, value);
      }
   }

   private Long insert(String sql, MapSqlParameterSource params, String duplicateCode, String duplicateMessage) {
      try {
         return jdbc.queryForObject(sql, params, Long.class);
      } catch (DuplicateKeyException e) {
         throw new ConflictException(duplicateCode, duplicateMessage);
      }
   }

   private void delete(String sql, Long id, String inUseMessage) {
      try {
         jdbc.update(sql, Map.of("id", id));
      } catch (DataIntegrityViolationException e) {
         throw inUse(inUseMessage + ". Можно только скрыть");
      }
      changes.changed();
   }

   private void reorder(String table, String key, List<Long> ids) {
      List<Long> known = jdbc.queryForList("select " + key + " from " + table, Map.of(), Long.class);
      if (!Set.copyOf(known).containsAll(ids) || Set.copyOf(ids).size() != ids.size()) {
         throw new BadRequestException("BAD_ORDER", "В порядке есть неизвестные или повторяющиеся id");
      }
      for (int i = 0; i < ids.size(); i++) {
         jdbc.update("update " + table + " set sort_order = :sort where " + key + " = :id",
               Map.of("sort", i + 1, "id", ids.get(i)));
      }
      changes.changed();
   }

   private static ConflictException inUse(String message) {
      return new ConflictException("IN_USE", message);
   }

   private static void years(Integer from, Integer to) {
      if (from != null && to != null && to < from) {
         throw new BadRequestException("BAD_YEARS", "Год окончания раньше года начала");
      }
   }

   private static int minutes(ServiceDuration duration) {
      return switch (duration) {
         case MIN_15 -> 15;
         case MIN_30 -> 30;
         case HOUR_1 -> 60;
         case HOUR_3 -> 180;
         case END_OF_DAY -> throw new BadRequestException("BAD_DURATION", "Для услуги — от 15 минут до 3 часов");
      };
   }

   static String[] aliases(List<String> values) {
      if (values == null) {
         return new String[0];
      }
      return values.stream().map(value -> value.strip().toLowerCase(Locale.ROOT)).filter(value -> !value.isEmpty())
            .distinct().toArray(String[]::new);
   }

   private static String strip(String value) {
      return value == null ? null : value.strip();
   }

   private static String blankToNull(String value) {
      return value == null || value.isBlank() ? null : value.strip();
   }

   private static MapSqlParameterSource like(String q) {
      String value = q == null || q.isBlank() ? null
            : "%" + q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
      return new MapSqlParameterSource().addValue("like", value, Types.VARCHAR);
   }

   private static List<String> array(ResultSet rs, String column) throws SQLException {
      Array array = rs.getArray(column);
      return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
   }

   private static Integer integer(ResultSet rs, String column) throws SQLException {
      int value = rs.getInt(column);
      return rs.wasNull() ? null : value;
   }

   private static AdminBrandDto brand(ResultSet rs, int n) throws SQLException {
      long id = rs.getLong("id");
      boolean uploaded = rs.getObject("logo_media_id") != null;
      return new AdminBrandDto(id, rs.getString("slug"), rs.getString("name"), rs.getString("short_name"),
            uploaded ? "/api/v1/brands/" + id + "/logo" : rs.getString("logo_url"), rs.getString("placeholder"),
            rs.getString("color"), rs.getBoolean("popular"), rs.getInt("sort_order"), array(rs, "aliases"),
            rs.getBoolean("is_active"), rs.getLong("models"), rs.getLong("sellers"), rs.getLong("masters"),
            rs.getLong("cars"));
   }

   private static AdminModelDto model(ResultSet rs, int n) throws SQLException {
      String name = rs.getString("name");
      String generation = rs.getString("generation");
      String displayName = rs.getString("display_name");
      String label = displayName != null && !displayName.isBlank() ? displayName
            : generation == null ? name : name + " " + generation;
      return new AdminModelDto(rs.getLong("id"), rs.getLong("brand_id"), name, generation, displayName, label,
            integer(rs, "year_from"), integer(rs, "year_to"), rs.getInt("sort_order"), array(rs, "aliases"),
            rs.getBoolean("is_active"), rs.getLong("cars"), rs.getLong("parts"));
   }

   private static AdminCategoryDto category(ResultSet rs, int n) throws SQLException {
      return new AdminCategoryDto(rs.getLong("id"), rs.getString("slug"), rs.getString("name_ru"),
            rs.getString("name_kg"), rs.getInt("sort_order"), rs.getBoolean("is_active"), rs.getLong("parts"),
            rs.getLong("shops"));
   }

   private static AdminServiceTypeDto serviceType(ResultSet rs, int n) throws SQLException {
      return new AdminServiceTypeDto(rs.getString("code"), rs.getString("name_ru"), rs.getString("name_kg"),
            rs.getString("icon"), rs.getInt("sort_order"), rs.getBoolean("needs_location"), rs.getBoolean("urgent"),
            ServiceDuration.ofMinutes(rs.getInt("duration_min")), rs.getInt("default_radius_km"),
            rs.getBoolean("is_active"), rs.getLong("masters"), rs.getLong("requests"));
   }

   private static AdminHintDto hint(ResultSet rs, int n) throws SQLException {
      Long category = (Long) rs.getObject("category_id");
      return new AdminHintDto(rs.getLong("id"), rs.getString("text_ru"), rs.getString("text_kg"), category,
            rs.getInt("popularity"), rs.getBoolean("is_active"), rs.getLong("requests"));
   }
}
