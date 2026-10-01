package kg.kudaibergen.admin.masters;

/**
 * Колонка «Проверка» [A7]: что видно о мастере. COMPLAINT — есть жалоба без решения (важнее всего),
 * NO_PHOTOS — фото места не загружены, PHOTOS — фото есть. «Документы» появятся вместе с загрузкой документов.
 */
public enum MasterCheck {
   COMPLAINT,
   NO_PHOTOS,
   PHOTOS;

   public static MasterCheck of(int photos, long openComplaints) {
      if (openComplaints > 0) {
         return COMPLAINT;
      }
      return photos > 0 ? PHOTOS : NO_PHOTOS;
   }
}
