package kg.kudaibergen.admin.tenants;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import kg.kudaibergen.admin.tenants.TenantSheet.RawRow;
import kg.kudaibergen.market.entity.Side;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantSheetTest {

   @Test
   void телефоныВЛюбойЗаписи() {
      assertThat(TenantSheet.phone("0555 12 34 56")).isEqualTo("+996555123456");
      assertThat(TenantSheet.phone("555123456")).isEqualTo("+996555123456");
      assertThat(TenantSheet.phone("+996 (555) 12-34-56")).isEqualTo("+996555123456");
      assertThat(TenantSheet.phone("996555123456")).isEqualTo("+996555123456");
      assertThat(TenantSheet.phone(" ")).isNull();
      assertThatThrownBy(() -> TenantSheet.phone("12345")).isInstanceOf(IllegalArgumentException.class);
   }

   @Test
   void стороныИРяды() {
      assertThat(TenantSheet.side("С")).isEqualTo(Side.NORTH);
      assertThat(TenantSheet.side("юг")).isEqualTo(Side.SOUTH);
      assertThat(TenantSheet.side("З.")).isEqualTo(Side.WEST);
      assertThat(TenantSheet.side("E")).isEqualTo(Side.EAST);
      assertThat(TenantSheet.side("")).isNull();
      assertThatThrownBy(() -> TenantSheet.side("верх")).isInstanceOf(IllegalArgumentException.class);
      assertThat(TenantSheet.rowKey("Ряд 14")).isEqualTo("14");
      assertThat(TenantSheet.rowKey("ряд Ш")).isEqualTo("ш");
   }

   @Test
   void csvСЗаголовкомКавычкамиИBom() throws Exception {
      String csv = "﻿Ряд,Номер,Сторона,ФИО,Телефон\n14,12,С,\"Иванов, А.\",0555123456\n\n14,13,,,0700000000\n";
      List<RawRow> rows = TenantSheet.read(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), "t.csv");

      assertThat(rows).hasSize(2);
      assertThat(rows.get(0)).isEqualTo(new RawRow(2, "14", "12", "С", "Иванов, А.", "0555123456"));
      assertThat(rows.get(1).line()).isEqualTo(4);
   }

   @Test
   void шаблонЧитаетсяОбратно() throws Exception {
      List<RawRow> rows = TenantSheet.read(new ByteArrayInputStream(TenantSheet.template()), "tenants.xlsx");

      assertThat(rows).containsExactly(new RawRow(2, "14", "12", "С", "Токтосунов Азамат", "+996555123456"));
   }
}
