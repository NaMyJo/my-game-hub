import 'dart:async';

import 'package:firebase_auth/firebase_auth.dart';
import 'package:http/http.dart' as http;

Future<bool> canReachNetworkHost(Uri uri) async => true;

bool isNetworkException(Object error) {
  return error is TimeoutException ||
      error is http.ClientException ||
      error is FirebaseAuthException && error.code == 'network-request-failed';
}
