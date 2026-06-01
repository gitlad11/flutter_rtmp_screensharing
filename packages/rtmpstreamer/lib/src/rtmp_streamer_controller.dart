import 'dart:async';

import 'platform/rtmp_streamer_backend_factory.dart';
import 'rtmp_streamer_models.dart';

export 'rtmp_streamer_models.dart';

class RtmpStreamer {
  static final StreamController<RtmpStreamerEvent> _events =
      StreamController<RtmpStreamerEvent>.broadcast();

  static final _backend = createRtmpStreamerBackend(_events.add);

  static Stream<RtmpStreamerEvent> get events => _events.stream;

  static Future<bool> startPreviewCamera() => _backend.startPreviewCamera();

  static Future<bool> startPreviewScreen() => _backend.startPreviewScreen();

  static Future<bool> startStream(String url) => _backend.startStream(url);

  static Future<void> updateStreamSettings(RtmpStreamSettings settings) =>
      _backend.updateStreamSettings(settings);

  static Future<bool> setCameraOrientation(
    RtmpOrientation orientation, {
    int? rotationDegrees,
  }) =>
      _backend.setCameraOrientation(
        orientation,
        rotationDegrees: rotationDegrees,
      );

  static Future<void> stopStream() => _backend.stopStream();

  static Future<bool> switchSource(RtmpSource source) =>
      _backend.switchSource(source);

  static Future<bool> switchCamera() => _backend.switchCamera();

  static Future<bool> toggleMute() => _backend.toggleMute();

  static Future<RtmpStreamerState> getState() => _backend.getState();

  static Future<void> release() => _backend.release();
}
