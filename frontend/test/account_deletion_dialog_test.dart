import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/services/api_client.dart';
import 'package:my_game_hub/widgets/account_deletion_dialog.dart';

void main() {
  testWidgets('cancel closes without deleting', (tester) async {
    var deleteCalls = 0;
    await _openDialog(
      tester,
      onConfirm: () async => deleteCalls++,
    );

    await tester.tap(find.text('아니오'));
    await tester.pumpAndSettle();

    expect(deleteCalls, 0);
    expect(find.byType(AccountDeletionDialog), findsNothing);
  });

  testWidgets('confirm shows loading and prevents duplicate deletion',
      (tester) async {
    final completer = Completer<void>();
    var deleteCalls = 0;
    await _openDialog(
      tester,
      onConfirm: () {
        deleteCalls++;
        return completer.future;
      },
    );

    await tester.tap(find.byKey(const ValueKey('confirm-account-deletion')));
    await tester.pump();

    expect(deleteCalls, 1);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    await tester.tap(
      find.byKey(const ValueKey('confirm-account-deletion')),
      warnIfMissed: false,
    );
    await tester.pump();
    expect(deleteCalls, 1);

    completer.complete();
    await tester.pumpAndSettle();
    expect(find.byType(AccountDeletionDialog), findsNothing);
  });

  testWidgets('recent login failure keeps dialog open with safe guidance',
      (tester) async {
    await _openDialog(
      tester,
      onConfirm: () async {
        throw const ApiException('server detail', statusCode: 409);
      },
    );

    await tester.tap(find.byKey(const ValueKey('confirm-account-deletion')));
    await tester.pumpAndSettle();

    expect(find.byType(AccountDeletionDialog), findsOneWidget);
    expect(find.textContaining('다시 로그인한 뒤'), findsOneWidget);
    expect(find.textContaining('server detail'), findsNothing);
  });
}

Future<void> _openDialog(
  WidgetTester tester, {
  required Future<void> Function() onConfirm,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      theme: ThemeData.dark(),
      home: Builder(
        builder: (context) => Scaffold(
          body: TextButton(
            onPressed: () => showDialog<bool>(
              context: context,
              barrierDismissible: false,
              builder: (_) => AccountDeletionDialog(onConfirm: onConfirm),
            ),
            child: const Text('열기'),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('열기'));
  await tester.pumpAndSettle();
}
