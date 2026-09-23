package kg.kudaibergen.store;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.store.dto.BranchResponse;
import kg.kudaibergen.store.dto.StoreDetailsResponse;
import kg.kudaibergen.store.dto.StoreSummaryResponse;
import kg.kudaibergen.store.dto.WorkHours;
import kg.kudaibergen.store.entity.Store;
import kg.kudaibergen.store.entity.StoreBranch;
import org.springframework.stereotype.Component;

@Component
public class StoreMapper {

   private final ObjectMapper objectMapper;

   public StoreMapper(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
   }

   public StoreSummaryResponse toSummary(Store store, List<StoreBranch> branches) {
      return new StoreSummaryResponse(store.getId(), store.getName(), store.getBusinessType(),
            store.getRating(), store.getReviewCount(), store.getTotalDeals(), store.getVerificationStatus(),
            // копия: ленивая коллекция не должна уезжать в сериализацию за пределы транзакции
            Set.copyOf(store.getCategories()),
            branches.stream().map(StoreBranch::getCity).distinct().toList());
   }

   public StoreDetailsResponse toDetails(Store store, List<StoreBranch> branches, boolean showPhones) {
      return new StoreDetailsResponse(store.getId(), store.getName(), store.getBusinessType(),
            store.getDescription(), store.getRating(), store.getReviewCount(), store.getTotalDeals(),
            store.getVerificationStatus(), Set.copyOf(store.getCategories()),
            branches.stream().map(branch -> toBranch(branch, showPhones)).toList());
   }

   /** Телефон филиала виден владельцу и покупателю с принятым предложением — остальным нет. */
   public BranchResponse toBranch(StoreBranch branch, boolean showPhone) {
      return new BranchResponse(branch.getId(), branch.getAddress(), branch.getCity(),
            showPhone ? branch.getPhone() : null, branch.getLatitude(), branch.getLongitude(),
            readWorkHours(branch.getWorkHours()));
   }

   public String writeWorkHours(WorkHours workHours) {
      if (workHours == null) {
         return null;
      }
      try {
         return objectMapper.writeValueAsString(workHours);
      } catch (JsonProcessingException ex) {
         throw new BadRequestException("INVALID_WORK_HOURS", "Некорректный график работы", "workHours");
      }
   }

   public WorkHours readWorkHours(String json) {
      if (json == null || json.isBlank()) {
         return null;
      }
      try {
         return objectMapper.readValue(json, WorkHours.class);
      } catch (JsonProcessingException ex) {
         return null;
      }
   }
}
