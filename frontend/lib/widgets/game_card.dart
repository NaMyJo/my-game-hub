import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../models/game_profile.dart';

class GameCard extends StatelessWidget {
  const GameCard({
    super.key,
    required this.profile,
    required this.isRefreshing,
    required this.onRefresh,
    required this.onRemove,
    this.mobile = false,
  });

  final GameProfile profile;
  final bool isRefreshing;
  final VoidCallback onRefresh;
  final VoidCallback onRemove;
  final bool mobile;
  ({String gameName, String tagLine})? _parseRiotId() {
    final parts = profile.accountName.split('#');

    if (parts.length != 2) {
      return null;
    }

    final gameName = parts[0].trim();
    final tagLine = parts[1].trim();

    if (gameName.isEmpty || tagLine.isEmpty) {
      return null;
    }

    return (
      gameName: gameName,
      tagLine: tagLine,
    );
  }

  String _mobileTier(String value) {
    // 예:
    // EMERALD IV · 72 LP · 137승 131패
    // -> EMERALD IV

    final dotIndex = value.indexOf('·');

    if (dotIndex != -1) {
      return value.substring(0, dotIndex).trim();
    }

    // 혹시 "EMERALD IV 72 LP" 같은 형태면 LP 앞까지만
    final lpIndex = value.toUpperCase().indexOf(' LP');

    if (lpIndex != -1) {
      final beforeLp = value.substring(0, lpIndex).trim();
      final parts = beforeLp.split(' ');

      if (parts.length >= 2) {
        return parts.take(parts.length - 1).join(' ');
      }
    }

    return value;
  }

  Future<void> _openOpgg() async {
    final riotId = _parseRiotId();

    if (riotId == null) return;

    final gameName = Uri.encodeComponent(riotId.gameName);
    final tagLine = Uri.encodeComponent(riotId.tagLine);

    await _openExternalUrl(
      'https://op.gg/lol/summoners/kr/$gameName-$tagLine',
    );
  }

  Future<void> _openLolPs() async {
    final riotId = _parseRiotId();

    if (riotId == null) return;

    final gameName = Uri.encodeComponent(riotId.gameName);
    final tagLine = Uri.encodeComponent(riotId.tagLine);

    await _openExternalUrl(
      'https://lol.ps/summoner/${gameName}_$tagLine?region=kr',
    );
  }

  Future<void> _openLolChess() async {
    final riotId = _parseRiotId();

    if (riotId == null) return;

    final gameName = Uri.encodeComponent(riotId.gameName);
    final tagLine = Uri.encodeComponent(riotId.tagLine);

    await _openExternalUrl(
      'https://lolchess.gg/profile/kr/$gameName-$tagLine',
    );
  }

  Future<void> _openDakggEternalReturn() async {
    final nickname = Uri.encodeComponent(profile.accountName.trim());

    await _openExternalUrl(
      'https://dak.gg/er/players/$nickname?hl=ko',
    );
  }

