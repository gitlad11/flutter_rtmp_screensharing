import 'dart:async';

import 'package:flutter/services.dart';

class NativeRtmpController {
  static const MethodChannel _channel = MethodChannel('screen_share_channel');

  static final StreamController<NativeRtmpEvent> _events =
      StreamController<NativeRtmpEvent>.broadcast();

  static bool _isListening = false;

  static Stream<NativeRtmpEvent> get events {
    _ensureListening();
    return _events.stream;
  }

  static Future<bool> startPreviewCamera() async {
    return await _invokeBool('startPreviewCamera');
  }

  static Future<bool> startPreviewScreen() async {
    return await _invokeBool('startPreviewScreen');
  }

  static Future<bool> startStream(String urlFull) async {
    return await _invokeBool('startStream', {'url': urlFull});
  }

  static Future<void> updateStreamSettings(RtmpStreamSettings settings) {
    return _channel.invokeMethod<void>(
      'updateStreamSettings',
      settings.toJson(),
    );
  }

  static Future<void> updateStreamSettingsMap(Map<String, dynamic> settings) {
    return _channel.invokeMethod<void>('updateStreamSettings', settings);
  }

  static Future<bool> setCameraOrientation(
    RtmpOrientation orientation, {
    int? rotationDegrees,
  }) async {
    return await _invokeBool('setCameraOrientation', {
      'orientation': orientation.value,
      'rotationDegrees': rotationDegrees ?? orientation.defaultRotationDegrees,
    });
  }

  static Future<void> stopStream() {
    return _channel.invokeMethod<void>('stopStream');
  }

  static Future<bool> switchSource(RtmpSource source) async {
    return await _invokeBool('switchSource', source.value);
  }

  static Future<bool> switchSourceRaw(String to) async {
    return await _invokeBool('switchSource', to);
  }

  static Future<bool> switchCamera() async {
    return await _invokeBool('switchCamera');
  }

  static Future<bool> toggleMute() async {
    return await _invokeBool('toggleMute');
  }

  static Future<NativeRtmpState> getState() async {
    final result = await _channel.invokeMapMethod<String, dynamic>('getState');
    return NativeRtmpState.fromJson(result ?? const {});
  }

  static Future<void> release() {
    return _channel.invokeMethod<void>('release');
  }

  static void _ensureListening() {
    if (_isListening) return;
    _isListening = true;
    _channel.setMethodCallHandler((call) async {
      if (call.method != 'onNativeEvent') return null;
      final args = Map<Object?, Object?>.from(call.arguments as Map);
      _events.add(NativeRtmpEvent.fromJson(args));
      return null;
    });
  }

  static Future<bool> _invokeBool(String method, [Object? arguments]) async {
    return (await _channel.invokeMethod<bool>(method, arguments)) == true;
  }
}

class NativeRtmpEvent {
  const NativeRtmpEvent({
    required this.type,
    this.message,
  });

  final String type;
  final String? message;

  factory NativeRtmpEvent.fromJson(Map<Object?, Object?> json) {
    return NativeRtmpEvent(
      type: json['type']?.toString() ?? 'event',
      message: json['message']?.toString(),
    );
  }
}

class NativeRtmpState {
  const NativeRtmpState({
    required this.source,
    required this.isStreaming,
    required this.isOnPreview,
    required this.isMuted,
  });

  final RtmpSource source;
  final bool isStreaming;
  final bool isOnPreview;
  final bool isMuted;

  factory NativeRtmpState.fromJson(Map<String, dynamic> json) {
    return NativeRtmpState(
      source: RtmpSource.fromValue(json['source']?.toString()),
      isStreaming: json['isStreaming'] == true,
      isOnPreview: json['isOnPreview'] == true,
      isMuted: json['isMuted'] == true,
    );
  }
}

class RtmpStreamSettings {
  const RtmpStreamSettings({
    this.width = 1280,
    this.height = 720,
    this.fps = 30,
    this.bitrate = 2500000,
    this.orientation = RtmpOrientation.landscape,
    this.rotationDegrees,
  });

  final int width;
  final int height;
  final int fps;
  final int bitrate;
  final RtmpOrientation orientation;
  final int? rotationDegrees;

  Map<String, dynamic> toJson() {
    return {
      'width': width,
      'height': height,
      'fps': fps,
      'bitrate': bitrate,
      'orientation': orientation.value,
      'rotationDegrees': rotationDegrees ?? orientation.defaultRotationDegrees,
    };
  }
}

enum RtmpSource {
  camera('camera'),
  screen('screen');

  const RtmpSource(this.value);

  final String value;

  static RtmpSource fromValue(String? value) {
    return RtmpSource.values.firstWhere(
      (source) => source.value == value,
      orElse: () => RtmpSource.camera,
    );
  }
}

enum RtmpOrientation {
  landscape('landscape', 0),
  portrait('portrait', 90);

  const RtmpOrientation(this.value, this.defaultRotationDegrees);

  final String value;
  final int defaultRotationDegrees;
}
