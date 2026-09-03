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
  });
}
