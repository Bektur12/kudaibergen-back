package kg.kudaibergen.delivery.dto;

import java.util.List;

import kg.kudaibergen.delivery.entity.DeliveryDistrict;
import kg.kudaibergen.delivery.entity.DeliveryRegion;
import org.springframework.lang.Nullable;

/**
 * «КУДА ВЕЗТИ»: плитки областей в порядке сетки, у Оша и Джалал-Абада — чипы отдалённых районов.
 * price — до 5 кг (null — по Бишкеку цену назовёт курьер), days — 0 «сегодня», 1 «1 день», 2 «2 дня».
 * cutoff — «Закажите до 17:30 — курьер заберёт посылку сегодня». surchargeNote — про доплату за вес.
 */
public record DeliveryTariffsDto(List<RegionDto> regions, String cutoff, String remoteNote, String surchargeNote,
                                 String courierName, @Nullable String courierPhone) {

   public record RegionDto(DeliveryRegion code, String name, String inName, int days, @Nullable Integer price,
                           List<DistrictDto> districts) {
   }

   public record DistrictDto(DeliveryDistrict code, String name, int days, int price) {
   }
}
