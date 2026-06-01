class RtmpStreamerEvent {
  const RtmpStreamerEvent({
    required this.type,
    this.message,
  });

  final String type;
  final String? message;

  factory RtmpStreamerEvent.fromJson(Map<Object?, Object?> json) {
    return RtmpStreamerEvent(
      type: json['type']?.toString() ?? 'event',
      message: json['message']?.toString(),
    );
  }
}

class RtmpStreamerState {
  const RtmpStreamerState({
    required this.source,
    required this.isStreaming,
    required this.isOnPreview,
    required this.isMuted,
  });

  final RtmpSource source;
  final bool isStreaming;
  final bool isOnPreview;
  final bool isMuted;

  factory RtmpStreamerState.fromJson(Map<String, dynamic> json) {
    return RtmpStreamerState(
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
  screen('screen'),
  combined('combined');

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
