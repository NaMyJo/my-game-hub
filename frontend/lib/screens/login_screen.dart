import 'package:flutter/material.dart';

import '../services/auth_service.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  bool _loading = false;
  String? _error;

  Future<void> _signIn() async {
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      await AuthService.instance.signInWithGoogle();
    } catch (error) {
      if (!mounted) return;
      setState(() => _error = 'Google 로그인에 실패했습니다.\n$error');
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _signInAnonymously() async {
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      await AuthService.instance.signInAnonymously();
    } catch (error) {
      if (!mounted) return;

      setState(() {
        _error = '게스트 로그인에 실패했습니다.\n$error';
      });
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          const Positioned.fill(child: _LoginBackground()),
          Center(
            child: Container(
              width: 460,
              margin: const EdgeInsets.all(24),
              padding: const EdgeInsets.all(42),
              decoration: BoxDecoration(
                color: const Color(0xE60A1220),
                borderRadius: BorderRadius.circular(24),
                border: Border.all(color: const Color(0xFF202A3A)),
                boxShadow: const [
                  BoxShadow(
                    blurRadius: 80,
                    spreadRadius: 10,
                    color: Color(0x40000000),
                  ),
                ],
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 78,
                    height: 78,
                    decoration: BoxDecoration(
                      color: const Color(0xFF7457FF),
                      borderRadius: BorderRadius.circular(24),
                    ),
                    child: Image.asset(
                      'assets/app_icon/favicon.png',
                      fit: BoxFit.cover,
                    ),
                  ),
                  const SizedBox(height: 26),
                  const Text(
                    'MY GAME HUB',
                    style: TextStyle(
                      fontSize: 29,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 1.2,
                    ),
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    '내 모든 게임 기록을 한 곳에서.',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Color(0xFF9DA9BA),
                      fontSize: 15,
                    ),
                  ),
                  const SizedBox(height: 38),
                  SizedBox(
                    width: double.infinity,
                    height: 54,
                    child: FilledButton(
                      onPressed: _loading ? null : _signIn,
                      style: FilledButton.styleFrom(
                        backgroundColor: Colors.white,
                        foregroundColor: const Color(0xFF10141D),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(14),
                        ),
                      ),
                      child: _loading
                          ? const SizedBox(
                              width: 22,
                              height: 22,
                              child: CircularProgressIndicator(
                                strokeWidth: 2.5,
                                color: Color(0xFF10141D),
                              ),
                            )
                          : const Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Text(
                                  'G',
                                  style: TextStyle(
                                    fontSize: 19,
                                    fontWeight: FontWeight.w900,
                                  ),
                                ),
                                SizedBox(width: 12),
                                Text(
                                  'Google로 계속하기',
                                  style: TextStyle(
                                    fontSize: 16,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                              ],
                            ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    height: 54,
                    child: OutlinedButton.icon(
                      onPressed: _loading ? null : _signInAnonymously,
                      icon: const Icon(
                        Icons.person_outline_rounded,
                        size: 20,
                      ),
                      label: const Text(
                        '로그인 없이 시작하기',
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: const Color(0xFFD7DEE9),
                        side: const BorderSide(
                          color: Color(0xFF35465F),
                        ),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(14),
                        ),
                      ),
                    ),
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 18),
                    Text(
                      _error!,
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: Colors.redAccent),
                    ),
                  ],
                  const SizedBox(height: 24),
                  const Text(
                    '로그인 후 게임 계정을 등록해 대시보드를 구성할 수 있습니다.',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Color(0xFF667386),
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _LoginBackground extends StatelessWidget {
  const _LoginBackground();

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: RadialGradient(
          center: Alignment(0.4, -0.4),
          radius: 1.1,
          colors: [
            Color(0xFF19214A),
            Color(0xFF09101D),
            Color(0xFF040810),
          ],
        ),
      ),
      child: const SizedBox.expand(),
    );
  }
}
