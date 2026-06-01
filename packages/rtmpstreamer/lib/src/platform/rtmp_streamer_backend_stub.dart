import '../rtmp_streamer_models.dart';
import 'rtmp_streamer_backend.dart';

RtmpStreamerBackend createRtmpStreamerBackend(RtmpEventCallback onEvent) {
  return _UnsupportedRtmpStreamerBackend();
}

class _UnsupportedRtmpStreamerBackend implements RtmpStreamerBackend {
  UnsupportedError get _error =>
      UnsupportedError('rtmpstreamer is not supported on this platform');

  @override
  Future<RtmpStreamerState> getState() => Future.error(_error);

  @override
  Future<void> release() => Future.error(_error);

  @override
  Future<bool> setCameraOrientation(
    RtmpOrientation orientation, {
    int? rotationDegrees,
  }) =>
      Future.error(_error);

  @override
  Future<bool> startPreviewCamera() => Future.error(_error);

  @override
  Future<bool> startPreviewScreen() => Future.error(_error);

  @override
  Future<bool> startStream(String url) => Future.error(_error);

  @override
  Future<void> stopStream() => Future.error(_error);

  @override
  Future<bool> switchCamera() => Future.error(_error);

  @override
  Future<bool> switchSource(RtmpSource source) => Future.error(_error);

  @override
  Future<bool> toggleMute() => Future.error(_error);

  @override
  Future<void> updateStreamSettings(RtmpStreamSettings settings) =>
      Future.error(_error);
}
