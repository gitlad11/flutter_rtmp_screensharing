import 'dart:async';
import 'dart:js_interop';

import 'package:web/web.dart' as web;

import '../rtmp_streamer_models.dart';
import 'rtmp_streamer_backend.dart';

final web.HTMLVideoElement rtmpStreamerWebPreviewElement =
    web.document.createElement('video') as web.HTMLVideoElement
      ..autoplay = true
      ..muted = true
      ..setAttribute('playsinline', 'true')
      ..style.width = '100%'
      ..style.height = '100%'
      ..style.objectFit = 'contain'
      ..style.backgroundColor = 'black';

RtmpStreamerBackend createRtmpStreamerBackend(RtmpEventCallback onEvent) {
  return _WebRtmpStreamerBackend(onEvent);
}

class _WebRtmpStreamerBackend implements RtmpStreamerBackend {
  _WebRtmpStreamerBackend(this._onEvent);

  final RtmpEventCallback _onEvent;

  RtmpStreamSettings _settings = const RtmpStreamSettings();
  RtmpSource _source = RtmpSource.camera;
  web.MediaStreamTrack? _videoTrack;
  web.MediaStreamTrack? _audioTrack;
  web.RTCPeerConnection? _peerConnection;
  String? _whipResourceUrl;
  bool _isStreaming = false;
  bool _isMuted = false;
  bool _frontCamera = false;

  @override
  Future<bool> startPreviewCamera() async {
    return _guard(() async {
      final stream = await web.window.navigator.mediaDevices
          .getUserMedia(
            web.MediaStreamConstraints(
              audio: (_audioTrack == null).toJS,
              video: _cameraConstraints,
            ),
          )
          .toDart;
      final videoTrack = stream.getVideoTracks().toDart.first;
      final audioTracks = stream.getAudioTracks().toDart;
      if (_audioTrack == null && audioTracks.isNotEmpty) {
        _audioTrack = audioTracks.first;
      }
      await _setVideoTrack(videoTrack, source: RtmpSource.camera);
      _emit('preview_started', _source.value);
      return true;
    });
  }

  @override
  Future<bool> startPreviewScreen() => switchSource(RtmpSource.screen);

  @override
  Future<bool> startStream(String url) async {
    return _guard(() async {
      if (_isStreaming) return true;
      if (!url.startsWith('http://') && !url.startsWith('https://')) {
        throw ArgumentError(
          'Web publishing requires a WHIP URL, for example '
          'http://localhost:8889/live/test/whip',
        );
      }
      if (_videoTrack == null && !await startPreviewCamera()) {
        return false;
      }
      final videoTrack = _videoTrack;
      if (videoTrack == null) return false;

      _emit('connecting', url);
      final peerConnection = web.RTCPeerConnection(
        web.RTCConfiguration(iceServers: <web.RTCIceServer>[].toJS),
      );
      _peerConnection = peerConnection;
      peerConnection.addTrack(videoTrack, _previewStream);
      final audioTrack = _audioTrack;
      if (audioTrack != null) {
        peerConnection.addTrack(audioTrack, _previewStream);
      }
      peerConnection.onconnectionstatechange = ((web.Event _) {
        switch (peerConnection.connectionState) {
          case 'connected':
            _emit('connected');
          case 'failed':
            _emit('failed', 'WebRTC connection failed');
          case 'disconnected':
            _emit('disconnected');
        }
      }).toJS;

      final offer = await peerConnection.createOffer().toDart;
      if (offer == null) throw StateError('Failed to create WHIP offer');
      await peerConnection
          .setLocalDescription(
            web.RTCLocalSessionDescriptionInit(
              type: offer.type,
              sdp: offer.sdp,
            ),
          )
          .toDart;
      await _waitForIceGathering(peerConnection);
      final sdp = peerConnection.localDescription?.sdp;
      if (sdp == null) throw StateError('Failed to create WHIP offer');

      final response = await web.window
          .fetch(
            url.toJS,
            web.RequestInit(
              method: 'POST',
              headers: <String, String>{
                'Content-Type': 'application/sdp',
              }.jsify()! as JSObject,
              body: sdp.toJS,
            ),
          )
          .toDart;
      if (!response.ok) {
        throw StateError('WHIP server returned HTTP ${response.status}');
      }
      final answer = (await response.text().toDart).toDart;
      if (answer.isEmpty) {
        throw StateError('WHIP server returned an empty SDP answer');
      }
      await peerConnection
          .setRemoteDescription(
            web.RTCSessionDescriptionInit(type: 'answer', sdp: answer),
          )
          .toDart;
      final location = response.headers.get('Location');
      if (location != null) {
        _whipResourceUrl = Uri.parse(url).resolve(location).toString();
      }
      _isStreaming = true;
      _emit('started', _source.value);
      return true;
    });
  }

