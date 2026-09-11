import 'dart:typed_data';

import 'package:image_picker/image_picker.dart';

Future<Uint8List?> pickProfileImageBytesImpl() async {
  final pickedImage = await ImagePicker().pickImage(
    source: ImageSource.gallery,
    maxWidth: 768,
    maxHeight: 768,
    imageQuality: 85,
  );

  return pickedImage?.readAsBytes();
}
