package kg.kudaibergen.release;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * «Обновите приложение». Версии — в таблице app_releases: latest_version — что сейчас в сторе (ниже — окно
 * «Доступно обновление» с «Позже»), min_version — ниже неё окно без «Позже». Версию iOS берём из App Store сами
 * (iTunes lookup раз в 30 минут), Android — вручную UPDATE после выкладки в Google Play (публичного API нет).
 */
@Service
public class AppReleaseService {

   private static final Logger log = LoggerFactory.getLogger(AppReleaseService.class);
   static final String BUNDLE_ID = "kg.kudaiberdi.app";

   private final NamedParameterJdbcTemplate jdbc;
   private final boolean appStoreLookup;
   private final RestClient itunes;

   public AppReleaseService(NamedParameterJdbcTemplate jdbc,
                            @Value("${app.releases.app-store-lookup:true}") boolean appStoreLookup) {
      this.jdbc = jdbc;
      this.appStoreLookup = appStoreLookup;
      SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(Duration.ofSeconds(5));
      factory.setReadTimeout(Duration.ofSeconds(10));
      this.itunes = RestClient.builder().baseUrl("https://itunes.apple.com").requestFactory(factory).build();
   }

   public AppReleaseDto check(StorePlatform platform, String version) {
      return jdbc.query("select latest_version, min_version, store_url from app_releases where platform = :p",
            Map.of("p", platform.name()), rs -> {
               if (!rs.next()) {
                  return new AppReleaseDto(null, null, null, false, false);
               }
               String latest = rs.getString("latest_version");
               String min = rs.getString("min_version");
               boolean required = version != null && compare(version, min) < 0;
               boolean available = version != null && compare(version, latest) < 0;
               return new AppReleaseDto(latest, min, rs.getString("store_url"), available || required, required);
            });
   }

   /** Новая версия в App Store — сразу окно у тех, у кого старая. Ошибки сети не мешают: попробуем через 30 минут. */
   @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT1M")
   public void syncAppStore() {
      if (!appStoreLookup) {
         return;
      }
      try {
         JsonNode body = itunes.get().uri("/lookup?bundleId={id}&country=kg", BUNDLE_ID).retrieve().body(JsonNode.class);
         JsonNode version = body == null ? null : body.path("results").path(0).path("version");
         if (version == null || !version.isTextual()) {
            return;
         }
         int updated = jdbc.update("""
               update app_releases set latest_version = :v, updated_at = now()
               where platform = 'IOS' and latest_version <> :v""", Map.of("v", version.asText()));
         if (updated > 0) {
            log.info("App Store: новая версия {}", version.asText());
         }
      } catch (RestClientException e) {
         log.warn("App Store lookup не удался: {}", e.getMessage());
      }
   }

   /** «1.0.10» > «1.0.9»: по числам, недостающие части — 0, нечисловое — 0. */
   static int compare(String a, String b) {
      String[] left = a.strip().split("\\.");
      String[] right = b.strip().split("\\.");
      for (int i = 0; i < Math.max(left.length, right.length); i++) {
         int diff = Integer.compare(part(left, i), part(right, i));
         if (diff != 0) {
            return diff;
         }
      }
      return 0;
   }

   private static int part(String[] parts, int i) {
      if (i >= parts.length) {
         return 0;
      }
      String digits = parts[i].replaceAll("\\D.*", "");
      return digits.isEmpty() ? 0 : Integer.parseInt(digits);
   }

   public enum StorePlatform {
      IOS,
      ANDROID
   }
}
