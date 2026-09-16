import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/app.dart';
import 'package:my_game_hub/theme/app_theme_controller.dart';

void main() {
  test('missing or unknown preference defaults to Dark', () {
    expect(decodeStoredThemeMode(null), AppThemeMode.dark);
    expect(decodeStoredThemeMode('unknown'), AppThemeMode.dark);
  });

  test('stored Dark, Light, and Pink values are restored', () {
    expect(decodeStoredThemeMode('dark'), AppThemeMode.dark);
    expect(decodeStoredThemeMode('light'), AppThemeMode.light);
    expect(decodeStoredThemeMode('pink'), AppThemeMode.pink);
  });

  test('legacy ThemeMode values remain compatible', () {
    expect(decodeStoredThemeMode('ThemeMode.dark'), AppThemeMode.dark);
    expect(decodeStoredThemeMode('ThemeMode.light'), AppThemeMode.light);
  });

  test('theme cycle is Dark to Light to Pink to Dark', () {
    expect(AppThemeMode.dark.next, AppThemeMode.light);
    expect(AppThemeMode.light.next, AppThemeMode.pink);
    expect(AppThemeMode.pink.next, AppThemeMode.dark);
  });

  test('controller restores and persists the next mode', () async {
    String? stored = 'pink';
    final controller = AppThemeController(
      readPreference: (_) async => stored,
      writePreference: (_, value) async => stored = value,
    );

    await controller.initialize();
    expect(controller.value, AppThemeMode.pink);

    controller.cycleThemeMode();
    await Future<void>.delayed(Duration.zero);

    expect(controller.value, AppThemeMode.dark);
    expect(stored, 'dark');
  });

  test('Pink ThemeData uses a distinct blush and rose color scheme', () {
    final theme = buildPinkTheme();

    expect(theme.brightness, Brightness.light);
    expect(theme.scaffoldBackgroundColor, const Color(0xFFFDEFF6));
    expect(theme.colorScheme.primary, const Color(0xFF9B3F73));
    expect(theme.colorScheme.surface, const Color(0xFFFFF5FA));
    expect(theme.colorScheme.outlineVariant, const Color(0xFFE7C4D5));
  });
}
