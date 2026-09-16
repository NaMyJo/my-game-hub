import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

const _imageDownloadChannel = MethodChannel(
  'com.mygamehub.app/image_download',
);

Future<void> downloadPngImpl({
  required Uint8List bytes,
  required String fileName,
}) async {
  if (defaultTargetPlatform != TargetPlatform.android) {
    throw UnsupportedError(
      '현재 플랫폼에서는 PNG 다운로드를 지원하지 않습니다.',
    );
  }

  await _imageDownloadChannel.invokeMethod<void>(
    'savePng',
    <String, Object>{
      'bytes': bytes,
      'fileName': fileName,
    },
  );
}
