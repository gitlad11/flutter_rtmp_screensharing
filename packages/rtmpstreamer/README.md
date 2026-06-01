# rtmpstreamer

Flutter live streaming with camera preview and on-the-fly screen sharing.

`rtmpstreamer` is built for Flutter apps that need live streaming with runtime
switching between the camera and screen sharing. Android publishes RTMP through
PedroSG94 RootEncoder. Web publishes WebRTC through a WHIP endpoint.

Current platform support:

```text
Android and Web
```

## Contact

For questions and feedback: efimovi420@gmail.com

## Install

From pub.dev:

```yaml
dependencies:
  rtmpstreamer: ^0.2.1
```

From Git:

```yaml
dependencies:
  rtmpstreamer:
    git:
      url: https://github.com/gitlad11/flutter_rtmp_screensharing.git
      path: packages/rtmpstreamer
      ref: main
```

The Android implementation uses PedroSG94 RootEncoder. Gradle downloads it
automatically from JitPack when the Android app is built. Add JitPack to the
repositories in your Flutter app if it is not already available.

For Kotlin DSL projects, add it to `android/settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
  }
}
```

For Groovy projects, add this line to the repositories block in
`android/build.gradle`:

```gradle
maven { url 'https://jitpack.io' }
```

## Features

- RTMP publishing from Flutter on Android.
- WHIP publishing from Flutter Web.
- Native camera preview widget.
- Microphone audio capture.
- On-the-fly switching from camera stream to screen sharing.
- Android MediaProjection screen capture support.
- Camera switching.
- Mute/unmute microphone.
- Stream orientation and rotation control.
- Native stream events: connecting, connected, started, failed, bitrate, stopped.
- Android raw encoded H264/AAC frame access.
- Android chunked encoded frame capture for recording/upload pipelines.

## Android Permissions

The plugin manifest declares the required permissions and foreground service.
The plugin requests camera and microphone permissions before starting the
camera preview. On Android 13 and newer, the host app should request notification
permission when appropriate for its UX.

Required capabilities:

```text
CAMERA
RECORD_AUDIO
INTERNET
FOREGROUND_SERVICE
FOREGROUND_SERVICE_MEDIA_PROJECTION
POST_NOTIFICATIONS
```

## Usage

Import:

```dart
import 'package:rtmpstreamer/rtmpstreamer.dart';
```

Add preview:

```dart
const AspectRatio(
  aspectRatio: 16 / 9,
  child: RtmpCameraPreview(),
)
```

Configure and start:

```dart
await RtmpStreamer.updateStreamSettings(
  const RtmpStreamSettings(
    width: 1280,
    height: 720,
    fps: 30,
    bitrate: 2500000,
    orientation: RtmpOrientation.landscape,
  ),
);

await RtmpStreamer.startPreviewCamera();
await RtmpStreamer.startStream('rtmp://<host-ip>:1935/live/test');
```

On Web, pass a WHIP endpoint instead:

```dart
await RtmpStreamer.startStream(
  'http://localhost:8889/live/test/whip',
);
```

Listen to events:

```dart
RtmpStreamer.events.listen((event) {
  print('${event.type}: ${event.message}');
});
```

Stop:

```dart
await RtmpStreamer.stopStream();
await RtmpStreamer.release();
```

Switch camera:

```dart
await RtmpStreamer.switchCamera();
```

Mute:

```dart
final muted = await RtmpStreamer.toggleMute();
```

On-the-fly screen sharing:

```dart
await RtmpStreamer.switchSource(RtmpSource.screen);
```

Android will show the MediaProjection consent dialog. After approval, the native Android layer switches the active stream source from camera to screen sharing.

On Web, show the shared screen with a camera overlay:

```dart
await RtmpStreamer.switchSource(RtmpSource.combined);
```

The combined source draws the shared screen and a camera overlay into a browser
canvas, then publishes the canvas stream through WHIP. This source is Web-only.

Switch back to camera:

```dart
await RtmpStreamer.switchSource(RtmpSource.camera);
```

Orientation:

```dart
await RtmpStreamer.setCameraOrientation(
  RtmpOrientation.portrait,
  rotationDegrees: 90,
);
```

## MediaMTX Test

For a quick local RTMP server with HLS playback:

```bash
docker run --rm -p 1935:1935 -p 8888:8888 -p 8889:8889 -p 8189:8189/udp bluenviron/mediamtx:latest
```

Publish URL:

```text
rtmp://<host-ip>:1935/live/test
```

Watch URL:

```text
http://<host-ip>:8888/live/test/
```

For Android Emulator:

```text
rtmp://10.0.2.2:1935/live/test
```

For a physical device, use the computer LAN IP.

For Flutter Web:

```text
http://localhost:8889/live/test/whip
```

Camera and screen capture require a secure browser context. `localhost` works
for local development. Production deployments should use HTTPS. Cross-origin
WHIP publishing also requires the media server to allow the web app origin.

## Native Android API

The plugin contains the Android core under:

```text
com.gitlad.rtmpstreamer
```

Main classes:

```text
RtmpStreamingClient
StreamSettings
StreamEvent
RawFrameListener
RawFrameChunker
ScreenShareForegroundService
PreviewSurfaceHolder
```

## Limitations

- Android RTMP and Web WHIP use different transports behind the same Dart API.
- iOS is not supported yet.
- RTMP protocol internals are currently handled by PedroSG94 RootEncoder.
- Raw/chunk APIs are available in the Android layer; Dart-facing raw frame streaming is not exposed yet.
- Chunked capture returns encoded frame chunks, not muxed `.flv`/`.mp4` files yet.
