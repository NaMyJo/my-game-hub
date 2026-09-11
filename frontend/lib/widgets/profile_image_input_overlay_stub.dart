import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../utils/profile_image_picker.dart';

class ProfileImageInputOverlay extends StatelessWidget {
  const ProfileImageInputOverlay({
    super.key,
    required this.onImageSelected,
    required this.onError,
  });

  final Future<void> Function(Uint8List bytes) onImageSelected;
  final void Function(Object error, StackTrace stackTrace) onError;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: '사진 선택',
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () async {
          try {
            final bytes = await pickProfileImageBytes();
            if (bytes != null) await onImageSelected(bytes);
          } catch (error, stackTrace) {
            onError(error, stackTrace);
          }
        },
        child: const SizedBox.expand(),
      ),
    );
  }
}
