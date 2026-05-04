# RTMP Streamer

Android/Kotlin RTMP streaming library with a Flutter sample app.

The project is split into two parts:

```text
android/rtmpstreamer   Android library module
android/app            Flutter Android sample/bridge app
lib/                   Flutter demo UI and MethodChannel controller
```

## What The Library Provides

The reusable library lives in `android/rtmpstreamer` under package:

```kotlin
com.gitlad.rtmpstreamer
```

Main components:

| Component | Purpose |
| --- | --- |
| `RtmpStreamingClient` | Main streaming API: preview, RTMP start/stop, camera/screen source, mute, orientation, raw frames. |
| `StreamSettings` | Video/audio stream configuration: width, height, FPS, bitrate, orientation, rotation. |
| `StreamEvent` / `StreamEventListener` | Connection and streaming events. |
| `RawFrame` / `RawFrameListener` | Access to encoded H264/AAC frames while streaming/previewing. |
| `RawFrameChunker` / `RawFrameChunkListener` | Groups encoded frames into chunks for recording/upload pipelines. |
| `AspectRatioTextureView` | TextureView helper for preview surfaces. |
| `PreviewSurfaceHolder` | Shared holder for camera/screen preview surfaces. |
| `ScreenShareForegroundService` | Foreground service required for Android screen capture. |

Internally the current encoder/RTMP backend is based on PedroSG94 RootEncoder:

```kotlin
com.github.pedroSG94.RootEncoder:library:2.6.7
com.github.pedroSG94.RootEncoder:rtmp:2.6.7
```

## Android Requirements

Minimum SDK:

```text
minSdk 23
```

Recommended permissions for an app using the library:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

The library manifest declares the screen sharing foreground service. The app is still responsible for runtime permissions and `MediaProjection` user consent.

## Installation

The library is prepared as an Android AAR module:

```text
android/rtmpstreamer
```

Local module dependency from this repository:

```kotlin
dependencies {
    implementation(project(":rtmpstreamer"))
}
```

JitPack dependency after publishing a GitHub release/tag:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

```kotlin
dependencies {
    implementation("com.github.gitlad11:flutter_rtmp_screensharing:v0.1.0")
}
```

The JitPack build uses:

```text
jitpack.yml
android/settings.rtmpstreamer.gradle.kts
```

This lets JitPack build only `:rtmpstreamer` without requiring Flutter SDK.

## Basic Kotlin Usage

```kotlin
val client = RtmpStreamingClient(
    context = applicationContext,
    listener = object : StreamEventListener {
        override fun onStreamEvent(event: StreamEvent) {
            println("${event.type}: ${event.message}")
        }
    },
)

client.updateSettings(
    StreamSettings(
        width = 1280,
        height = 720,
        fps = 30,
        bitrate = 2_500_000,
        orientation = StreamOrientation.LANDSCAPE,
    ),
)

client.startPreview(textureView)
client.startStream("rtmp://192.168.101.117:1935/live/test")
```

Stop and release:

```kotlin
client.stopStream()
client.release()
```

## Events

Common event types:

```text
preview_started
connecting
connected
started
stopped
disconnected
failed
error
bitrate
auth_success
source_changed
orientation_changed
```

## Camera And Screen Source

Switch back to camera:

```kotlin
client.switchToCamera()
```

Switch to screen after the app obtains a `MediaProjection`:

```kotlin
client.switchToScreen(mediaProjection)
```

For screen sharing on Android, the app must:

1. Start `ScreenShareForegroundService`.
2. Request `MediaProjection` permission.
3. Pass the resulting `MediaProjection` into `client.switchToScreen(...)`.

The Flutter sample app does this in `MainActivity`.

## Orientation Control

Orientation can be configured before prepare/start:

```kotlin
client.updateSettings(
    StreamSettings(
        width = 1280,
        height = 720,
        orientation = StreamOrientation.PORTRAIT,
        rotationDegrees = 90,
    ),
)
```

It can also be changed explicitly:

