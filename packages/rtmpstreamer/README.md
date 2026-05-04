# rtmpstreamer

Flutter plugin for Android RTMP streaming with camera preview, screen sharing, orientation control, native events, raw encoded frames, and chunked frame capture support on Android.

Current platform support:

```text
Android only
```

## Install

From Git:

```yaml
dependencies:
  rtmpstreamer:
    git:
      url: https://github.com/gitlad11/flutter_rtmp_screensharing.git
      path: packages/rtmpstreamer
      ref: v0.1.0
```

After publishing on pub.dev:

```yaml
dependencies:
  rtmpstreamer: ^0.1.0
```

## Android Permissions

The plugin manifest declares the required permissions and foreground service. Your app still needs to request runtime permissions when Android asks for them.

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

Screen sharing:

```dart
await RtmpStreamer.startPreviewScreen();
```

Android will show the MediaProjection consent dialog.

Orientation:

```dart
await RtmpStreamer.setCameraOrientation(
  RtmpOrientation.portrait,
  rotationDegrees: 90,
);
```

## MediaMTX Test

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

- Android only.
- RTMP protocol internals are currently handled by PedroSG94 RootEncoder.
- Raw/chunk APIs are available in the Android layer; Dart-facing raw frame streaming is not exposed yet.
- Chunked capture returns encoded frame chunks, not muxed `.flv`/`.mp4` files yet.
