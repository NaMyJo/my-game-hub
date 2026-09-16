import 'package:web/web.dart' as web;

Future<String?> readThemePreference(String key) async =>
    web.window.localStorage.getItem(key);

Future<void> writeThemePreference(String key, String value) async {
  web.window.localStorage.setItem(key, value);
}
