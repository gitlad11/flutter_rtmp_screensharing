import 'package:flutter/services.dart';

import '../rtmp_streamer_models.dart';
import 'rtmp_streamer_backend.dart';

RtmpStreamerBackend createRtmpStreamerBackend(RtmpEventCallback onEvent) {
  return _NativeRtmpStreamerBackend(onEvent);
}

class _NativeRtmpStreamerBackend implements RtmpStreamerBackend {
  _NativeRtmpStreamerBackend(this._onEvent) {
    _channel.setMethodCallHandler((call) async {
      if (call.method != 'onNativeEvent') return null;
      _onEvent(
        RtmpStreamerEvent.fromJson(
          Map<Object?, Object?>.from(call.arguments as Map),
        ),
      );
      return null;
    });
  }

  static const MethodChannel _channel = MethodChannel('rtmpstreamer');
  final RtmpEventCallback _onEvent;

  @override
  Future<bool> startPreviewCamera() => _invokeBool('startPreviewCamera');

  @override
  Future<bool> startPreviewScreen() => _invokeBool('startPreviewScreen');

  @override
  Future<bool> startStream(String url) =>
      _invokeBool('startStream', {'url': url});

  @override
  Future<void> updateStreamSettings(RtmpStreamSettings settings) =>
      _channel.invokeMethod<void>('updateStreamSettings', settings.toJson());

  @override
  Future<bool> setCameraOrientation(
    RtmpOrientation orientation, {
    int? rotationDegrees,
  }) =>
      _invokeBool('setCameraOrientation', {
        'orientation': orientation.value,
        'rotationDegrees':
            rotationDegrees ?? orientation.defaultRotationDegrees,
      });

  @override
  Future<void> stopStream() => _channel.invokeMethod<void>('stopStream');

  @override
  Future<bool> switchSource(RtmpSource source) =>
      _invokeBool('switchSource', source.value);

  @override
  Future<bool> switchCamera() => _invokeBool('switchCamera');

  @override
  Future<bool> toggleMute() => _invokeBool('toggleMute');

  @override
  Future<RtmpStreamerState> getState() async {
    final result = await _channel.invokeMapMethod<String, dynamic>('getState');
    return RtmpStreamerState.fromJson(result ?? const {});
  }

  @override
  Future<void> release() => _channel.invokeMethod<void>('release');

  Future<bool> _invokeBool(String method, [Object? arguments]) async {
    return (await _channel.invokeMethod<bool>(method, arguments)) == true;
  }
}
