import 'package:flutter/material.dart';

class AccountDeletionPage extends StatelessWidget {
  const AccountDeletionPage({super.key});

  static const path = '/account-deletion';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF050A13),
      body: SafeArea(
        child: SelectionArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 860),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    InkWell(
                      onTap: () => Navigator.of(context)
                          .pushNamedAndRemoveUntil('/', (route) => false),
                      borderRadius: BorderRadius.circular(12),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(vertical: 6),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            ClipRRect(
                              borderRadius: BorderRadius.circular(9),
                              child: Image.asset(
                                'assets/app_icon/favicon.png',
                                width: 36,
                                height: 36,
                              ),
                            ),
                            const SizedBox(width: 11),
                            const Flexible(
                              child: Text(
                                'MY GAME HUB',
                                style: TextStyle(
                                  color: Colors.white,
                                  fontSize: 18,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 42),
                    const Text(
                      'MY GAME HUB 계정 삭제 안내',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 32,
                        height: 1.25,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    const SizedBox(height: 14),
                    const Text(
                      '계정 삭제 방법과 삭제되는 정보를 안내합니다.',
                      style: TextStyle(
                        color: Color(0xFFAAB5C7),
                        fontSize: 16,
                        height: 1.7,
                      ),
                    ),
                    const SizedBox(height: 34),
                    const _DeletionSection(
                      title: '1. 앱에서 계정을 삭제하는 방법',
                      body: '''모바일
마이페이지 → 계정 삭제 → 확인

웹
왼쪽 Sidebar 사용자 영역 → 계정 삭제 → 확인''',
                    ),
                    const _DeletionSection(
                      title: '2. 계정 삭제 시 삭제되는 정보',
                      body: '''• MY GAME HUB 사용자 계정 정보
• 프로필 정보
• 등록한 게임 계정 정보
• 게임 신분증 및 관련 기록
• Game Finder 사용자 설정 및 최근 기록
• My Game Picks 저장 정보
• Firebase Authentication 계정''',
                    ),
                    const _DeletionSection(
                      title: '3. 삭제되지 않는 공용 데이터',
                      body:
                          'Steam 및 IGDB에서 수집된 게임 catalog 정보는 특정 사용자의 개인정보가 아닌 공용 게임 데이터이므로 계정 삭제 대상에 포함되지 않습니다. 여기에는 게임명, 가격, 장르·태그, 출시일, 플레이 인원 정보, IGDB taxonomy 및 관리자 동기화 상태 등이 포함됩니다.',
                    ),
                    const _DeletionSection(
                      title: '4. 삭제 처리 안내',
                      body:
                          '계정 삭제가 완료되면 삭제된 계정과 사용자 데이터는 복구할 수 없습니다. 계속하기 전에 필요한 정보를 확인해 주세요.',
                    ),
                    const _DeletionSection(
                      title: '5. 로그인할 수 없는 경우',
                      body:
                          '앱에 로그인할 수 없어 계정 삭제가 필요한 경우 audwhd1113@gmail.com 으로 문의해 주세요. 이메일로 비밀번호, Firebase UID 또는 인증 토큰을 보내지 마세요.',
                    ),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(18),
                      decoration: BoxDecoration(
                        color: const Color(0xFF0C1524),
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: const Color(0xFF27354A)),
                      ),
                      child: const Text(
                        '계정 삭제는 되돌릴 수 없습니다.',
                        style: TextStyle(
                          color: Color(0xFFD7DEE9),
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    const SizedBox(height: 36),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _DeletionSection extends StatelessWidget {
  const _DeletionSection({required this.title, required this.body});

  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 30),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(
              color: Color(0xFF9B8CFF),
              fontSize: 20,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 11),
          Text(
            body,
            style: const TextStyle(
              color: Color(0xFFD7DEE9),
              fontSize: 15,
              height: 1.75,
            ),
          ),
        ],
      ),
    );
  }
}
