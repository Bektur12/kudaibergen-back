package kg.kudaibergen.master.dto;

import java.util.List;

import org.springframework.lang.Nullable;

/**
 * «Кто рядом» — сразу, без заявки: мастер, «12 км от вас», какие услуги делает. reachesYou — вы в его радиусе
 * (сам сказал, что доедет); worksWithCar — берётся за вашу машину (null — машину не передали).
 */
public record NearbyMasterDto(MasterCardDto master, int distanceM, List<String> services, boolean reachesYou,
                              @Nullable Boolean worksWithCar) {
}
