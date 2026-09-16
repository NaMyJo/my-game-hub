import 'package:flutter/material.dart';

import 'account_deletion_page.dart';

class PrivacyPolicyPage extends StatelessWidget {
  const PrivacyPolicyPage({super.key});

  static const path = '/privacy';

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
                            const Text(
                              'MY GAME HUB',
                              style: TextStyle(
                                color: Colors.white,
                                fontSize: 18,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 42),
                    const Text(
                      'MY GAME HUB 개인정보처리방침',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 32,
                        height: 1.25,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    const SizedBox(height: 14),
                    const Text(
                      'MY GAME HUB는 서비스 제공에 필요한 최소한의 개인정보만 처리합니다.',
                      style: TextStyle(
                        color: Color(0xFFAAB5C7),
                        fontSize: 16,
                        height: 1.7,
                      ),
                    ),
                    const SizedBox(height: 34),
                    const _PolicySection(
                      title: '1. 수집하는 개인정보',
                      body: '''Google 로그인 사용 시 다음 정보를 처리할 수 있습니다.
• Google 계정 이메일 주소
• Google 계정의 공개 프로필 정보(이름 및 프로필 사진)
• 사용자 식별용 Firebase UID

서비스 이용 과정에서 다음 정보가 저장될 수 있습니다.
• 사용자가 등록한 게임 계정 및 캐릭터 정보
• 생성·저장한 게임 신분증 정보와 재구성 가능한 스냅샷
• Game Finder에서 선택한 게임과 선호 태그
• 가격, 플레이 인원, 성인 콘텐츠 및 출시 선호 등 추천 설정''',
                    ),
                    const _PolicySection(
                      title: '2. 개인정보 이용 목적',
                      body: '''수집한 정보는 다음 목적으로 이용합니다.
• 사용자 인증 및 로그인 상태 유지
• 사용자별 게임 계정 관리와 전적 정보 제공
• 게임 신분증 생성·저장 및 공개 설정 기능 제공
• 사용자 취향 기반 게임 검색과 추천
• 서비스 기능 제공, 장애 확인 및 오류 대응''',
                    ),
                    const _PolicySection(
                      title: '3. Google 로그인',
                      body:
                          'MY GAME HUB는 Google 계정 로그인을 위해 Google 및 Firebase Authentication을 사용합니다. Google 비밀번호는 MY GAME HUB가 직접 수집하거나 저장하지 않으며, Google 로그인 인증 결과와 Firebase UID를 이용해 사용자를 식별합니다.',
                    ),
                    const _PolicySection(
                      title: '4. 개인정보 보관 및 삭제',
                      body:
                          '''사용자는 앱 내 계정 삭제 기능을 이용해 계정을 삭제할 수 있습니다. 자세한 방법은 https://www.mygamehub.kr/account-deletion 에서 확인할 수 있습니다.

계정 삭제 처리가 성공하면 MY GAME HUB 사용자 계정과 프로필, 등록한 게임 계정, 게임 신분증 및 관련 기록, Game Finder 사용자 설정과 최근 기록, My Game Picks 저장 정보가 활성 서비스 데이터베이스에서 삭제되고 Firebase Authentication 계정도 삭제됩니다. 이 처리는 되돌릴 수 없습니다.

Steam/IGDB 기반 공용 game catalog는 특정 사용자의 계정 데이터가 아니므로 삭제 대상이 아닙니다. 서비스 및 인프라 운영 과정에서 생성되는 서버·호스팅 로그의 구체적인 보유 기간은 코드에서 확인되지 않으며, 관련 제공자의 운영 설정과 정책에 따를 수 있습니다.''',
                    ),
                    const _PolicySection(
                      title: '5. 제3자 제공 및 외부 처리 서비스',
                      body:
                          'MY GAME HUB는 개인정보를 판매하거나 이용 목적과 무관하게 임의로 제3자에게 제공하지 않습니다. 다만 서비스 제공 과정에서 Google/Firebase Authentication은 인증을, Vercel과 Render는 서비스 호스팅을, Neon은 데이터베이스 인프라를 제공합니다. 사용자가 등록한 게임 계정의 정보를 조회할 때는 해당 게임의 공식 또는 외부 게임 API가 계정 식별 정보를 처리할 수 있습니다. Game Finder는 Steam 및 IGDB에서 제공되는 공용 게임 catalog 정보를 사용하며, 사용자의 개인 Steam 라이브러리를 수집하는 기능은 제공하지 않습니다. 각 업체는 해당 서비스 제공에 필요한 범위에서 정보를 처리할 수 있습니다.',
                    ),
                    const _PolicySection(
                      title: '6. 쿠키, 브라우저 저장소 및 분석',
                      body:
                          'Flutter Web에서 로그인 상태를 유지하기 위해 Firebase Authentication이 브라우저 저장소 또는 쿠키 등 브라우저가 제공하는 저장 수단을 사용할 수 있습니다. 브라우저 설정이나 저장 데이터 삭제 시 로그인 상태가 해제될 수 있습니다. 웹 서비스 품질과 성능 확인을 위해 Vercel Analytics 및 Speed Insights 스크립트를 사용합니다. 해당 서비스가 처리하는 구체적인 항목과 보유 기간은 Vercel의 운영 설정 및 정책에 따를 수 있습니다. MY GAME HUB는 현재 자체 맞춤형 광고 SDK를 사용하지 않습니다.',
                    ),
                    const _PolicySection(
                      title: '7. 사용자의 권리',
                      body: '''사용자는 다음 사항을 요청할 수 있습니다.
• 본인의 개인정보 확인
• 부정확한 개인정보 수정
• 개인정보 또는 등록 정보 삭제
• 서비스 이용 중단

요청은 아래 문의 이메일로 접수할 수 있습니다.''',
                    ),
                    const _PolicySection(
                      title: '8. 문의처',
                      body: '개인정보처리방침 및 개인정보 관련 문의: audwhd1113@gmail.com',
                    ),
                    TextButton.icon(
                      onPressed: () => Navigator.of(context)
                          .pushNamed(AccountDeletionPage.path),
                      icon: const Icon(Icons.person_remove_outlined),
                      label: const Text('계정 삭제 안내'),
                      style: TextButton.styleFrom(
                        foregroundColor: const Color(0xFFC5BAFF),
                        padding: const EdgeInsets.symmetric(
                          horizontal: 4,
                          vertical: 12,
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    Container(
                      width: double.infinity,
                      margin: const EdgeInsets.only(top: 6),
                      padding: const EdgeInsets.all(18),
                      decoration: BoxDecoration(
                        color: const Color(0xFF0C1524),
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: const Color(0xFF27354A)),
                      ),
                      child: const Text(
                        '시행일: 2026년 9월 13일',
                        style: TextStyle(
                          color: Color(0xFFB8C2D1),
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

class _PolicySection extends StatelessWidget {
  const _PolicySection({required this.title, required this.body});

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
