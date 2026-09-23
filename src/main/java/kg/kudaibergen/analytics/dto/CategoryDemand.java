package kg.kudaibergen.analytics.dto;

/** Что чаще всего спрашивают — подсказка, что закупить на склад. */
public record CategoryDemand(String category, long requests, long answered) {
}
