import 'dart:async';
import 'dart:js_interop';
import 'dart:typed_data';

import 'package:web/web.dart' as web;

Future<Uint8List?> pickProfileImageBytesImpl() {
  final completer = Completer<Uint8List?>();
  final input = web.HTMLInputElement()
    ..type = 'file'
    ..accept = 'image/*'
    // iOS in-app WebView는 display:none인 file input의 기준 위치를 찾지
    // 못하면 마지막 터치 지점 주변에 팝오버를 배치할 수 있다. 화면 상단의
    // 투명한 실제 요소를 기준점으로 제공해 선택 메뉴가 아래로 열리도록
    // 유도한다. 요소는 포인터 입력과 화면 배치에는 영향을 주지 않는다.
    ..style.position = 'fixed'
    ..style.top = '8px'
    ..style.left = '50%'
    ..style.width = '1px'
    ..style.height = '1px'
    ..style.opacity = '0'
    ..style.pointerEvents = 'none'
    ..style.zIndex = '0';

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
