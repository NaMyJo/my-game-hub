import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/app.dart';
import 'package:my_game_hub/screens/privacy_policy_page.dart';

void main() {
  test('privacy policy route is generated without initializing Firebase', () {
    final route = generateAppRoute(
      const RouteSettings(name: PrivacyPolicyPage.path),
    );

    expect(route, isA<MaterialPageRoute<void>>());
  });
}
