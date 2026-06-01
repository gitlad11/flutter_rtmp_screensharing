import 'dart:ui_web' as ui_web;

import 'package:flutter/widgets.dart';

import '../platform/rtmp_streamer_backend_web.dart';

const _viewType = 'rtmpstreamer_web_preview';
bool _registered = false;

void _ensureRegistered() {
  if (_registered) return;
  _registered = true;
  ui_web.platformViewRegistry.registerViewFactory(
    _viewType,
    (int viewId, {Object? params}) => rtmpStreamerWebPreviewElement,
  );
}

class RtmpCameraPreview extends StatelessWidget {
  const RtmpCameraPreview({super.key});

  @override
  Widget build(BuildContext context) {
    _ensureRegistered();
    return const HtmlElementView(viewType: _viewType);
  }
}
