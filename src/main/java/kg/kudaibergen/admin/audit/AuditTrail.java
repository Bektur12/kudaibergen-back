package kg.kudaibergen.admin.audit;

import org.springframework.lang.Nullable;

/**
 * Подробности текущего действия {@link Audited}: состояние до/после, id объекта, комментарий.
 * Вне действия вызовы ничего не делают.
 */
public final class AuditTrail {

   private static final ThreadLocal<Entry> CURRENT = new ThreadLocal<>();

   private AuditTrail() {
   }

   public static void before(@Nullable Object snapshot) {
      Entry entry = CURRENT.get();
      if (entry != null) {
         entry.before = snapshot;
      }
   }

   public static void after(@Nullable Object snapshot) {
      Entry entry = CURRENT.get();
      if (entry != null) {
         entry.after = snapshot;
         entry.afterSet = true;
      }
   }

   public static void entityId(@Nullable Long id) {
      Entry entry = CURRENT.get();
      if (entry != null) {
         entry.entityId = id;
      }
   }

   public static void comment(@Nullable String comment) {
      Entry entry = CURRENT.get();
      if (entry != null) {
         entry.comment = comment;
      }
   }

   static Entry open() {
      Entry entry = new Entry();
      CURRENT.set(entry);
      return entry;
   }

   static void close() {
      CURRENT.remove();
   }

   static final class Entry {
      Object before;
      Object after;
      boolean afterSet;
      Long entityId;
      String comment;
   }
}
