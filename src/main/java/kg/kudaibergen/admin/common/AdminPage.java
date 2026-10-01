package kg.kudaibergen.admin.common;

import java.util.List;

import org.springframework.lang.Nullable;

/**
 * Список админки: {items, nextCursor, counts}. nextCursor = null — дальше ничего нет.
 * counts — серверные счётчики табов и чипов экрана, с учётом поиска q, но без учёта таба.
 */
public record AdminPage<T, C>(List<T> items, @Nullable String nextCursor, C counts) {
}
