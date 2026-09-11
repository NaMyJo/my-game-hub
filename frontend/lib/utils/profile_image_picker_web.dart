import 'dart:async';
import 'dart:js_interop';
import 'dart:typed_data';

import 'package:web/web.dart' as web;

Future<Uint8List?> pickProfileImageBytesImpl() {
  final completer = Completer<Uint8List?>();
  final input = web.HTMLInputElement()
    ..type = 'file'
    ..accept = 'image/*'
    ..style.display = 'none';

  void complete(Uint8List? bytes) {
    if (!completer.isCompleted) {
      completer.complete(bytes);
    }
  }

  input.onchange = (web.Event _) {
    () async {
      try {
        final files = input.files;
        final file = files == null || files.length == 0 ? null : files.item(0);

        if (file == null) {
          complete(null);
          return;
        }

        final arrayBuffer = await file.arrayBuffer().toDart;
        complete(arrayBuffer.toDart.asUint8List());
      } catch (error, stackTrace) {
        if (!completer.isCompleted) {
          completer.completeError(error, stackTrace);
        }
      }
    }();
  }.toJS;

  input.oncancel = (web.Event _) {
    complete(null);
  }.toJS;
  input.onerror = (web.Event _) {
    if (!completer.isCompleted) {
      completer.completeError(StateError('이미지 파일을 읽을 수 없습니다.'));
    }
  }.toJS;

  web.document.body?.append(input);
  input.click();

  return completer.future.whenComplete(() {
    input.remove();
  });
}
