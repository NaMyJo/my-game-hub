import 'package:flutter/material.dart';

import '../theme/app_typography.dart';

class IconPageHeader extends StatelessWidget {
  const IconPageHeader({
    super.key,
    required this.icon,
    required this.title,
  });

  final IconData icon;
  final String title;

  @override
  Widget build(BuildContext context) => Row(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: const Color(0xFF765EFF).withValues(alpha: .14),
              borderRadius: BorderRadius.circular(13),
            ),
            child: Icon(icon, color: const Color(0xFF806AFF), size: 22),
          ),
          const SizedBox(width: 13),
          Text(
            title,
            style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                  fontFamily: AppTypography.display,
                  fontWeight: FontWeight.w800,
                  letterSpacing: -.4,
                ),
          ),
        ],
      );
}
