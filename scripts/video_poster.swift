// Длительность, размер и кадр-обложка ролика (для демо-данных): swift video_poster.swift in.mp4 poster.jpg
import AVFoundation
import AppKit
let asset = AVURLAsset(url: URL(fileURLWithPath: CommandLine.arguments[1]))
let duration = CMTimeGetSeconds(asset.duration)
let track = asset.tracks(withMediaType: .video).first!
let size = track.naturalSize.applying(track.preferredTransform)
let generator = AVAssetImageGenerator(asset: asset)
generator.appliesPreferredTrackTransform = true
let frame = try! generator.copyCGImage(at: CMTime(seconds: min(1.5, duration / 2), preferredTimescale: 600), actualTime: nil)
let rep = NSBitmapImageRep(cgImage: frame)
try! rep.representation(using: .jpeg, properties: [.compressionFactor: 0.88])!.write(to: URL(fileURLWithPath: CommandLine.arguments[2]))
print(String(format: "%.1f %d %d", duration, Int(abs(size.width)), Int(abs(size.height))))
