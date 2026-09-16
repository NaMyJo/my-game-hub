import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/screens/login_screen.dart';

void main() {
  testWidgets('mobile login content scrolls in a short landscape viewport',
      (tester) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(760, 360);
    addTearDown(tester.view.reset);

    await tester.pumpWidget(const MaterialApp(home: LoginScreen()));

    expect(tester.takeException(), isNull);
    final scrollView = find.byKey(const ValueKey('mobile-login-scroll'));
    expect(scrollView, findsOneWidget);
    expect(
      tester.getTopLeft(scrollView).dy,
      greaterThan(tester.getBottomLeft(find.text('MY GAME HUB')).dy),
    );

    final privacyBefore = tester.getTopLeft(find.text('개인정보처리방침')).dy;
    await tester.drag(scrollView, const Offset(0, -220));
    await tester.pumpAndSettle();

    expect(
      tester.getTopLeft(find.text('개인정보처리방침')).dy,
      lessThan(privacyBefore),
    );
    expect(tester.takeException(), isNull);
  });
}
