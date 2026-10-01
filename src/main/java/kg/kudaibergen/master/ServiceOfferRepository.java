package kg.kudaibergen.master;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import kg.kudaibergen.master.entity.OfferAnswer;
import kg.kudaibergen.master.entity.ServiceOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceOfferRepository extends JpaRepository<ServiceOffer, Long> {

   Optional<ServiceOffer> findByRequestIdAndMasterId(Long requestId, Long masterId);

   boolean existsByRequestIdAndMasterIdAndAnswer(Long requestId, Long masterId, OfferAnswer answer);

   /** Отклики «Могу помочь» в порядке прихода; afterId — только новые. */
   @Query("""
         select o from ServiceOffer o
         where o.requestId = :requestId and o.answer = :canHelp and o.hiddenByAdmin = false and o.id > :afterId
         order by o.id""")
   List<ServiceOffer> findCanHelp(@Param("requestId") Long requestId, @Param("canHelp") OfferAnswer canHelp,
                                  @Param("afterId") long afterId);

   List<ServiceOffer> findByMasterIdAndRequestIdIn(Long masterId, Collection<Long> requestIds);
}
