import 'dart:async';
import 'dart:io';

import 'package:firebase_auth/firebase_auth.dart';
import 'package:http/http.dart' as http;

Future<bool> canReachNetworkHost(Uri uri) async {
  Socket? socket;

  try {
    socket = await Socket.connect(
      uri.host,
      uri.hasPort ? uri.port : 443,
      timeout: const Duration(seconds: 4),
    );
    return true;
  } on SocketException {
    return false;
  } on TimeoutException {
    return false;
  } catch (_) {
    return false;
  } finally {
    socket?.destroy();
  }
}

bool isNetworkException(Object error) {
  return error is SocketException ||
      error is TimeoutException ||
      error is http.ClientException ||
      error is FirebaseAuthException && error.code == 'network-request-failed';
}
