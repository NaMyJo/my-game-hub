import '../models/my_game_pick.dart';
import 'api_client.dart';

class MyGamePicksRepository {
  MyGamePicksRepository._();
  static final instance = MyGamePicksRepository._();

  Future<List<MyGamePick>> list() async {
    final json =
        await ApiClient.instance.get('/api/my-game-picks') as List<dynamic>;
    return json
        .map((value) => MyGamePick.fromJson(value as Map<String, dynamic>))
        .toList();
  }

  Future<MyGamePick> add(int appId) async => MyGamePick.fromJson(
      await ApiClient.instance.post('/api/my-game-picks/$appId')
          as Map<String, dynamic>);

  Future<void> remove(int appId) =>
      ApiClient.instance.delete('/api/my-game-picks/$appId');
}
