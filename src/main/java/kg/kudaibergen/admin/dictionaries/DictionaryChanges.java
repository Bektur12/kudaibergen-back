package kg.kudaibergen.admin.dictionaries;

import kg.kudaibergen.category.CategoryService;
import kg.kudaibergen.common.web.DictionaryVersion;
import kg.kudaibergen.garage.VehicleDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Правка справочника «сразу видна в приложении»: версия справочников растёт в той же транзакции,
 * кэши марок, моделей и категорий перечитываются после коммита.
 */
@Component
public class DictionaryChanges {

   private final DictionaryVersion version;
   private final VehicleDirectory directory;
   private final CategoryService categories;

   public DictionaryChanges(DictionaryVersion version, VehicleDirectory directory, CategoryService categories) {
      this.version = version;
      this.directory = directory;
      this.categories = categories;
   }

   public void changed() {
      version.bump();
      Runnable reload = () -> {
         directory.reload();
         categories.reload();
      };
      if (TransactionSynchronizationManager.isSynchronizationActive()) {
         TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
               reload.run();
            }
         });
      } else {
         reload.run();
      }
   }
}
