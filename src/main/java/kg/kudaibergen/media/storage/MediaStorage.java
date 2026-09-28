package kg.kudaibergen.media.storage;

import java.io.InputStream;

/**
 * Хранилище файлов: фото товаров, вложения чата. В базе лежит ключ, URL для клиента собирается
 * при выдаче ({@link #urlFor}). Реализации: локальная папка (dev, app.media.storage=local)
 * и приватный бакет MinIO/S3 с presigned-ссылками (s3).
 */
public interface MediaStorage {

   /** URL для клиента; null-безопасно. */
   String urlFor(String key);

   /** Кладёт объект под ключом. Ключи — UUID, содержимое по ключу не меняется. */
   void put(String key, InputStream content, long size, String contentType);
}
