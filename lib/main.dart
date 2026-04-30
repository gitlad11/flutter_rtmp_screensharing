import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const RtmpDemoApp());
}

class RtmpDemoApp extends StatelessWidget {
  const RtmpDemoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'RTMP Preview',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF0F766E),
          brightness: Brightness.dark,
        ),
        scaffoldBackgroundColor: const Color(0xFF101418),
        useMaterial3: true,
      ),
      home: const RtmpHomePage(),
    );
  }
}

class RtmpHomePage extends StatefulWidget {
  const RtmpHomePage({super.key});

  @override
  State<RtmpHomePage> createState() => _RtmpHomePageState();
}

class _RtmpHomePageState extends State<RtmpHomePage> with WidgetsBindingObserver {
  static const _channel = MethodChannel('screen_share_channel');
  static const _nativeCameraPreviewType = 'native_camera_preview';
  static const _defaultRtmpUrl = 'rtmp://192.168.101.116:1935/live/test';
  static const _watchUrl = 'http://192.168.101.116:8888/live/test/';

  final _urlController = TextEditingController(text: _defaultRtmpUrl);

  String _status = 'Готово к превью';
  String _source = 'camera';
  bool _isPreviewStarted = false;
  bool _isStreaming = false;
  bool _isMuted = false;
  bool _isBusy = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _channel.setMethodCallHandler(_handleNativeEvent);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _applyDefaultSettings();
      _startPreview();
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _urlController.dispose();
    _channel.invokeMethod<void>('release');
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && !_isPreviewStarted) {
      _startPreview();
    }
  }

  Future<dynamic> _handleNativeEvent(MethodCall call) async {
    if (call.method != 'onNativeEvent') return null;
    final data = Map<Object?, Object?>.from(call.arguments as Map);
    final type = data['type']?.toString() ?? 'event';
    final message = data['message']?.toString();

    setState(() {
      switch (type) {
        case 'preview_started':
          _isPreviewStarted = true;
          _source = message ?? _source;
          _status = 'Превью камеры запущено';
          break;
        case 'connecting':
          _status = 'Подключение к $message';
          break;
        case 'connected':
          _isStreaming = true;
          _status = 'Стрим идет';
          break;
        case 'started':
          _isStreaming = true;
          _source = message ?? _source;
          _status = 'RTMP отправляется';
          break;
        case 'stopped':
        case 'disconnected':
          _isStreaming = false;
          _status = 'Стрим остановлен';
          break;
        case 'source_changed':
          _source = message ?? _source;
          _status = _source == 'screen' ? 'Источник: экран' : 'Источник: камера';
          break;
        case 'failed':
        case 'error':
          _isStreaming = false;
          _status = message == null ? 'Ошибка RTMP' : 'Ошибка: $message';
          break;
        default:
          _status = message == null ? type : '$type: $message';
          break;
      }
    });
    return null;
  }

  Future<void> _applyDefaultSettings() {
    return _channel.invokeMethod<void>('updateStreamSettings', const {
      'width': 1280,
      'height': 720,
      'fps': 30,
      'bitrate': 2500000,
      'orientation': 'landscape',
    });
  }

  Future<void> _startPreview() async {
    if (!Platform.isAndroid || _isBusy) return;
    setState(() {
      _isBusy = true;
      _status = 'Запускаю превью...';
    });

    try {
      await _applyDefaultSettings();
      final ok = await _channel.invokeMethod<bool>('startPreviewCamera') ?? false;
      setState(() {
        _isPreviewStarted = ok;
        _status = ok
            ? 'Превью камеры запущено'
            : 'Разрешите камеру/микрофон и нажмите превью еще раз';
      });
    } on PlatformException catch (e) {
      setState(() => _status = e.message ?? 'Не удалось запустить превью');
    } finally {
      if (mounted) setState(() => _isBusy = false);
    }
  }

  Future<void> _startStream() async {
    if (_isBusy) return;
    final url = _urlController.text.trim();
    if (url.isEmpty) {
      setState(() => _status = 'Введите RTMP URL');
      return;
    }

    setState(() {
      _isBusy = true;
      _status = 'Старт RTMP...';
    });

    try {
      await _applyDefaultSettings();
      if (!_isPreviewStarted) await _channel.invokeMethod<bool>('startPreviewCamera');
      final ok = await _channel.invokeMethod<bool>('startStream', {'url': url}) ?? false;
      setState(() {
        _isStreaming = ok;
        _status = ok ? 'RTMP отправляется' : 'Не удалось стартовать RTMP';
      });
    } on PlatformException catch (e) {
      setState(() => _status = e.message ?? 'Ошибка старта RTMP');
    } finally {
      if (mounted) setState(() => _isBusy = false);
    }
  }

  Future<void> _stopStream() async {
    await _channel.invokeMethod<bool>('stopStream');
    setState(() {
      _isStreaming = false;
      _status = 'Стрим остановлен';
    });
  }

  Future<void> _switchCamera() async {
    final ok = await _channel.invokeMethod<bool>('switchCamera') ?? false;
    setState(() => _status = ok ? 'Камера переключена' : 'Камера пока недоступна');
  }

  Future<void> _toggleMute() async {
    final muted = await _channel.invokeMethod<bool>('toggleMute') ?? _isMuted;
    setState(() {
      _isMuted = muted;
      _status = muted ? 'Микрофон выключен' : 'Микрофон включен';
    });
  }

  Future<void> _switchSource(String source) async {
    final ok = await _channel.invokeMethod<bool>('switchSource', source) ?? false;
    setState(() {
      if (ok) _source = source;
      _status = ok
          ? (source == 'screen' ? 'Подтвердите захват экрана' : 'Источник: камера')
          : 'Не удалось сменить источник';
    });
  }

  @override
  Widget build(BuildContext context) {
    final isAndroid = !kIsWeb && Platform.isAndroid;

    return Scaffold(
      appBar: AppBar(
        title: const Text('RTMP тест'),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: Center(child: _StatusPill(isLive: _isStreaming)),
          ),
        ],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            AspectRatio(
              aspectRatio: 16 / 9,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: DecoratedBox(
                  decoration: const BoxDecoration(color: Colors.black),
                  child: isAndroid
                      ? const AndroidView(viewType: _nativeCameraPreviewType)
                      : const Center(child: Text('Native preview доступен на Android')),
                ),
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _urlController,
              keyboardType: TextInputType.url,
              decoration: const InputDecoration(
                labelText: 'RTMP publish URL',
                helperText: 'Для просмотра откройте http://localhost:8888/live/test/',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            Text(_status, style: Theme.of(context).textTheme.bodyLarge),
            const SizedBox(height: 16),
            Wrap(
              spacing: 10,
              runSpacing: 10,
              children: [
                FilledButton.icon(
                  onPressed: _isBusy ? null : _startPreview,
                  icon: const Icon(Icons.videocam),
                  label: const Text('Превью'),
                ),
                FilledButton.icon(
                  onPressed: _isBusy || _isStreaming ? null : _startStream,
                  icon: const Icon(Icons.podcasts),
                  label: const Text('Старт'),
                ),
                OutlinedButton.icon(
                  onPressed: _isStreaming ? _stopStream : null,
                  icon: const Icon(Icons.stop_circle),
                  label: const Text('Стоп'),
                ),
                OutlinedButton.icon(
                  onPressed: _source == 'camera' ? _switchCamera : null,
                  icon: const Icon(Icons.cameraswitch),
                  label: const Text('Камера'),
                ),
                OutlinedButton.icon(
                  onPressed: _toggleMute,
                  icon: Icon(_isMuted ? Icons.mic_off : Icons.mic),
                  label: Text(_isMuted ? 'Звук выкл.' : 'Звук вкл.'),
                ),
              ],
            ),
            const SizedBox(height: 16),
            SegmentedButton<String>(
              segments: const [
                ButtonSegment(
                  value: 'camera',
                  icon: Icon(Icons.photo_camera),
                  label: Text('Камера'),
                ),
                ButtonSegment(
                  value: 'screen',
                  icon: Icon(Icons.screen_share),
                  label: Text('Экран'),
                ),
              ],
              selected: {_source},
              onSelectionChanged: (value) => _switchSource(value.first),
            ),
            const SizedBox(height: 16),
            SelectableText(
              'Проверка MediaMTX: $_watchUrl',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ],
        ),
      ),
    );
  }
}

class _StatusPill extends StatelessWidget {
  const _StatusPill({required this.isLive});

  final bool isLive;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: isLive ? const Color(0xFFB91C1C) : const Color(0xFF374151),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        child: Text(
          isLive ? 'LIVE' : 'IDLE',
          style: Theme.of(context).textTheme.labelMedium?.copyWith(
                color: Colors.white,
                fontWeight: FontWeight.w700,
              ),
        ),
      ),
    );
  }
}
