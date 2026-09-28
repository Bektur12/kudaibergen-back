package kg.kudaibergen.garage;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.garage.dto.CarDto;
import kg.kudaibergen.garage.dto.CreateCarRequest;
import kg.kudaibergen.garage.dto.UpdateCarRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/cars")
@Tag(name = "Гараж")
public class GarageController {

   private final GarageService garage;

   public GarageController(GarageService garage) {
      this.garage = garage;
   }

   @GetMapping
   @Operation(summary = "Мой гараж (экран 04)", description = "Основная машина первой; чипы на экранах 06 и 27")
   public List<CarDto> list(@AuthenticationPrincipal AuthPrincipal principal) {
      return garage.list(principal.userId()).stream().map(CarDto::of).toList();
   }

   @PostMapping
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "Добавить машину", description = "Первая машина становится основной. Не больше 10 машин")
   @ApiResponse(responseCode = "400", description = "YEAR_OUT_OF_RANGE (yearFrom, yearTo), VALIDATION_ERROR")
   @ApiResponse(responseCode = "409", description = "GARAGE_LIMIT")
   public CarDto add(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody CreateCarRequest request) {
      return CarDto.of(garage.add(principal.userId(), request));
   }

   @PatchMapping("/{id}")
   @Operation(summary = "Изменить или переименовать машину")
   public CarDto update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                        @Valid @RequestBody UpdateCarRequest request) {
      return CarDto.of(garage.update(id, principal.userId(), request));
   }

   @PostMapping("/{id}/primary")
   @Operation(summary = "Сделать основной")
   public CarDto makePrimary(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      return CarDto.of(garage.makePrimary(id, principal.userId()));
   }

   @DeleteMapping("/{id}")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Удалить машину", description = "Если удалена основная — основной становится следующая")
   public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      garage.delete(id, principal.userId());
   }
}
