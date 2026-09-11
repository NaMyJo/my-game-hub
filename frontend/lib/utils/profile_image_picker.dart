import 'dart:typed_data';

import 'profile_image_picker_stub.dart'
    if (dart.library.js_interop) 'profile_image_picker_web.dart';

Future<Uint8List?> pickProfileImageBytes() {
  return pickProfileImageBytesImpl();
}