```kotlin
client.setCameraOrientation(
    orientation = StreamOrientation.PORTRAIT,
    rotationDegrees = 90,
)
```

Supported rotation values are normalized to:

```text
0, 90, 180, 270
```

## Raw Encoded Frames

The library can expose encoded H264/AAC frames:

```kotlin
client.setRawFrameListener(object : RawFrameListener {
    override fun onFrame(frame: RawFrame) {
        when (frame.type) {
            RawFrameType.VIDEO -> {
                println("video ${frame.size}, key=${frame.isKeyFrame}")
            }
            RawFrameType.AUDIO -> {
                println("audio ${frame.size}")
            }
        }
    }
})
```

`RawFrame.data` is not a complete video file. It is encoded frame data. To produce playable files, wrap frames into a container such as FLV, MP4, fMP4, or HLS segments.

## Chunked Recording API

For progressive recording/upload, use frame chunks:

```kotlin
client.setRawFrameChunkListener(
    listener = object : RawFrameChunkListener {
        override fun onChunk(chunk: RawFrameChunk) {
            println("chunk=${chunk.index}, bytes=${chunk.bytes}, durationUs=${chunk.durationUs}")
        }
    },
    maxDurationUs = 2_000_000L,
    maxBytes = 2 * 1024 * 1024,
)
```

This returns chunks of encoded frames. It is useful for:

- uploading while recording;
- building a custom archive pipeline;
- creating future FLV/HLS/fMP4 segment writers;
- debugging stream output.

Current limitation: chunks are not yet muxed into `.flv` or `.mp4` files. A future `FlvChunkWriter` can be built on top of this API.

## Flutter Bridge

The sample app includes a Dart bridge:

```dart
lib/native_rtmp_controller.dart
```

Example:

```dart
await NativeRtmpController.updateStreamSettings(
  const RtmpStreamSettings(
    width: 1280,
    height: 720,
    fps: 30,
    bitrate: 2500000,
    orientation: RtmpOrientation.landscape,
  ),
);

await NativeRtmpController.startPreviewCamera();
await NativeRtmpController.startStream('rtmp://192.168.101.117:1935/live/test');
```

Listen to native events:

```dart
NativeRtmpController.events.listen((event) {
  print('${event.type}: ${event.message}');
});
```

Change orientation:

```dart
await NativeRtmpController.setCameraOrientation(
  RtmpOrientation.portrait,
  rotationDegrees: 90,
);
```

The Flutter bridge is currently part of the sample app. The reusable Android streaming implementation is in `android/rtmpstreamer`.

## MediaMTX Test

For local MediaMTX:

```text
RTMP publish: rtmp://<host-ip>:1935/live/test
HTTP watch:   http://<host-ip>:8888/live/test/
```

For Android Emulator, use:

```text
rtmp://10.0.2.2:1935/live/test
```

For a physical phone, use the computer LAN IP, for example:

```text
rtmp://192.168.101.117:1935/live/test
```

The phone and computer must be on the same network, and firewall rules must allow ports `1935` and `8888`.

## Current Limitations

- RTMP protocol/chunk internals are still handled by Pedro/RootEncoder.
- `RawFrameChunker` emits encoded frame chunks, not ready-to-play media files.
- Flutter `PlatformView` registration still lives in the sample app.
- Runtime permission flow is handled by the sample app.

## Planned Next Steps

- Add `FlvWriter` and `FlvChunkWriter`.
- Add optional recording API that writes playable `.flv` chunks.
- Add richer stats: dropped frames, sent frames, cache size, bytes sent.
- Package the Flutter bridge as a plugin if the library should be consumed directly from Flutter.

## Publishing

Check local Maven publication:

```powershell
cd android
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat -c settings.rtmpstreamer.gradle.kts :rtmpstreamer:publishToMavenLocal
```

Create a GitHub release/tag:

```powershell
git add .
git commit -m "Prepare rtmpstreamer library publishing"
git tag v0.1.0
git push origin main
git push origin v0.1.0
```

Then open:

```text
https://jitpack.io/#gitlad11/flutter_rtmp_screensharing/v0.1.0
```
