package kg.kudaibergen.master.dto;

import kg.kudaibergen.master.entity.ServiceDuration;

/**
 * Плитка услуги (33, 38). needsLocation — заявке нужна точка на карте; urgent — «Срочно»;
 * defaultDuration — сколько по умолчанию ждать отклики (эвакуатор — 15 минут); maxRadiusKm — предел радиуса заявки
 * и мастера (эвакуатор, выездной — 200 км, остальное — 50 для заявки и 30 для мастера).
 */
public record ServiceTypeDto(String code, String name, String icon, boolean needsLocation, boolean urgent,
                             ServiceDuration defaultDuration, int maxRadiusKm) {
}
