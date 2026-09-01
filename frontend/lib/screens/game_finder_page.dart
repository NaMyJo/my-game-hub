import 'dart:async';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import '../models/game_finder.dart';
import '../services/game_finder_repository.dart';

class GameFinderPage extends StatefulWidget {
  const GameFinderPage({super.key});
  @override
  State<GameFinderPage> createState() => _GameFinderPageState();
}

class _GameFinderPageState extends State<GameFinderPage> {
  int step = 1;
  RangeValues price = const RangeValues(0, 100000);
  RangeValues players = const RangeValues(1, 15);
  bool includeAdult = false;
  final searchController = TextEditingController();
  Timer? debounce;
  List<SteamGameSearchItem> searchResults = [];
  final selected = <SteamGameSearchItem>[];
  final shown = <int>{};
  List<GameFinderRecommendation> recommendations = [];
  bool loading = false;
  String? error;
  @override
  void dispose() {
    debounce?.cancel();
    searchController.dispose();
    super.dispose();
  }

  void searchChanged(String value) {
    debounce?.cancel();
    debounce = Timer(const Duration(milliseconds: 450), () async {
      if (value.trim().length < 2) {
        if (mounted) setState(() => searchResults = []);
        return;
      }
      setState(() => loading = true);
      try {
        final result = await GameFinderRepository.instance.search(value);
        if (mounted) setState(() => searchResults = result);
      } catch (_) {
        if (mounted) setState(() => error = 'Steam 게임 검색에 실패했습니다.');
      } finally {
        if (mounted) setState(() => loading = false);
      }
    });
  }

