package kg.kudaibergen.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Синонимы поиска: «стойка» → «амортизатор». Справочник ведёт суперадмин. */
@Repository
public class SynonymRepository {

   private final NamedParameterJdbcTemplate jdbc;

   public SynonymRepository(NamedParameterJdbcTemplate jdbc) {
      this.jdbc = jdbc;
   }

   /**
    * [слово запроса, синоним]. Слово сравнивается с термином и как есть, и по основе
    * (русский стеммер): «стойки» находит термин «стойка».
    */
   public List<String[]> synonymsOf(Collection<String> tokens) {
      if (tokens.isEmpty()) {
         return List.of();
      }
      return jdbc.query("""
                  select t.token, s.synonym
                  from unnest(array[:tokens]::text[]) as t(token)
                  join search_synonyms s
                    on s.term = t.token
                    or to_tsvector('russian', s.term) @@ plainto_tsquery('russian', t.token)""",
            Map.of("tokens", tokens), (rs, n) -> new String[]{rs.getString(1), rs.getString(2)});
   }
}
