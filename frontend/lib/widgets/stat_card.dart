import 'package:flutter/material.dart';

class StatCard extends StatelessWidget {
  const StatCard({
    super.key,
    this.icon,
    this.imageAsset,
    required this.label,
    required this.value,
    required this.caption,
    this.embedded = false,
  });

  final IconData? icon;
  final String? imageAsset;

  final String label;
  final String value;
  final String caption;
  final bool embedded;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: embedded
            ? Colors.transparent
            : (isDark ? const Color(0xFF091322) : Colors.white),
        borderRadius: embedded ? null : BorderRadius.circular(18),
        border: Border.all(
          color: isDark ? const Color(0xFF1A293C) : const Color(0xFFDDE3EC),
        ),
      ),
      child: Row(
        children: [
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              color: const Color(0xFF7657FF).withValues(alpha: 0.18),
              borderRadius: BorderRadius.circular(14),
            ),
            child: imageAsset != null
                ? Padding(
                    padding: const EdgeInsets.all(8),
                    child: Image.asset(
                      imageAsset!,
                      fit: BoxFit.contain,
                      errorBuilder: (context, error, stackTrace) {
                        return Icon(
                          icon ?? Icons.sports_esports_rounded,
                          color: const Color(0xFF8067FF),
                          size: 24,
                        );
                      },
                    ),
                  )
                : Icon(
                    icon ?? Icons.sports_esports_rounded,
                    color: const Color(0xFF8067FF),
                    size: 24,
                  ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  label,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: isDark
                        ? const Color(0xFF7B899D)
                        : const Color(0xFF687386),
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 5),
                Text(
                  value,
                  maxLines: 2,
                  overflow: TextOverflow.visible,
                  style: TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                    height: 1.2,
                    color: isDark ? Colors.white : const Color(0xFF202636),
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  caption,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: isDark
                        ? const Color(0xFF5F6E82)
                        : const Color(0xFF7A8494),
                    fontSize: 11,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
