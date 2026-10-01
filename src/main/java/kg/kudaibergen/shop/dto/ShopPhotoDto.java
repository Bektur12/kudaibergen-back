package kg.kudaibergen.shop.dto;

import kg.kudaibergen.media.PhotoDto;

/** Фото места (22): первое — «Обложка». id — id фото (media), по нему «сделать обложкой» и «удалить». */
public record ShopPhotoDto(Long id, PhotoDto photo, boolean isCover, int sort) {
}