  @override
  Future<void> updateStreamSettings(RtmpStreamSettings settings) async {
    _settings = settings;
  }

  @override
  Future<bool> setCameraOrientation(
    RtmpOrientation orientation, {
    int? rotationDegrees,
  }) async {
    _settings = RtmpStreamSettings(
      width: _settings.width,
      height: _settings.height,
      fps: _settings.fps,
      bitrate: _settings.bitrate,
      orientation: orientation,
      rotationDegrees: rotationDegrees,
    );
    _emit(
      'orientation_changed',
      '${orientation.value}:${rotationDegrees ?? orientation.defaultRotationDegrees}',
    );
    return true;
  }

  @override
  Future<void> stopStream() async {
    final resourceUrl = _whipResourceUrl;
    _whipResourceUrl = null;
    _peerConnection?.close();
    _peerConnection = null;
    _isStreaming = false;
    if (resourceUrl != null) {
      unawaited(
        web.window
            .fetch(
              resourceUrl.toJS,
              web.RequestInit(method: 'DELETE'),
            )
            .toDart
            .catchError((_) => web.Response()),
      );
    }
    _emit('stopped');
  }

  @override
  Future<bool> switchSource(RtmpSource source) async {
    if (source == RtmpSource.camera) return startPreviewCamera();
    return _guard(() async {
      final stream = await web.window.navigator.mediaDevices
          .getDisplayMedia(
            web.DisplayMediaStreamOptions(
              video: true.toJS,
              audio: false.toJS,
            ),
          )
          .toDart;
      final videoTrack = stream.getVideoTracks().toDart.first;
      videoTrack.onended = ((web.Event _) {
        unawaited(startPreviewCamera());
      }).toJS;
      await _setVideoTrack(videoTrack, source: RtmpSource.screen);
      _emit('source_changed', _source.value);
      return true;
    });
  }

  @override
  Future<bool> switchCamera() async {
    _frontCamera = !_frontCamera;
    return startPreviewCamera();
  }

  @override
  Future<bool> toggleMute() async {
    _isMuted = !_isMuted;
    _audioTrack?.enabled = !_isMuted;
    return _isMuted;
  }

  @override
  Future<RtmpStreamerState> getState() async {
    return RtmpStreamerState(
      source: _source,
      isStreaming: _isStreaming,
      isOnPreview: _videoTrack != null,
      isMuted: _isMuted,
    );
  }

  @override
  Future<void> release() async {
    await stopStream();
    _videoTrack?.stop();
    _audioTrack?.stop();
    _videoTrack = null;
    _audioTrack = null;
    rtmpStreamerWebPreviewElement.srcObject = null;
  }

  JSObject get _cameraConstraints => <String, Object>{
        'width': {'ideal': _settings.width},
        'height': {'ideal': _settings.height},
        'frameRate': {'ideal': _settings.fps},
        'facingMode': _frontCamera ? 'user' : 'environment',
      }.jsify()! as JSObject;

  web.MediaStream get _previewStream => web.MediaStream(
        <web.MediaStreamTrack>[
          if (_videoTrack != null) _videoTrack!,
          if (_audioTrack != null) _audioTrack!,
        ].toJS,
      );

  Future<void> _setVideoTrack(
    web.MediaStreamTrack track, {
    required RtmpSource source,
  }) async {
    final oldTrack = _videoTrack;
    final senders = _peerConnection?.getSenders().toDart ?? const [];
    final videoSenders =
        senders.where((sender) => sender.track?.kind == 'video');
    if (videoSenders.isNotEmpty) {
      await videoSenders.first.replaceTrack(track).toDart;
    }
    _videoTrack = track;
    _source = source;
    rtmpStreamerWebPreviewElement.srcObject = _previewStream;
    unawaited(rtmpStreamerWebPreviewElement.play().toDart);
    oldTrack?.stop();
  }

  Future<void> _waitForIceGathering(
    web.RTCPeerConnection peerConnection,
  ) async {
    if (peerConnection.iceGatheringState == 'complete') return;
    final completer = Completer<void>();
    peerConnection.onicecandidate = ((web.Event _) {
      if (peerConnection.iceGatheringState == 'complete' &&
          !completer.isCompleted) {
        completer.complete();
      }
    }).toJS;
    await completer.future.timeout(
      const Duration(seconds: 5),
      onTimeout: () {
        throw TimeoutException('Timed out while gathering ICE candidates');
      },
    );
  }

  Future<bool> _guard(Future<bool> Function() action) async {
    try {
      return await action();
    } catch (error) {
      _emit('failed', error.toString());
      return false;
    }
  }

  void _emit(String type, [String? message]) {
    _onEvent(RtmpStreamerEvent(type: type, message: message));
  }
}
