import 'rtmp_streamer_backend.dart';
import 'rtmp_streamer_backend_stub.dart'
    if (dart.library.io) 'rtmp_streamer_backend_native.dart'
    if (dart.library.html) 'rtmp_streamer_backend_web.dart' as implementation;

RtmpStreamerBackend createRtmpStreamerBackend(RtmpEventCallback onEvent) {
  return implementation.createRtmpStreamerBackend(onEvent);
}
