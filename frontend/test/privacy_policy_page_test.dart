import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/screens/privacy_policy_page.dart';

void main() {
  testWidgets('privacy policy renders its public policy essentials',
      (tester) async {
    await tester.pumpWidget(
      const MaterialApp(home: PrivacyPolicyPage()),
    );

    expect(find.text('MY GAME HUB 개인정보처리방침'), findsOneWidget);
    expect(find.textContaining('Google 비밀번호'), findsOneWidget);
    expect(find.textContaining('Vercel Analytics'), findsOneWidget);
    expect(find.textContaining('개인 Steam 라이브러리'), findsOneWidget);
    expect(find.textContaining('audwhd1113@gmail.com'), findsOneWidget);
    expect(find.text('계정 삭제 안내'), findsOneWidget);
    expect(find.text('시행일: 2026년 9월 13일'), findsOneWidget);
    expect(find.byType(SingleChildScrollView), findsOneWidget);
  });
}
