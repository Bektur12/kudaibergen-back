package kg.kudaibergen.media;

/** Фото для клиента: url — 1080 px (карточка, карусель), thumbUrl — 320 px (сетка, превью). */
public record PhotoDto(Long id, String url, String thumbUrl, int width, int height) {
}
