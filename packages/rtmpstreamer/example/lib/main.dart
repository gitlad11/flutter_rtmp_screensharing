import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:rtmpstreamer/rtmpstreamer.dart';

void main() {
  runApp(const ExampleApp());
}

class ExampleApp extends StatefulWidget {
  const ExampleApp({super.key});

  @override
  State<ExampleApp> createState() => _ExampleAppState();
}

class _ExampleAppState extends State<ExampleApp> {
  final _urlController = TextEditingController(
    text: kIsWeb
        ? 'http://localhost:8889/live/test/whip'
        : 'rtmp://<host-ip>:1935/live/test',
  );

  String _status = 'Idle';
  bool _isLive = false;

  @override
  void initState() {
    super.initState();
    RtmpStreamer.events.listen((event) {
      if (!mounted) return;
      setState(() {
        _status = event.message == null
            ? event.type
            : '${event.type}: ${event.message}';
        if (event.type == 'connected' || event.type == 'started')
          _isLive = true;
        if (event.type == 'stopped' ||
            event.type == 'disconnected' ||
            event.type == 'failed') {
          _isLive = false;
        }
      });
    });
  }

  @override
  void dispose() {
    _urlController.dispose();
    RtmpStreamer.release();
    super.dispose();
  }

  Future<void> _start() async {
    await RtmpStreamer.updateStreamSettings(
      const RtmpStreamSettings(
        width: 1280,
        height: 720,
        fps: 30,
        bitrate: 2500000,
      ),
    );
    await RtmpStreamer.startPreviewCamera();
    await RtmpStreamer.startStream(_urlController.text.trim());
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: Text(_isLive ? 'RTMP Live' : 'RTMP Example')),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            const AspectRatio(
              aspectRatio: 16 / 9,
              child: ColoredBox(
                color: Colors.black,
                child: RtmpCameraPreview(),
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _urlController,
              decoration: const InputDecoration(
                labelText: kIsWeb ? 'WHIP URL' : 'RTMP URL',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            Text(_status),
            const SizedBox(height: 16),
            Wrap(
              spacing: 8,
              children: [
                FilledButton(
                  onPressed: _start,
                  child: const Text('Start'),
                ),
                OutlinedButton(
                  onPressed: RtmpStreamer.stopStream,
                  child: const Text('Stop'),
                ),
                OutlinedButton(
                  onPressed: RtmpStreamer.switchCamera,
                  child: const Text('Switch camera'),
                ),
                OutlinedButton(
                  onPressed: () => RtmpStreamer.switchSource(RtmpSource.screen),
                  child: const Text('Screen'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
