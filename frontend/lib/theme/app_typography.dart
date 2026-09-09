import 'package:flutter/material.dart';

abstract final class AppTypography {
  static const String display = 'Paperlogy';
  static const String body = 'Pretendard';
  static const String numeric = 'IBM Plex Sans KR';

  static TextTheme textTheme(Color color) => TextTheme(
        displayLarge: TextStyle(fontFamily: display, color: color),
        displayMedium: TextStyle(fontFamily: display, color: color),
        headlineLarge: TextStyle(fontFamily: display, color: color),
        headlineMedium: TextStyle(fontFamily: display, color: color),
        headlineSmall: TextStyle(fontFamily: display, color: color),
        titleLarge: TextStyle(fontFamily: display, color: color),
        bodyLarge: TextStyle(fontFamily: body, color: color),
        bodyMedium: TextStyle(fontFamily: body, color: color),
        bodySmall: TextStyle(fontFamily: body, color: color),
        labelLarge: TextStyle(fontFamily: body, color: color),
        labelMedium: TextStyle(fontFamily: body, color: color),
        labelSmall: TextStyle(fontFamily: body, color: color),
      );

  static const TextStyle numericStyle = TextStyle(fontFamily: numeric);
}
