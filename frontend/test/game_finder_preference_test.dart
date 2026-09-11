import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/models/game_finder.dart';

void main() {
  test('seed 또는 tag가 있어야 추천할 수 있다', () {
    expect(canRequestGameFinderRecommendation([570], []), isTrue);
    expect(canRequestGameFinderRecommendation([], ['coop']), isTrue);
    expect(canRequestGameFinderRecommendation([570], ['coop']), isTrue);
    expect(canRequestGameFinderRecommendation([], []), isFalse);
  });

  test('마지막 설정과 최근 게임 응답을 복원한다', () {
    final value = GameFinderPreferences.fromJson({
      'selectedGames': [
        {'steamAppId': 570, 'name': 'Dota 2'}
      ],
      'preferredTags': ['moba'],
      'priceMin': 1000,
      'priceMax': 50000,
      'includeAdult': false,
      'playerMin': 1,
      'playerMax': 5,
      'recentGames': [
        {'steamAppId': 730, 'name': 'Counter-Strike 2'}
      ],
    });
    expect(value.selectedGames.single.appId, 570);
    expect(value.preferredTags, ['moba']);
    expect(value.recentGames.single.appId, 730);
    expect(value.priceMax, 50000);
    expect(value.releasePreference, GameFinderReleasePreference.recent);
  });

  test('출시 선호를 복원하고 기존 응답은 RECENT를 기본값으로 사용한다', () {
    final balanced = GameFinderPreferences.fromJson({
      'releasePreference': 'BALANCED',
    });
    final legacy = GameFinderPreferences.fromJson({});
    final removedAny = GameFinderPreferences.fromJson({
      'releasePreference': 'ANY',
    });
    expect(balanced.releasePreference, GameFinderReleasePreference.balanced);
    expect(legacy.releasePreference, GameFinderReleasePreference.recent);
    expect(removedAny.releasePreference, GameFinderReleasePreference.balanced);
  });

  test('플레이 방식에 따라 인원 범위 표시 여부를 결정한다', () {
    expect(GameFinderPlayMode.single.showsPlayerRange, isFalse);
    expect(GameFinderPlayMode.multi.showsPlayerRange, isTrue);
    expect(GameFinderPlayMode.single.apiValue, 'SINGLE');
    expect(GameFinderPlayMode.multi.apiValue, 'MULTI');
  });

  test('가격 유형에 따라 가격 범위 표시 여부를 결정한다', () {
    expect(GameFinderPriceMode.free.showsPriceRange, isFalse);
    expect(GameFinderPriceMode.paid.showsPriceRange, isTrue);
    expect(GameFinderPriceMode.free.apiValue, 'FREE');
    expect(GameFinderPriceMode.paid.apiValue, 'PAID');
  });
}