  Future<void> recommend({bool more = false}) async {
    if (selected.isEmpty) return;
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final result = await GameFinderRepository.instance.recommend(
          likedIds: selected.map((e) => e.appId).toList(),
          priceMin: price.start.round(),
          priceMax: price.end.round(),
          includeAdult: includeAdult,
          playerMin: players.start.round(),
          playerMax: players.end.round(),
          excluded: more ? shown : <int>{});
      if (mounted) {
        setState(() {
          recommendations = result;
          shown.addAll(result.map((e) => e.appId));
          step = 3;
        });
      }
    } catch (_) {
      if (mounted) setState(() => error = '추천 결과를 불러오지 못했습니다.');
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const Text('GAME FINDER',
          style: TextStyle(fontSize: 28, fontWeight: FontWeight.w900)),
      const SizedBox(height: 6),
      const Text('좋아했던 Steam 게임을 바탕으로 다음 게임을 찾아보세요.'),
      const SizedBox(height: 20),
      Wrap(spacing: 8, children: [
        for (var i = 1; i <= 3; i++)
          Chip(
              avatar: CircleAvatar(child: Text('$i')),
              label: Text(i == 1
                  ? '취향 게임'
                  : i == 2
                      ? '탐색 범위'
                      : '추천 결과'),
              backgroundColor: step == i
                  ? const Color(0xFF6F5AE8).withValues(alpha: .24)
                  : null)
      ]),
      const SizedBox(height: 18),
      if (error != null)
        Container(
            width: double.infinity,
            padding: const EdgeInsets.all(14),
            color: Colors.red.withValues(alpha: .1),
            child: Text(error!)),
      if (step == 1) _taste(),
      if (step == 2) _filters(),
      if (step == 3) _results(),
      if (loading)
        const Padding(
            padding: EdgeInsets.all(24),
            child: Center(child: CircularProgressIndicator()))
    ]);
  }

  Widget _panel(Widget child) => Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
          color: Theme.of(context).brightness == Brightness.dark
              ? const Color(0xFF091322)
              : Colors.white,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
              color: Theme.of(context).brightness == Brightness.dark
                  ? const Color(0xFF24344B)
                  : const Color(0xFFDDE3EC))),
      child: child);
  Widget _filters() =>
      _panel(Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('탐색 범위',
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
        const SizedBox(height: 20),
        Text(
            '가격  ${_won(price.start.round())} ~ ${price.end == 100000 ? '100,000원+' : _won(price.end.round())}'),
        RangeSlider(
            values: price,
            min: 0,
            max: 100000,
            divisions: 100,
            onChanged: (v) => setState(() => price = v)),
        const SizedBox(height: 12),
        SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('성인용 게임 포함'),
            subtitle: const Text('끄면 성인용으로 확인된 게임을 제외합니다.'),
            value: includeAdult,
            onChanged: (v) => setState(() => includeAdult = v)),
        const SizedBox(height: 12),
        Text(
            '플레이 인원  ${players.start.round()}명 ~ ${players.end == 15 ? '15명+' : '${players.end.round()}명'}'),
        RangeSlider(
            values: players,
            min: 1,
            max: 15,
            divisions: 14,
            onChanged: (v) => setState(() => players = v)),
        const SizedBox(height: 18),
        Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          TextButton(
              onPressed: () => setState(() => step = 1),
              child: const Text('취향 게임 수정')),
          FilledButton.icon(
              onPressed: selected.isEmpty || loading ? null : () => recommend(),
              icon: const Icon(Icons.auto_awesome),
              label: const Text('게임 추천받기'))
        ])
      ]));
  Widget _taste() =>
      _panel(Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          const Expanded(
              child: Text('재미있게 했던 게임',
                  style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900))),
          Text('${selected.length} / 10')
        ]),
        const SizedBox(height: 14),
        TextField(
            controller: searchController,
            onChanged: searchChanged,
            decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search),
                hintText: 'Steam 게임명 검색',
                border: OutlineInputBorder())),
        if (selected.isNotEmpty) ...[
          const SizedBox(height: 12),
          Wrap(
              spacing: 8,
              runSpacing: 8,
              children: selected
                  .map((g) => InputChip(
                      label: Text(g.name),
                      onDeleted: () => setState(() => selected.remove(g))))
                  .toList())
        ],
        const SizedBox(height: 14),
        if (!loading &&
            searchController.text.length >= 2 &&
            searchResults.isEmpty)
          const Text('검색 결과가 없습니다.'),
        ...searchResults
            .where((g) => !selected.any((s) => s.appId == g.appId))
            .take(10)
            .map((g) => ListTile(
                leading: _image(g.imageUrl, 48),
                title: Text(g.name),
                trailing: const Icon(Icons.add_circle_outline),
                onTap: selected.length >= 10
                    ? null
                    : () => setState(() => selected.add(g)))),
        const SizedBox(height: 16),
        Align(
            alignment: Alignment.centerRight,
            child: FilledButton.icon(
                onPressed:
                    selected.isEmpty ? null : () => setState(() => step = 2),
                icon: const Icon(Icons.arrow_forward),
                label: const Text('조건 설정')))
      ]));
  Widget _results() =>
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        _panel(Wrap(spacing: 14, runSpacing: 8, children: [
          Text(
              '가격 ${_won(price.start.round())}~${price.end == 100000 ? '상한 없음' : _won(price.end.round())}'),
          Text(
              '인원 ${players.start.round()}~${players.end == 15 ? '15명+' : players.end.round()}'),
          Text(includeAdult ? '성인 포함' : '성인 제외'),
          Text('취향 게임 ${selected.length}개')
        ])),
        const SizedBox(height: 16),
        if (recommendations.isEmpty)
          _panel(Column(children: [
            const Text('조건에 맞는 새로운 게임을 모두 확인했어요.'),
            TextButton(
                onPressed: () {
                  setState(() => shown.clear());
                  recommend();
                },
                child: const Text('처음부터 다시 보기'))
          ]))
        else
          LayoutBuilder(builder: (context, c) {
            final columns = c.maxWidth >= 1100
                ? 4
                : c.maxWidth >= 700
                    ? 3
                    : c.maxWidth >= 430
                        ? 2
                        : 1;
            return GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: recommendations.length,
                gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: columns,
                    crossAxisSpacing: 12,
                    mainAxisSpacing: 12,
                    childAspectRatio: .68),
                itemBuilder: (_, i) => _card(recommendations[i]));
          }),
        const SizedBox(height: 18),
        Wrap(spacing: 10, children: [
          OutlinedButton(
              onPressed: () => setState(() => step = 2),
              child: const Text('조건 수정')),
          FilledButton.icon(
              onPressed: loading ? null : () => recommend(more: true),
              icon: const Icon(Icons.refresh),
              label: const Text('다른 게임 보기'))
        ])
      ]);
  Widget _card(GameFinderRecommendation g) => Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
          onTap: () =>
              launchUrl(Uri.parse(g.storeUrl), webOnlyWindowName: '_blank'),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            AspectRatio(
                aspectRatio: 460 / 215,
                child: _image(g.imageUrl, double.infinity)),
            Expanded(
                child: Padding(
                    padding: const EdgeInsets.all(14),
                    child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(g.name,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                  fontSize: 16, fontWeight: FontWeight.w900)),
                          const SizedBox(height: 7),
                          Text('취향 일치도 ${g.matchScore}%',
                              style: const TextStyle(
                                  color: Color(0xFF8D79FF),
                                  fontWeight: FontWeight.w800)),
                          const SizedBox(height: 7),
                          Text(_price(g)),
                          Text(_release(g)),
                          const SizedBox(height: 7),
                          Text(
                              [
                                if (g.singlePlayer == true) '싱글',
                                if (g.multiplayer == true) '멀티',
                                if (g.onlineCoop == true) '온라인 협동',
                                if (g.maxPlayers != null) '최대 ${g.maxPlayers}명'
                              ].join(' · '),
                              maxLines: 2),
                          const Spacer(),
                          Text(g.genres.take(3).join(' · '),
                              maxLines: 2, overflow: TextOverflow.ellipsis),
                          const SizedBox(height: 8),
                          const Row(
                              mainAxisAlignment: MainAxisAlignment.end,
                              children: [
                                Text('Steam Store'),
                                SizedBox(width: 5),
                                Icon(Icons.open_in_new, size: 15)
                              ])
                        ])))
          ])));
  Widget _image(String? url, double size) => url == null || url.isEmpty
      ? Container(
          width: size,
          color: const Color(0xFF18243A),
          child: const Icon(Icons.sports_esports))
      : Image.network(url,
          width: size,
          fit: BoxFit.cover,
          errorBuilder: (_, __, ___) => Container(
              width: size,
              color: const Color(0xFF18243A),
              child: const Icon(Icons.broken_image_outlined)));
  String _price(GameFinderRecommendation g) {
    if (g.isFree) return '무료';
    if (g.currentPrice == null) return '가격 미정';
    final discount =
        (g.discountPercent ?? 0) > 0 ? ' (-${g.discountPercent}%)' : '';
    return '${_won(g.currentPrice!)}$discount';
  }

  String _release(GameFinderRecommendation g) {
    if (g.releaseDate != null) {
      final d = DateTime.tryParse(g.releaseDate!);
      if (d != null) {
        return '${d.year}. ${d.month.toString().padLeft(2, '0')}. ${d.day.toString().padLeft(2, '0')} ${g.comingSoon ? '출시 예정' : '출시'}';
      }
    }
    return g.comingSoon
        ? (g.releaseDateText?.isNotEmpty == true
            ? '${g.releaseDateText} 출시 예정'
            : '출시 예정')
        : (g.releaseDateText?.isNotEmpty == true
            ? '${g.releaseDateText} 출시'
            : '출시일 미상');
  }

  String _won(int value) =>
      '₩${value.toString().replaceAllMapped(RegExp(r'\B(?=(\d{3})+(?!\d))'), (m) => ',')}';
}
