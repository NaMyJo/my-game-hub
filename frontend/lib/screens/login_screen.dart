import 'package:flutter/material.dart';

import '../services/auth_service.dart';
import '../theme/app_typography.dart';
import 'dashboard_screen.dart';

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

  Future<void> _signInAnonymously({DashboardPage? destination}) async {
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      pendingDashboardPage = destination ?? DashboardPage.dashboard;
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
    if (MediaQuery.sizeOf(context).width >= 900) {
      return _WebLanding(
        loading: _loading,
        error: _error,
        onGoogle: _signIn,
        onGuest: () => _signInAnonymously(),
        onTools: () => _signInAnonymously(destination: DashboardPage.tools),
        onFinder: () =>
            _signInAnonymously(destination: DashboardPage.gameFinder),
      );
    }
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
                    '내 게임 관리 및\n취향 게임 찾기',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 26,
                      height: 1.25,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    '흘어진 게임 계정을 관리하고\n내 취향에 맞는 게임을 발견해보세요.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Color(0xFF9DA9BA), fontSize: 14),
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
                      onPressed: _loading ? null : () => _signInAnonymously(),
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
                  const SizedBox(height: 10),
                  TextButton(
                    onPressed: () =>
                        Navigator.of(context).pushNamed('/privacy'),
                    child: const Text(
                      '개인정보처리방침',
                      style: TextStyle(fontSize: 12),
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

class _WebLanding extends StatelessWidget {
  const _WebLanding({
    required this.loading,
    required this.error,
    required this.onGoogle,
    required this.onGuest,
    required this.onTools,
    required this.onFinder,
  });

  final bool loading;
  final String? error;
  final VoidCallback onGoogle;
  final VoidCallback onGuest;
  final VoidCallback onTools;
  final VoidCallback onFinder;

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: const Color(0xFF050A13),
        body: Stack(children: [
          const Positioned.fill(child: _LoginBackground()),
          Positioned.fill(
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Colors.black.withValues(alpha: .08),
                    Colors.black.withValues(alpha: .42)
                  ],
                ),
              ),
            ),
          ),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 22),
              child: Column(children: [
                Row(children: [
                  InkWell(
                    onTap: () {},
                    borderRadius: BorderRadius.circular(12),
                    child: Row(children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(9),
                        child: Image.asset('assets/app_icon/favicon.png',
                            width: 34, height: 34),
                      ),
                      const SizedBox(width: 11),
                      const Text('MY GAME HUB',
                          style: TextStyle(
                              fontWeight: FontWeight.w800, fontSize: 17)),
                    ]),
                  ),
                  const SizedBox(width: 42),
                  TextButton.icon(
                      onPressed: loading ? null : onTools,
                      icon: const Icon(Icons.handyman_outlined, size: 17),
                      label: const Text('도구 모음')),
                  const SizedBox(width: 8),
                  TextButton.icon(
                      onPressed: loading ? null : onFinder,
                      icon: const Icon(Icons.explore_outlined, size: 17),
                      label: const Text('GAME FINDER')),
                  const Spacer(),
                  TextButton(
                      onPressed: loading ? null : onGoogle,
                      child: const Text('Google 로그인')),
                ]),
                Expanded(
                  child: LayoutBuilder(
                    builder: (context, viewport) => SingleChildScrollView(
                      child: ConstrainedBox(
                        constraints:
                            BoxConstraints(minHeight: viewport.maxHeight),
                        child: Center(
                          child: ConstrainedBox(
                            constraints: const BoxConstraints(maxWidth: 920),
                            child: Padding(
                              padding: const EdgeInsets.symmetric(vertical: 36),
                              child: Column(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Container(
                                      padding: const EdgeInsets.symmetric(
                                          horizontal: 14, vertical: 8),
                                      decoration: BoxDecoration(
                                          color: const Color(0xFF765EFF)
                                              .withValues(alpha: .16),
                                          borderRadius:
                                              BorderRadius.circular(30),
                                          border: Border.all(
                                              color: const Color(0xFF826DFF)
                                                  .withValues(alpha: .4))),
                                      child: const Text('YOUR GAMES, ONE PLACE',
                                          style: TextStyle(
                                              color: Color(0xFFB7AAFF),
                                              fontSize: 12,
                                              fontWeight: FontWeight.w700,
                                              letterSpacing: 1.4)),
                                    ),
                                    const SizedBox(height: 24),
                                    Text('내 게임 관리 및\n취향 게임 찾기',
                                        textAlign: TextAlign.center,
                                        style: Theme.of(context)
                                            .textTheme
                                            .displayMedium
                                            ?.copyWith(
                                                fontFamily:
                                                    AppTypography.display,
                                                fontSize: 58,
                                                height: 1.12,
                                                fontWeight: FontWeight.w800,
                                                color: Colors.white)),
                                    const SizedBox(height: 20),
                                    const Text(
                                        '흩어진 게임 계정을 한곳에서 관리하고,\n내 취향에 맞는 새로운 게임을 발견해보세요.',
                                        textAlign: TextAlign.center,
                                        style: TextStyle(
                                            fontSize: 16,
                                            height: 1.65,
                                            color: Color(0xFFA9B4C6))),
                                    const SizedBox(height: 34),
                                    Wrap(
                                        spacing: 12,
                                        runSpacing: 12,
                                        alignment: WrapAlignment.center,
                                        children: [
                                          OutlinedButton.icon(
                                              onPressed:
                                                  loading ? null : onGuest,
                                              icon: const Icon(
                                                  Icons.arrow_forward_rounded),
                                              label: const Text('로그인 없이 시작하기'),
                                              style: OutlinedButton.styleFrom(
                                                  minimumSize:
                                                      const Size(190, 54),
                                                  foregroundColor: Colors.white,
                                                  side: const BorderSide(
                                                      color: Color(0xFF59677C)),
                                                  shape: RoundedRectangleBorder(
                                                      borderRadius:
                                                          BorderRadius.circular(
                                                              14)))),
                                          FilledButton.icon(
                                              onPressed:
                                                  loading ? null : onGoogle,
                                              icon: loading
                                                  ? const SizedBox.square(
                                                      dimension: 18,
                                                      child:
                                                          CircularProgressIndicator(
                                                              strokeWidth: 2))
                                                  : const Icon(
                                                      Icons.login_rounded),
                                              label: const Text(
                                                  'Google 로그인으로 시작하기'),
                                              style: FilledButton.styleFrom(
                                                  minimumSize:
                                                      const Size(230, 54),
                                                  backgroundColor:
                                                      const Color(0xFF765EFF),
                                                  shape: RoundedRectangleBorder(
                                                      borderRadius:
                                                          BorderRadius.circular(
                                                              14)))),
                                        ]),
                                    if (error != null) ...[
                                      const SizedBox(height: 18),
                                      Text(error!,
                                          textAlign: TextAlign.center,
                                          style: const TextStyle(
                                              color: Colors.redAccent))
                                    ],
                                  ]),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
                Wrap(
                  spacing: 12,
                  runSpacing: 4,
                  alignment: WrapAlignment.center,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    const Text('MY GAME HUB · 게임 기록과 취향 탐색을 위한 개인 허브',
                        style:
                            TextStyle(color: Color(0xFF68758A), fontSize: 12)),
                    TextButton(
                      style: TextButton.styleFrom(
                        visualDensity: VisualDensity.compact,
                        foregroundColor: const Color(0xFF8F9BAE),
                      ),
                      onPressed: () =>
                          Navigator.of(context).pushNamed('/privacy'),
                      child: const Text('개인정보처리방침',
                          style: TextStyle(fontSize: 12)),
                    ),
                  ],
                ),
              ]),
            ),
          ),
        ]),
      );
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
