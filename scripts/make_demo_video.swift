// Демо-ролик «как рилсы» для «Видео с рынка»: 720×1280, 30 к/с, H.264. Кадры — фото (810×1440) с медленным наездом
// и сдвигом (Ken Burns), между ними — растворение; на каждом кадре — крупная рекламная подпись.
//
//   swift scripts/make_demo_video.swift out.mp4 poster.jpg "Подпись 1|Подпись 2|…" photo1.jpg photo2.jpg …
//
// Только для локальных демо-данных (scripts/demo_seed.py).
import AVFoundation
import AppKit
import CoreVideo

let args = CommandLine.arguments
guard args.count >= 5 else {
    FileHandle.standardError.write("usage: out.mp4 poster.jpg \"cap1|cap2\" photo…\n".data(using: .utf8)!)
    exit(1)
}
let outURL = URL(fileURLWithPath: args[1])
let posterPath = args[2]
let captions = args[3].split(separator: "|").map(String.init)
let photos: [CGImage] = args[4...].compactMap { path in
    guard let image = NSImage(contentsOfFile: path) else { return nil }
    var rect = CGRect(origin: .zero, size: image.size)
    return image.cgImage(forProposedRect: &rect, context: nil, hints: nil)
}
guard !photos.isEmpty else { exit(2) }

let width = 720, height = 1280, fps: Int32 = 30
let perPhoto = 12.0 / Double(photos.count)
let fade = 0.5
let totalFrames = Int(12.0 * Double(fps))

try? FileManager.default.removeItem(at: outURL)
let writer = try! AVAssetWriter(outputURL: outURL, fileType: .mp4)
let input = AVAssetWriterInput(mediaType: .video, outputSettings: [
    AVVideoCodecKey: AVVideoCodecType.h264,
    AVVideoWidthKey: width,
    AVVideoHeightKey: height,
    AVVideoCompressionPropertiesKey: [AVVideoAverageBitRateKey: 2_500_000, AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel],
])
input.expectsMediaDataInRealTime = false
let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [
    kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB,
    kCVPixelBufferWidthKey as String: width,
    kCVPixelBufferHeightKey as String: height,
])
writer.add(input)
writer.startWriting()
writer.startSession(atSourceTime: .zero)

/** One photo at progress p (0…1 within its slot): zoom 1.0→1.12, drifting sideways — even slots left, odd right. */
func drawPhoto(_ ctx: CGContext, _ image: CGImage, index: Int, progress p: Double, alpha: CGFloat) {
    let zoom = 1.0 + 0.12 * p
    let w = Double(width) * 1.125 * zoom, h = Double(height) * 1.125 * zoom
    let drift = (index % 2 == 0 ? -1.0 : 1.0) * 40 * (p - 0.5)
    let rect = CGRect(x: (Double(width) - w) / 2 + drift, y: (Double(height) - h) / 2, width: w, height: h)
    ctx.saveGState()
    ctx.setAlpha(alpha)
    ctx.interpolationQuality = .high
    ctx.draw(image, in: rect)
    ctx.restoreGState()
}

/** The caption in the upper third: bold white, a soft shadow, fading in with its photo. */
func drawCaption(_ ctx: CGContext, _ text: String, alpha: CGFloat) {
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(cgContext: ctx, flipped: false)
    let shadow = NSShadow()
    shadow.shadowColor = NSColor.black.withAlphaComponent(0.65)
    shadow.shadowBlurRadius = 18
    shadow.shadowOffset = NSSize(width: 0, height: -2)
    let style = NSMutableParagraphStyle()
    style.alignment = .center
    let attributes: [NSAttributedString.Key: Any] = [
        .font: NSFont.systemFont(ofSize: 64, weight: .heavy),
        .foregroundColor: NSColor.white.withAlphaComponent(alpha),
        .shadow: shadow,
        .paragraphStyle: style,
        .kern: -1.0,
    ]
    let box = CGRect(x: 40, y: Double(height) * 0.56, width: Double(width) - 80, height: 260)
    (text as NSString).draw(with: box, options: [.usesLineFragmentOrigin], attributes: attributes)
    NSGraphicsContext.restoreGraphicsState()
}

func render(frame: Int) -> CVPixelBuffer {
    var buffer: CVPixelBuffer?
    CVPixelBufferPoolCreatePixelBuffer(nil, adaptor.pixelBufferPool!, &buffer)
    let pixels = buffer!
    CVPixelBufferLockBaseAddress(pixels, [])
    let ctx = CGContext(data: CVPixelBufferGetBaseAddress(pixels), width: width, height: height, bitsPerComponent: 8,
                        bytesPerRow: CVPixelBufferGetBytesPerRow(pixels), space: CGColorSpaceCreateDeviceRGB(),
                        bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue)!
    ctx.setFillColor(CGColor(red: 0, green: 0, blue: 0, alpha: 1))
    ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))
    let t = Double(frame) / Double(fps)
    let slot = min(photos.count - 1, Int(t / perPhoto))
    let local = (t - Double(slot) * perPhoto) / perPhoto
    drawPhoto(ctx, photos[slot], index: slot, progress: local, alpha: 1)
    // into the next photo over the last `fade` seconds of this one
    let untilEnd = Double(slot + 1) * perPhoto - t
    var captionAlpha = CGFloat(min(1, local * perPhoto / 0.4))
    if slot + 1 < photos.count && untilEnd < fade {
        let k = CGFloat(1 - untilEnd / fade)
        drawPhoto(ctx, photos[slot + 1], index: slot + 1, progress: 0, alpha: k)
        captionAlpha *= 1 - k
    }
    if !captions.isEmpty {
        drawCaption(ctx, captions[slot % captions.count], alpha: captionAlpha)
    }
    if frame == Int(perPhoto * 0.5 * Double(fps)) {
        // the poster: the first photo with its caption, half way through
        if let image = ctx.makeImage() {
            let rep = NSBitmapImageRep(cgImage: image)
            try? rep.representation(using: .jpeg, properties: [.compressionFactor: 0.88])?.write(to: URL(fileURLWithPath: posterPath))
        }
    }
    CVPixelBufferUnlockBaseAddress(pixels, [])
    return pixels
}

for frame in 0..<totalFrames {
    while !input.isReadyForMoreMediaData { usleep(2000) }
    adaptor.append(render(frame: frame), withPresentationTime: CMTime(value: CMTimeValue(frame), timescale: fps))
}
input.markAsFinished()
let done = DispatchSemaphore(value: 0)
writer.finishWriting { done.signal() }
done.wait()
print(writer.status == .completed ? "ok" : "failed: \(String(describing: writer.error))")
