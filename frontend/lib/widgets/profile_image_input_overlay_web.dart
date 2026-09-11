import 'dart:async';
import 'dart:js_interop';
import 'dart:typed_data';
import 'dart:ui_web' as ui_web;

import 'package:flutter/material.dart';
import 'package:web/web.dart' as web;

class ProfileImageInputOverlay extends StatefulWidget {
  const ProfileImageInputOverlay({
    super.key,
    required this.onImageSelected,
    required this.onError,
  });

  final Future<void> Function(Uint8List bytes) onImageSelected;
  final void Function(Object error, StackTrace stackTrace) onError;

  @override
  State<ProfileImageInputOverlay> createState() =>
      _ProfileImageInputOverlayState();
}

class _ProfileImageInputOverlayState extends State<ProfileImageInputOverlay> {
  static int _nextViewId = 0;

  late final String _viewType;
  late final web.HTMLInputElement _input;

  @override
  void initState() {
    super.initState();
    _viewType = 'profile-image-input-${_nextViewId++}';
    _input = web.HTMLInputElement()
      ..type = 'file'
      ..accept = 'image/*'
      ..setAttribute('aria-label', '사진 선택')
      ..style.position = 'absolute'
      ..style.inset = '0'
      ..style.display = 'block'
      ..style.width = '100%'
      ..style.height = '100%'
      ..style.margin = '0'
      ..style.padding = '0'
      ..style.border = '0'
      ..style.opacity = '0'
      ..style.cursor = 'pointer';

    _input.onchange = (web.Event _) {
      unawaited(_readSelectedImage());
    }.toJS;
    _input.onerror = (web.Event _) {
      widget.onError(
        StateError('이미지 파일을 읽을 수 없습니다.'),
        StackTrace.current,
      );
    }.toJS;

    ui_web.platformViewRegistry.registerViewFactory(
      _viewType,
      (int viewId) => _input,
    );
  }

  Future<void> _readSelectedImage() async {
    try {
      final files = _input.files;
      final file = files == null || files.length == 0 ? null : files.item(0);
      if (file == null) return;

      final arrayBuffer = await file.arrayBuffer().toDart;
      await widget.onImageSelected(arrayBuffer.toDart.asUint8List());
      _input.value = '';
    } catch (error, stackTrace) {
      widget.onError(error, stackTrace);
    }
  }

  @override
  void dispose() {
    _input.remove();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return HtmlElementView(viewType: _viewType);
  }
}
