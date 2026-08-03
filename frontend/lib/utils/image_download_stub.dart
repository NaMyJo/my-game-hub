import 'dart:typed_data';

Future<void> downloadPngImpl({
  required Uint8List bytes,
  required String fileName,
}) {
  throw UnsupportedError(
    '현재 플랫폼에서는 PNG 다운로드를 지원하지 않습니다.',
  );
}
