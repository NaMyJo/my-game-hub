import 'package:flutter/material.dart';

import '../services/api_client.dart';

class AccountDeletionDialog extends StatefulWidget {
  const AccountDeletionDialog({
    super.key,
    required this.onConfirm,
  });

  final Future<void> Function() onConfirm;

  @override
  State<AccountDeletionDialog> createState() => _AccountDeletionDialogState();
}

class _AccountDeletionDialogState extends State<AccountDeletionDialog> {
  bool _deleting = false;
  String? _errorMessage;

  Future<void> _confirm() async {
    if (_deleting) return;

    setState(() {
      _deleting = true;
      _errorMessage = null;
    });

    try {
      await widget.onConfirm();
      if (mounted) {
        Navigator.of(context).pop(true);
      }
    } catch (error) {
      if (!mounted) return;

      final requiresRecentLogin =
          error is ApiException && error.statusCode == 409;
      setState(() {
        _deleting = false;
        _errorMessage = requiresRecentLogin
            ? '보안을 위해 다시 로그인한 뒤 계정 삭제를 시도해주세요.'
            : '계정 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final neutralColor =
        isDark ? const Color(0xFFC4B8FF) : const Color(0xFF5D4BB3);

    return PopScope(
      canPop: !_deleting,
      child: AlertDialog(
        backgroundColor: isDark ? const Color(0xFF0C1624) : Colors.white,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: BorderSide(
            color: isDark ? const Color(0xFF2A374C) : const Color(0xFFD8DEE8),
          ),
        ),
        title: Text(
          '계정을 삭제하시겠습니까?',
          style: TextStyle(
            fontWeight: FontWeight.w800,
            color: isDark ? Colors.white : const Color(0xFF202636),
          ),
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '계정을 삭제하면 저장된 프로필과 게임 데이터가\n'
              '모두 삭제되며 복구할 수 없습니다.\n\n'
              '계정 삭제 후 자동으로 로그아웃됩니다.',
              style: TextStyle(
                color:
                    isDark ? const Color(0xFFAEB9C8) : const Color(0xFF596579),
                height: 1.5,
              ),
            ),
            if (_errorMessage != null) ...[
              const SizedBox(height: 16),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: const Color(0x1FFF5B6E),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: const Color(0x66FF5B6E)),
                ),
                child: Text(
                  _errorMessage!,
                  style: const TextStyle(
                    color: Color(0xFFFF8A98),
                    fontSize: 13,
                    height: 1.4,
                  ),
                ),
              ),
            ],
          ],
        ),
        actions: [
          OutlinedButton(
            autofocus: true,
            onPressed:
                _deleting ? null : () => Navigator.of(context).pop(false),
            style: OutlinedButton.styleFrom(
              foregroundColor: neutralColor,
              side: BorderSide(color: neutralColor.withValues(alpha: 0.65)),
            ),
            child: const Text('아니오'),
          ),
          FilledButton(
            key: const ValueKey('confirm-account-deletion'),
            onPressed: _deleting ? null : _confirm,
            style: FilledButton.styleFrom(
              foregroundColor: Colors.white,
              backgroundColor: const Color(0xFFD7485C),
              disabledBackgroundColor: const Color(0xFF6D3943),
            ),
            child: _deleting
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Text('네'),
          ),
        ],
      ),
    );
  }
}
