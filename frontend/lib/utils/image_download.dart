import 'dart:typed_data';

import 'image_download_stub.dart'
    if (dart.library.js_interop) 'image_download_web.dart';

Future<void> downloadPng({
  required Uint8List bytes,
  required String fileName,
}) {
  return downloadPngImpl(
    bytes: bytes,
    fileName: fileName,
  );
}
