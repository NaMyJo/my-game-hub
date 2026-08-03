import 'dart:js_interop';
import 'dart:typed_data';

import 'package:web/web.dart' as web;

Future<void> downloadPngImpl({
  required Uint8List bytes,
  required String fileName,
}) async {
  final blob = web.Blob(
    <web.BlobPart>[
      bytes.toJS,
    ].toJS,
    web.BlobPropertyBag(
      type: 'image/png',
    ),
  );

  final objectUrl = web.URL.createObjectURL(blob);

  final anchor = web.document.createElement('a') as web.HTMLAnchorElement;

  anchor.href = objectUrl;
  anchor.download = fileName;
  anchor.style.display = 'none';

  web.document.body?.append(anchor);

  anchor.click();
  anchor.remove();

  web.URL.revokeObjectURL(objectUrl);
}
