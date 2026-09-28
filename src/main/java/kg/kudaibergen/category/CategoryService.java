package kg.kudaibergen.category;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import kg.kudaibergen.common.error.BadRequestException;
import org.springframework.stereotype.Service;

/** Справочник категорий в памяти: десяток строк, меняется только суперадмином. */
@Service
public class CategoryService {

   private final CategoryRepository categories;
   private final AtomicReference<List<Category>> cache = new AtomicReference<>();

   public CategoryService(CategoryRepository categories) {
      this.categories = categories;
   }

   public List<Category> all() {
      List<Category> loaded = cache.get();
      if (loaded == null) {
         loaded = categories.findAllByOrderBySortOrderAsc();
         cache.set(loaded);
      }
      return loaded;
   }

   public Map<Long, Category> byId() {
      return all().stream().collect(Collectors.toMap(Category::getId, Function.identity()));
   }

   /** Проверяет, что все id существуют; бросает 400 UNKNOWN_CATEGORY. */
   public Set<Long> requireExisting(Collection<Long> ids) {
      Set<Long> known = byId().keySet();
      Set<Long> requested = new HashSet<>(ids);
      requested.removeAll(known);
      if (!requested.isEmpty()) {
         throw new BadRequestException("UNKNOWN_CATEGORY", "Нет таких категорий: " + requested);
      }
      return new HashSet<>(ids);
   }

   public void reload() {
      cache.set(null);
   }
}
