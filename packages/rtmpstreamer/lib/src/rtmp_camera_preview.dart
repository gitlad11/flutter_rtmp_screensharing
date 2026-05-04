import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

class RtmpCameraPreview extends StatelessWidget {
  const RtmpCameraPreview({super.key});

  static const viewType = 'rtmpstreamer_camera_preview';

  @override
  Widget build(BuildContext context) {
    if (kIsWeb || !Platform.isAndroid) {
      return const SizedBox.shrink();
    }
    return const AndroidView(viewType: viewType);
  }
}
