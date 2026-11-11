// CDN для фото, голосовых и видео KUDAIBERDI. Бакет Railway приватный и стоит в США; Worker забирает файл
// своими ключами один раз и держит его в кэше узла Cloudflare рядом с пользователем. Ключи файлов — UUID,
// содержимое по ключу не меняется, поэтому кэш вечный. Range (перемотка видео) режет сам Cache API.
import { AwsClient } from 'aws4fetch'

const KEY = /^[a-z_]+\/(?:[a-z_]+\/)?[0-9a-f-]{36}[a-z0-9-]*\.[a-z0-9]{2,5}$/

export default {
  async fetch(request, env, ctx) {
    if (request.method !== 'GET' && request.method !== 'HEAD') return new Response('Method not allowed', { status: 405 })
    const url = new URL(request.url)
    const key = decodeURIComponent(url.pathname.slice(1))
    if (!KEY.test(key)) return new Response('Not found', { status: 404 })

    const cache = caches.default
    const cacheKey = url.origin + '/' + key
    // Cache API сам режет ответ по Range (206) — для перемотки видео
    const hit = await cache.match(new Request(cacheKey, { headers: request.headers }))
    if (hit) return request.method === 'HEAD' ? new Response(null, { status: hit.status, headers: hit.headers }) : hit

    const aws = new AwsClient({ accessKeyId: env.S3_ACCESS_KEY, secretAccessKey: env.S3_SECRET_KEY, region: env.S3_REGION, service: 's3' })
    const origin = await aws.fetch(`https://${env.S3_BUCKET}.${env.S3_HOST}/${key}`)
    if (origin.status === 404 || origin.status === 403) return new Response('Not found', { status: 404 })
    if (!origin.ok) return new Response('Storage error', { status: 502 })

    const headers = new Headers()
    for (const h of ['content-type', 'content-length', 'etag', 'last-modified']) if (origin.headers.has(h)) headers.set(h, origin.headers.get(h))
    headers.set('Cache-Control', 'public, max-age=31536000, immutable')
    headers.set('Access-Control-Allow-Origin', '*')
    headers.set('Accept-Ranges', 'bytes')
    // Первый запрос — весь файл (200, клиенты это принимают и на Range), параллельно кладём в кэш
    const [toClient, toCache] = origin.body.tee()
    ctx.waitUntil(cache.put(cacheKey, new Response(toCache, { headers })))
    return new Response(request.method === 'HEAD' ? null : toClient, { status: 200, headers })
  },
}
