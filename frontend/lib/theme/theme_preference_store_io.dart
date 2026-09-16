import 'package:flutter/services.dart';

const _channel = MethodChannel('com.mygamehub.app/theme_preferences');

Future<String?> readThemePreference(String key) =>
    _channel.invokeMethod<String>('read', {'key': key});

Future<void> writeThemePreference(String key, String value) =>
    _channel.invokeMethod<void>('write', {'key': key, 'value': value});
