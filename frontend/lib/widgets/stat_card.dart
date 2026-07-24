import 'package:flutter/material.dart';

class StatCard extends StatelessWidget {
  const StatCard({
    super.key,
    this.icon,
    this.imageAsset,
    required this.label,
    required this.value,
    required this.caption,
  });

  final IconData? icon;
  final String? imageAsset;

  final String label;
  final String value;
  final String caption;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFF091322),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: const Color(0xFF1A293C),
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
                  style: const TextStyle(
                    color: Color(0xFF7B899D),
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 5),
                Text(
                  value,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Color(0xFFF1F4F8),
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  caption,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Color(0xFF5F6E82),
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
