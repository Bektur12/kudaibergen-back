package kg.kudaibergen.catalog.importing;

import java.util.List;

/**
 * Итог импорта: сколько черновиков создано и какие строки пропущены. Черновики публикуются из
 * приложения после добавления фото — без фото запчасть не публикуется (ТЗ 9.3).
 */
public record ImportReportDto(int created, List<Long> partIds, List<PartImportMapper.RowError> errors) {
}
