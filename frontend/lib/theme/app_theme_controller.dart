import 'dart:async';

import 'package:flutter/foundation.dart';

import 'theme_preference_store.dart';

enum AppThemeMode { dark, light, pink }

extension AppThemeModeX on AppThemeMode {
  String get storageValue => name;

  String get displayName => switch (this) {
        AppThemeMode.dark => 'Dark',
        AppThemeMode.light => 'Light',
        AppThemeMode.pink => 'Pink',
      };

  AppThemeMode get next => switch (this) {
        AppThemeMode.dark => AppThemeMode.light,
        AppThemeMode.light => AppThemeMode.pink,
        AppThemeMode.pink => AppThemeMode.dark,
      };
}

AppThemeMode decodeStoredThemeMode(String? value) => switch (value) {
      'light' || 'ThemeMode.light' => AppThemeMode.light,
      'pink' => AppThemeMode.pink,
      'dark' || 'ThemeMode.dark' => AppThemeMode.dark,
      _ => AppThemeMode.dark,
    };

class AppThemeController extends ValueNotifier<AppThemeMode> {
  AppThemeController({
    Future<String?> Function(String key)? readPreference,
    Future<void> Function(String key, String value)? writePreference,
  })  : _readPreference = readPreference ?? readThemePreference,
        _writePreference = writePreference ?? writeThemePreference,
        super(AppThemeMode.dark);

  static const preferenceKey = 'my_game_hub.theme_mode';

  final Future<String?> Function(String key) _readPreference;
  final Future<void> Function(String key, String value) _writePreference;

  Future<void> initialize() async {
    try {
      value = decodeStoredThemeMode(await _readPreference(preferenceKey));
    } catch (_) {
      value = AppThemeMode.dark;
    }
  }

  void cycleThemeMode() {
    value = value.next;
    unawaited(_persist(value));
  }

  Future<void> _persist(AppThemeMode mode) async {
    try {
      await _writePreference(preferenceKey, mode.storageValue);
    } catch (_) {
      // Theme changes remain available for the current session.
    }
  }
}

final AppThemeController appThemeMode = AppThemeController();