  ButtonStyle _externalButtonStyle() {
    return OutlinedButton.styleFrom(
      padding: const EdgeInsets.symmetric(
        vertical: 12,
      ),
      side: const BorderSide(
        color: Color(0xFF35465F),
      ),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(10),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final accent = _accent(profile.type);

    return Container(
      height: mobile
          ? switch (profile.type) {
              GameType.lostArk => 300,
              GameType.leagueOfLegends => 260,
              GameType.tft => 260,
              GameType.eternalReturn => 330,
            }
          : switch (profile.type) {
              GameType.lostArk => 340,
              GameType.leagueOfLegends => 360,
              GameType.tft => 360,
              GameType.eternalReturn => 480,
            },
      padding: EdgeInsets.all(mobile ? 12 : 18),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        color: const Color(0xFF091322),
        border: Border.all(color: const Color(0xFF1A293C)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            children: [
              Container(
                width: 34,
                height: 34,
                decoration: BoxDecoration(
                  color: accent.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(
                  _icon(profile.type),
                  color: accent,
                  size: 20,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  profile.type.displayName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 14,
                  ),
                ),
              ),
              IconButton(
                tooltip: '최신 정보 불러오기',
                onPressed: isRefreshing ? null : onRefresh,
                icon: AnimatedRotation(
                  turns: isRefreshing ? 1 : 0,
                  duration: const Duration(milliseconds: 700),
                  curve: Curves.easeInOut,
                  child: const Icon(Icons.refresh),
                ),
              ),
              PopupMenuButton<String>(
                tooltip: '게임 메뉴',
                onSelected: (value) {
                  if (value == 'remove') onRemove();
                },
                itemBuilder: (_) => const [
                  PopupMenuItem(
                    value: 'remove',
                    child: Text('삭제'),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 14),
          Text(
            profile.accountName,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: Color(0xFFAEB9C8),
              fontSize: 12,
            ),
          ),
          const SizedBox(height: 22),
          if (profile.type == GameType.eternalReturn) ...[
            _Metric(
              label: profile.primaryLabel,
              value: profile.secondaryValue ?? '-',
              color: accent,
              prominent: true,
            ),
            const SizedBox(height: 12),
            _Metric(
              label: '판수',
              value:
                  profile.totalGames == null ? '-' : '${profile.totalGames}판',
            ),
            const SizedBox(height: 12),
            _Metric(
              label: '평균 순위',
              value: profile.averagePlacement == null
                  ? '-'
                  : profile.averagePlacement!.toStringAsFixed(2),
            ),
            if (!mobile) ...[
              const SizedBox(height: 16),
              const Text(
                '선호 실험체',
                style: TextStyle(
                  color: Color(0xFF7B899D),
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: _FavoriteCharacter(
                      rank: 1,
                      character: profile.favoriteCharacter1,
                    ),
                  ),
                  const SizedBox(width: 6),
                  Expanded(
                    child: _FavoriteCharacter(
                      rank: 2,
                      character: profile.favoriteCharacter2,
                    ),
                  ),
                  const SizedBox(width: 6),
                  Expanded(
                    child: _FavoriteCharacter(
                      rank: 3,
                      character: profile.favoriteCharacter3,
                    ),
                  ),
                ],
              ),
            ],
          ] else ...[
            if (mobile &&
                (profile.type == GameType.leagueOfLegends ||
                    profile.type == GameType.tft))
              _MobileTier(
                value: _mobileTier(profile.primaryValue),
                color: accent,
              )
            else ...[
              _Metric(
                label: profile.primaryLabel,
                value: profile.primaryValue,
                color: accent,
                prominent: true,
              ),
              if (profile.secondaryLabel != null &&
                  profile.secondaryValue != null) ...[
                const SizedBox(height: 16),
                _Metric(
                  label: profile.secondaryLabel!,
                  value: profile.secondaryValue!,
                ),
              ],
              if (profile.tertiaryLabel != null &&
                  profile.tertiaryValue != null) ...[
                const SizedBox(height: 16),
                _Metric(
                  label: profile.tertiaryLabel!,
                  value: profile.tertiaryValue!,
                ),
              ],
            ],
          ],
          const Spacer(),
          const Spacer(),

// ====================
// LOST ARK
// ====================
          if (profile.type == GameType.lostArk) ...[
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: _openKloa,
                    icon: const Icon(
                      Icons.open_in_new_rounded,
                      size: 15,
                    ),
                    label: const Text(
                      'KLOA',
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    style: _externalButtonStyle(),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: _openLopec,
                    icon: const Icon(
                      Icons.open_in_new_rounded,
                      size: 15,
                    ),
                    label: const Text(
                      'LOPEC',
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    style: _externalButtonStyle(),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
          ],

// ====================
// LEAGUE OF LEGENDS
// ====================
          if (profile.type == GameType.leagueOfLegends) ...[
            if (mobile)
              SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  onPressed: _openOpgg,
                  icon: const Icon(
                    Icons.open_in_new_rounded,
                    size: 14,
                  ),
                  label: const Text(
                    'OP.GG',
                    style: TextStyle(
                      fontWeight: FontWeight.w700,
                      fontSize: 11,
                    ),
                  ),
                  style: _externalButtonStyle(),
                ),
              )
            else
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _openOpgg,
                      icon: const Icon(
                        Icons.open_in_new_rounded,
                        size: 15,
                      ),
                      label: const Text(
                        'OP.GG',
                        style: TextStyle(
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      style: _externalButtonStyle(),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _openLolPs,
                      icon: const Icon(
                        Icons.open_in_new_rounded,
                        size: 15,
                      ),
                      label: const Text(
                        'LOL.PS',
                        style: TextStyle(
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      style: _externalButtonStyle(),
                    ),
                  ),
                ],
              ),
            const SizedBox(height: 12),
          ],

// ====================
// TFT
// ====================
          if (profile.type == GameType.tft) ...[
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                onPressed: _openLolChess,
                icon: const Icon(
                  Icons.open_in_new_rounded,
                  size: 15,
                ),
                label: const Text(
                  'LOLCHESS.GG 전적 보기',
                  style: TextStyle(
                    fontWeight: FontWeight.w700,
                  ),
                ),
                style: _externalButtonStyle(),
              ),
            ),
            const SizedBox(height: 12),
          ],

// ====================
// ETERNAL RETURN
// ====================
          if (profile.type == GameType.eternalReturn) ...[
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                onPressed: _openDakggEternalReturn,
                icon: const Icon(
                  Icons.open_in_new_rounded,
                  size: 15,
                ),
                label: const Text(
                  'DAK.GG 전적 보기',
                  style: TextStyle(
                    fontWeight: FontWeight.w700,
                  ),
                ),
                style: _externalButtonStyle(),
              ),
            ),
            const SizedBox(height: 12),
          ],

// 기존 API 출처
          if (!mobile) ...[
            Row(
              children: [
                const Icon(
                  Icons.sync_rounded,
                  color: Color(0xFF5F6E82),
                  size: 14,
                ),
                const SizedBox(width: 5),
                Text(
                  switch (profile.type) {
                    GameType.lostArk => 'Lost Ark Open API',
                    GameType.leagueOfLegends => 'Riot Games API',
                    GameType.tft => 'Riot Games TFT API',
                    GameType.eternalReturn => 'Eternal Return Open API',
                  },
                  style: const TextStyle(
                    color: Color(0xFF5F6E82),
                    fontSize: 10,
                  ),
                ),
              ],
            ),
            Row(
              children: [
                const Icon(
                  Icons.sync_rounded,
                  color: Color(0xFF5F6E82),
                  size: 14,
                ),
                const SizedBox(width: 5),
                Text(
                  switch (profile.type) {
                    GameType.lostArk => 'Lost Ark Open API',
                    GameType.leagueOfLegends => 'Riot Games API',
                    GameType.tft => 'Riot Games TFT API',
                    GameType.eternalReturn => 'Eternal Return API 승인 대기',
                  },
                  style: const TextStyle(
                    color: Color(0xFF5F6E82),
                    fontSize: 10,
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  IconData _icon(GameType type) {
    switch (type) {
      case GameType.lostArk:
        return Icons.auto_awesome_rounded;
      case GameType.leagueOfLegends:
        return Icons.shield_rounded;
      case GameType.tft:
        return Icons.workspace_premium_rounded;
      case GameType.eternalReturn:
        return Icons.diamond_rounded;
    }
  }

  Future<void> _openExternalUrl(String url) async {
    final uri = Uri.parse(url);

    final opened = await launchUrl(
      uri,
      mode: LaunchMode.externalApplication,
    );

    if (!opened) {
      throw Exception('외부 사이트를 열 수 없습니다.');
    }
  }

  Future<void> _openKloa() async {
    final characterName = Uri.encodeComponent(profile.accountName);

    final uri = Uri.parse(
      'https://kloa.gg/characters/$characterName',
    );

    final opened = await launchUrl(
      uri,
      mode: LaunchMode.externalApplication,
      webOnlyWindowName: '_blank',
    );

    if (!opened) {
      throw Exception('KLOA 페이지를 열 수 없습니다.');
    }
  }

  Future<void> _openLopec() async {
    final characterName = Uri.encodeComponent(profile.accountName);

    final uri = Uri.parse(
      'https://lopec.kr/character/specPoint/$characterName',
    );

    final opened = await launchUrl(
      uri,
      mode: LaunchMode.externalApplication,
      webOnlyWindowName: '_blank',
    );

    if (!opened) {
      throw Exception('LOPEC 페이지를 열 수 없습니다.');
    }
  }

  Color _accent(GameType type) {
    switch (type) {
      case GameType.lostArk:
        return const Color(0xFFD6A558);
      case GameType.leagueOfLegends:
        return const Color(0xFF31A8FF);
      case GameType.tft:
        return const Color(0xFFB66CFF);
      case GameType.eternalReturn:
        return const Color(0xFF6988FF);
    }
  }
}

class _Metric extends StatelessWidget {
  const _Metric({
    required this.label,
    required this.value,
    this.color,
    this.prominent = false,
  });

  final String label;
  final String value;
  final Color? color;
  final bool prominent;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        SizedBox(
          width: 90,
          child: Text(
            label,
            style: const TextStyle(
              color: Color(0xFF7B899D),
              fontSize: 11,
            ),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Align(
            alignment: Alignment.centerRight,
            child: FittedBox(
              fit: BoxFit.scaleDown,
              alignment: Alignment.centerRight,
              child: Text(
                value,
                maxLines: 1,
                style: TextStyle(
                  color: color ?? const Color(0xFFD2DBE7),
                  fontSize: prominent ? 19 : 14,
                  fontWeight: prominent ? FontWeight.w800 : FontWeight.w700,
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _FavoriteCharacter extends StatelessWidget {
  const _FavoriteCharacter({
    required this.rank,
    required this.character,
  });

  final int rank;
  final FavoriteCharacter? character;

  @override
  Widget build(BuildContext context) {
    final medal = switch (rank) {
      1 => '🥇',
      2 => '🥈',
      3 => '🥉',
      _ => '',
    };

    final name = character?.name ?? '-';
    final games = character?.totalGames;
    final averageRank = character?.averageRank;

    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: 6,
        vertical: 8,
      ),
      decoration: BoxDecoration(
        color: const Color(0xFF111C2B),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: const Color(0xFF26364B),
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            '$medal $name',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: Color(0xFFD2DBE7),
              fontSize: 10,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            games == null
                ? '-'
                : '$games판 · 평균 ${averageRank?.toStringAsFixed(1) ?? '-'}등',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: Color(0xFF7B899D),
              fontSize: 9,
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }
}

class _MobileTier extends StatelessWidget {
  const _MobileTier({
    required this.value,
    required this.color,
  });

  final String value;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          '티어',
          style: TextStyle(
            color: Color(0xFF7B899D),
            fontSize: 11,
          ),
        ),
        const SizedBox(height: 7),
        Text(
          value,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
            color: color,
            fontSize: 17,
            fontWeight: FontWeight.w800,
          ),
        ),
      ],
    );
  }
}
