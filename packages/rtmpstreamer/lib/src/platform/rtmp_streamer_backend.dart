import '../rtmp_streamer_models.dart';

typedef RtmpEventCallback = void Function(RtmpStreamerEvent event);

abstract class RtmpStreamerBackend {
  Future<bool> startPreviewCamera();
  Future<bool> startPreviewScreen();
  Future<bool> startStream(String url);
  Future<void> updateStreamSettings(RtmpStreamSettings settings);
  Future<bool> setCameraOrientation(
    RtmpOrientation orientation, {
    int? rotationDegrees,
  });
  Future<void> stopStream();
  Future<bool> switchSource(RtmpSource source);
  Future<bool> switchCamera();
  Future<bool> toggleMute();
  Future<RtmpStreamerState> getState();
  Future<void> release();
}
