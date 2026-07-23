import 'dart:convert';

import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

class ApiException implements Exception {
  const ApiException(this.message, {this.statusCode});

  final String message;
  final int? statusCode;

  @override
  String toString() => message;
}

class ApiClient {
  ApiClient._();

  static final ApiClient instance = ApiClient._();

  /// Chrome에서 Flutter Web을 실행할 때 백엔드 주소.
  ///
  /// 실제 배포에서는 환경별로 분리하는 것을 권장합니다.
  static const String baseUrl = 'https://my-game-hub-api.onrender.com';

  Future<Map<String, String>> _headers() async {
    final user = FirebaseAuth.instance.currentUser;

    if (user == null) {
      throw const ApiException('로그인이 필요합니다.');
    }

    final token = await user.getIdToken();

    if (token == null || token.isEmpty) {
      throw const ApiException('Firebase ID Token을 가져오지 못했습니다.');
    }

    return {
      'Authorization': 'Bearer $token',
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    };
  }

  Future<dynamic> get(String path) async {
    final response = await http.get(
      Uri.parse('$baseUrl$path'),
      headers: await _headers(),
    );

    return _decode(response);
  }

  Future<dynamic> post(
    String path, {
    Object? body,
  }) async {
    final response = await http.post(
      Uri.parse('$baseUrl$path'),
      headers: await _headers(),
      body: body == null ? null : jsonEncode(body),
    );

    return _decode(response);
  }

  Future<dynamic> put(
    String path, {
    Object? body,
  }) async {
    final response = await http.put(
      Uri.parse('$baseUrl$path'),
      headers: await _headers(),
      body: body == null ? null : jsonEncode(body),
    );
    debugPrint('PUT $path');
    debugPrint('status = ${response.statusCode}');
    debugPrint('body = ${response.body}');
    return _decode(response);
  }

  Future<void> delete(String path) async {
    final response = await http.delete(
      Uri.parse('$baseUrl$path'),
      headers: await _headers(),
    );

    if (response.statusCode < 200 || response.statusCode >= 300) {
      _throwApiError(response);
    }
  }

  dynamic _decode(http.Response response) {
    final isSuccess = response.statusCode >= 200 && response.statusCode < 300;

    if (!isSuccess) {
      throw ApiException(
        '서버 요청에 실패했습니다. (${response.statusCode})',
      );
    }

    // 204 No Content 등의 빈 응답 처리
    if (response.body.trim().isEmpty) {
      return null;
    }

    return jsonDecode(response.body);
  }

  Never _throwApiError(http.Response response) {
    String message = '서버 요청에 실패했습니다.';

    try {
      final decoded = jsonDecode(utf8.decode(response.bodyBytes));
      if (decoded is Map<String, dynamic> && decoded['message'] != null) {
        message = decoded['message'].toString();
      }
    } catch (_) {
      // JSON 형식이 아닌 에러 응답이면 기본 메시지를 사용합니다.
    }

    throw ApiException(
      message,
      statusCode: response.statusCode,
    );
  }
}
