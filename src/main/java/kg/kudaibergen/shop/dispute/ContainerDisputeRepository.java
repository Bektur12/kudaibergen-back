package kg.kudaibergen.shop.dispute;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContainerDisputeRepository extends JpaRepository<ContainerDispute, Long> {

   Optional<ContainerDispute> findFirstByContainerIdAndClaimantUserIdAndStatus(Long containerId, Long claimantUserId,
                                                                              DisputeStatus status);
}
