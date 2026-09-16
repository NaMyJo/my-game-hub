import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/models/game_profile.dart';
import 'package:my_game_hub/screens/dashboard_screen.dart';
import 'package:my_game_hub/theme/app_theme_controller.dart';
import 'package:my_game_hub/widgets/game_card.dart';

void main() {
  const profile = GameProfile(
    id: 1,
    type: GameType.tft,
    accountName: '테스트 계정',
    primaryLabel: '티어',
    primaryValue: 'GOLD I',
  );

  Future<void> pumpGameCard(
    WidgetTester tester, {
    required bool mobile,
    required VoidCallback onRefresh,
    required VoidCallback onRemove,
  }) {
    return tester.pumpWidget(
      MaterialApp(
        theme: ThemeData.dark(),
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: mobile ? 161 : 420,
              child: GameCard(
                profile: profile,
                isRefreshing: false,
                onRefresh: onRefresh,
                onRemove: onRemove,
                mobile: mobile,
              ),
            ),
          ),
        ),
      ),
    );
  }

  testWidgets('mobile game card omits game name and keeps both actions',
      (tester) async {
    var refreshCount = 0;
    var removeCount = 0;
    await pumpGameCard(
      tester,
      mobile: true,
      onRefresh: () => refreshCount++,
      onRemove: () => removeCount++,
    );

    expect(find.text(GameType.tft.displayName), findsNothing);
    expect(find.byType(Image), findsOneWidget);
    expect(find.byIcon(Icons.refresh), findsOneWidget);
    expect(find.byIcon(Icons.delete_outline_rounded), findsOneWidget);

    await tester.tap(find.byIcon(Icons.refresh));
    await tester.tap(find.byIcon(Icons.delete_outline_rounded));

    expect(refreshCount, 1);
    expect(removeCount, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('desktop game card keeps game name', (tester) async {
    await pumpGameCard(
      tester,
      mobile: false,
      onRefresh: () {},
      onRemove: () {},
    );

    expect(find.text(GameType.tft.displayName), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  for (final mode in AppThemeMode.values) {
    testWidgets('mobile mode button presents and cycles from ${mode.name}',
        (tester) async {
      var currentMode = mode;
      await tester.pumpWidget(
        MaterialApp(
          theme: ThemeData.light(),
          home: Scaffold(
            body: StatefulBuilder(
              builder: (context, setState) => SizedBox(
                width: 288,
                child: MobileThemeModeButton(
                  themeMode: currentMode,
                  onTap: () => setState(() {
                    currentMode = currentMode.next;
                  }),
                ),
              ),
            ),
          ),
        ),
      );

      expect(find.text(mode.displayName), findsOneWidget);
      expect(find.textContaining('Dark · Light · Pink'), findsOneWidget);

      await tester.tap(
        find.byKey(const ValueKey('mobile-theme-mode-button')),
      );
      await tester.pump();

      expect(currentMode, mode.next);
      expect(find.text(mode.next.displayName), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('Google profile avatar enables the web image fallback strategy',
      (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GoogleProfileAvatar(
            radius: 38,
            displayName: '남명종',
            photoUrl: 'https://lh3.googleusercontent.com/example',
          ),
        ),
      ),
    );

    final image = tester.widget<Image>(
      find.byKey(const ValueKey('google-profile-image')),
    );
    final provider = image.image as NetworkImage;

    expect(provider.webHtmlElementStrategy, WebHtmlElementStrategy.fallback);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Google profile avatar has a visible fallback in every theme',
      (tester) async {
    for (final theme in [
      ThemeData.dark(),
      ThemeData.light(),
      ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF9B3F73),
        ),
      ),
    ]) {
      await tester.pumpWidget(
        MaterialApp(
          theme: theme,
          home: const Scaffold(
            body: GoogleProfileAvatar(
              radius: 38,
              displayName: '남명종',
              photoUrl: null,
            ),
          ),
        ),
      );

      expect(find.text('명종'), findsOneWidget);
      expect(tester.takeException(), isNull);
    }
  });
}
