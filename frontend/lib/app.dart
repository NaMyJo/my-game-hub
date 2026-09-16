import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';

import 'screens/account_deletion_page.dart';
import 'screens/dashboard_screen.dart';
import 'screens/login_screen.dart';
import 'screens/privacy_policy_page.dart';
import 'screens/public_pages.dart';
import 'theme/app_theme_controller.dart';
import 'theme/app_typography.dart';

ThemeData buildPinkTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: const Color(0xFF9B3F73),
    brightness: Brightness.light,
  ).copyWith(
    primary: const Color(0xFF9B3F73),
    onPrimary: const Color(0xFFFFF8FC),
    primaryContainer: const Color(0xFFF4CFE2),
    onPrimaryContainer: const Color(0xFF3F1730),
    secondary: const Color(0xFF75539B),
    onSecondary: const Color(0xFFFFFBFF),
    secondaryContainer: const Color(0xFFE9DCF5),
    onSecondaryContainer: const Color(0xFF342344),
    tertiary: const Color(0xFF7655A6),
    onTertiary: const Color(0xFFFFFBFF),
    surface: const Color(0xFFFFF5FA),
    onSurface: const Color(0xFF35232F),
    onSurfaceVariant: const Color(0xFF705A68),
    outline: const Color(0xFF997286),
    outlineVariant: const Color(0xFFE7C4D5),
    surfaceContainerLowest: const Color(0xFFFFFBFD),
    surfaceContainerLow: const Color(0xFFFFF3F8),
    surfaceContainer: const Color(0xFFFBE7F1),
    surfaceContainerHigh: const Color(0xFFF6D9E8),
    surfaceContainerHighest: const Color(0xFFEFCBDD),
  );

  return ThemeData(
    brightness: Brightness.light,
    scaffoldBackgroundColor: const Color(0xFFFDEFF6),
    colorScheme: scheme,
    fontFamily: AppTypography.body,
    textTheme: AppTypography.textTheme(const Color(0xFF35232F)),
    cardColor: const Color(0xFFFFF5FA),
    dividerColor: const Color(0xFFE7C4D5),
    dialogTheme: const DialogThemeData(
      backgroundColor: Color(0xFFFFF5FA),
      surfaceTintColor: Colors.transparent,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: const Color(0xFFFFFBFD),
      enabledBorder: OutlineInputBorder(
        borderSide: const BorderSide(color: Color(0xFFE2B9CD)),
        borderRadius: BorderRadius.circular(14),
      ),
      focusedBorder: OutlineInputBorder(
        borderSide: const BorderSide(color: Color(0xFF9B3F73), width: 1.4),
        borderRadius: BorderRadius.circular(14),
      ),
    ),
    chipTheme: ChipThemeData(
      backgroundColor: const Color(0xFFF6DDE9),
      selectedColor: const Color(0xFFEFC6DB),
      disabledColor: const Color(0xFFE9DCE3),
      side: const BorderSide(color: Color(0xFFE2B9CD)),
      labelStyle: const TextStyle(color: Color(0xFF503743)),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
    ),
    sliderTheme: const SliderThemeData(
      activeTrackColor: Color(0xFF9B3F73),
      inactiveTrackColor: Color(0xFFE6C5D5),
      thumbColor: Color(0xFFB04F83),
    ),
    useMaterial3: true,
  );
}

class MyGameHubApp extends StatelessWidget {
  const MyGameHubApp({super.key});

  @override
  Widget build(BuildContext context) {
    final lightTheme = ThemeData(
      brightness: Brightness.light,
      scaffoldBackgroundColor: const Color(0xFFF4F6FA),
      colorScheme: ColorScheme.fromSeed(
        seedColor: const Color(0xFF6750D8),
        brightness: Brightness.light,
      ),
      fontFamily: AppTypography.body,
      textTheme: AppTypography.textTheme(const Color(0xFF202636)),
      useMaterial3: true,
    );
    final darkTheme = ThemeData(
      brightness: Brightness.dark,
      scaffoldBackgroundColor: const Color(0xFF050A13),
      colorScheme: ColorScheme.fromSeed(
        seedColor: const Color(0xFF7C5CFF),
        brightness: Brightness.dark,
      ),
      fontFamily: AppTypography.body,
      textTheme: AppTypography.textTheme(const Color(0xFFF4F1FF)),
      useMaterial3: true,
    );
    final pinkTheme = buildPinkTheme();

    return ValueListenableBuilder<AppThemeMode>(
      valueListenable: appThemeMode,
      builder: (context, themeMode, _) => MaterialApp(
        title: 'My Game Hub',
        debugShowCheckedModeBanner: false,
        themeMode:
            themeMode == AppThemeMode.dark ? ThemeMode.dark : ThemeMode.light,
        themeAnimationDuration: const Duration(milliseconds: 140),
        themeAnimationCurve: Curves.easeOutCubic,
        theme: themeMode == AppThemeMode.pink ? pinkTheme : lightTheme,
        darkTheme: darkTheme,
        builder: (context, child) {
          if (child == null) {
            return const SizedBox.shrink();
          }

          return child;
        },
        onGenerateRoute: generateAppRoute,
        home: const AuthGate(),
      ),
    );
  }
}

Route<dynamic>? generateAppRoute(RouteSettings settings) {
  final uri = Uri.parse(settings.name ?? '/');
  if (uri.path == PrivacyPolicyPage.path) {
    return MaterialPageRoute<void>(
      settings: settings,
      builder: (_) => const PrivacyPolicyPage(),
    );
  }
  if (uri.path == AccountDeletionPage.path) {
    return MaterialPageRoute<void>(
      settings: settings,
      builder: (_) => const AccountDeletionPage(),
    );
  }
  if (uri.pathSegments.length == 2 && uri.pathSegments.first == 'profile') {
    return MaterialPageRoute<void>(
      settings: settings,
      builder: (_) => PublicProfilePage(
        publicId: uri.pathSegments[1],
      ),
    );
  }
  if (uri.pathSegments.length == 2 && uri.pathSegments.first == 'identity') {
    return MaterialPageRoute<void>(
      settings: settings,
      builder: (_) => SharedIdentityPage(
        shareId: uri.pathSegments[1],
      ),
    );
  }
  return null;
}

class AuthGate extends StatelessWidget {
  const AuthGate({super.key});

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<User?>(
      stream: FirebaseAuth.instance.authStateChanges(),
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Scaffold(
            body: Center(child: CircularProgressIndicator()),
          );
        }

        if (snapshot.data == null) {
          return const LoginScreen();
        }

        return const DashboardScreen();
      },
    );
  }
}
