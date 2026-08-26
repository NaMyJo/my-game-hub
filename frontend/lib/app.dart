import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';

import 'screens/dashboard_screen.dart';
import 'screens/login_screen.dart';
import 'theme/app_theme_controller.dart';

class MyGameHubApp extends StatelessWidget {
  const MyGameHubApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<ThemeMode>(
      valueListenable: appThemeMode,
      builder: (context, themeMode, _) => MaterialApp(
        title: 'My Game Hub',
        debugShowCheckedModeBanner: false,
        themeMode: themeMode,
        themeAnimationDuration: const Duration(milliseconds: 140),
        themeAnimationCurve: Curves.easeOutCubic,
        theme: ThemeData(
          brightness: Brightness.light,
          scaffoldBackgroundColor: const Color(0xFFF4F6FA),
          colorScheme: ColorScheme.fromSeed(
            seedColor: const Color(0xFF6750D8),
            brightness: Brightness.light,
          ),
          fontFamily: 'sans-serif',
          useMaterial3: true,
        ),
        darkTheme: ThemeData(
          brightness: Brightness.dark,
          scaffoldBackgroundColor: const Color(0xFF050A13),
          colorScheme: ColorScheme.fromSeed(
            seedColor: const Color(0xFF7C5CFF),
            brightness: Brightness.dark,
          ),
          fontFamily: 'sans-serif',
          useMaterial3: true,
        ),
        home: const AuthGate(),
      ),
    );
  }
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
