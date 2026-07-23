package com.mygamehub.game;

import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.eternalreturn.EternalReturnApiClient;
import com.mygamehub.eternalreturn.EternalReturnCharacterStat;
import com.mygamehub.eternalreturn.EternalReturnRank;
import com.mygamehub.eternalreturn.EternalReturnUser;
import com.mygamehub.eternalreturn.EternalReturnUserStat;
import com.mygamehub.game.dto.GameAccountResponse;
import com.mygamehub.game.dto.RegisterGameRequest;
import com.mygamehub.lostark.LostArkClient;
import com.mygamehub.lostark.LostArkProfile;
import com.mygamehub.riot.RiotAccount;
import com.mygamehub.riot.RiotApiClient;
import com.mygamehub.riot.RiotLeagueEntry;
import com.mygamehub.riot.TftLeagueEntry;
import com.mygamehub.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GameAccountService {

    private final GameAccountRepository repository;
    private final LostArkClient lostArkClient;
    private final RiotApiClient riotApiClient;
    private final EternalReturnApiClient eternalReturnApiClient;
    private final UserService userService;

    public GameAccountService(
            GameAccountRepository repository,
            LostArkClient lostArkClient,
            RiotApiClient riotApiClient,
            EternalReturnApiClient eternalReturnApiClient,
            UserService userService
    ) {
        this.repository = repository;
        this.lostArkClient = lostArkClient;
        this.riotApiClient = riotApiClient;
        this.eternalReturnApiClient = eternalReturnApiClient;
        this.userService = userService;
    }
        @Transactional
        public void reorderGames(
                String firebaseUid,
                List<Long> gameIds
        ) {
        if (gameIds == null || gameIds.isEmpty()) {
                return;
        }

        List<GameAccount> accounts =
                repository.findAllByFirebaseUidOrderByDisplayOrderAscIdAsc(
                        firebaseUid
                );

        Map<Long, GameAccount> accountMap =
                accounts.stream()
                        .collect(Collectors.toMap(
                                GameAccount::getId,
                                account -> account
                        ));

        if (gameIds.size() != accounts.size()) {
                throw new IllegalArgumentException(
                        "게임 계정 목록이 일치하지 않습니다."
                );
        }

        for (Long gameId : gameIds) {
                if (!accountMap.containsKey(gameId)) {
                throw new IllegalArgumentException(
                        "유효하지 않은 게임 계정입니다."
                );
                }
        }

        for (int i = 0; i < gameIds.size(); i++) {
                GameAccount account =
                        accountMap.get(gameIds.get(i));

                account.setDisplayOrder(i);
        }

        repository.saveAll(accounts);
        }
        @Transactional(readOnly = true)
    public List<GameAccountResponse> list(String firebaseUid) {
        return repository
                .findAllByFirebaseUidOrderByDisplayOrderAscIdAsc(firebaseUid)
                .stream()
                .map(GameAccountResponse::from)
                .toList();
    }
        @Transactional
        public GameAccountResponse register(
                AuthenticatedUser authUser,
                RegisterGameRequest request
        ) {
        userService.sync(authUser);

        // 1. 현재 등록된 게임 카드 개수 확인
        long gameCount =
                repository.countByFirebaseUid(authUser.uid());

        if (gameCount >= 10) {
                throw new IllegalArgumentException(
                        "게임 카드는 최대 10개까지 등록할 수 있습니다."
                );
        }

        // 2. 계정명 정리
        String accountName =
                request.accountName().trim();

        // 3. 같은 게임의 같은 계정 중복 등록 방지
        boolean alreadyExists =
                repository.existsByFirebaseUidAndGameTypeAndAccountName(
                        authUser.uid(),
                        request.gameType(),
                        accountName
                );

        if (alreadyExists) {
                throw new IllegalArgumentException(
                        "이미 등록된 게임 계정입니다."
                );
        }

        // 4. 새로운 게임 카드 생성
        GameAccount account =
                new GameAccount(
                        authUser.uid(),
                        request.gameType(),
                        accountName
                );

        List<GameAccount> existingAccounts =
                repository.findAllByFirebaseUidOrderByDisplayOrderAscIdAsc(
                        authUser.uid()
                );

        account.setDisplayOrder(existingAccounts.size());

        // 5. 게임 API 호출
        refreshStats(account);

        // 6. DB 저장
        return GameAccountResponse.from(
                repository.save(account)
        );
        }
    @Transactional
    public GameAccountResponse refresh(
            AuthenticatedUser authUser,
            Long id
    ) {
        GameAccount account = repository
                .findByIdAndFirebaseUid(
                        id,
                        authUser.uid()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "게임 계정을 찾을 수 없습니다."
                        )
                );

        refreshStats(account);

        return GameAccountResponse.from(
                repository.save(account)
        );
    }

    @Transactional
    public void delete(
            String firebaseUid,
            Long id
    ) {
        GameAccount account = repository
                .findByIdAndFirebaseUid(
                        id,
                        firebaseUid
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "게임 계정을 찾을 수 없습니다."
                        )
                );

        repository.delete(account);
    }

    // =========================================================
    // 게임별 새로고침
    // =========================================================

    private void refreshStats(GameAccount account) {
        switch (account.getGameType()) {
            case LOST_ARK ->
                    refreshLostArk(account);

            case LEAGUE_OF_LEGENDS ->
                    refreshLeague(account);

            case TFT ->
                    refreshTft(account);

            case ETERNAL_RETURN ->
                    refreshEternalReturn(account);
        }
    }

    // =========================================================
    // League of Legends
    // =========================================================

    private void refreshLeague(GameAccount account) {
        String[] riotId =
                account.getAccountName().split("#", 2);

        if (riotId.length != 2) {
            throw new IllegalArgumentException(
                    "Riot ID는 게임이름#태그 형식으로 입력해주세요."
            );
        }

        String gameName = riotId[0].trim();
        String tagLine = riotId[1].trim();

        RiotAccount riotAccount =
                riotApiClient.getLolAccount(
                        gameName,
                        tagLine
                );

        if (riotAccount == null ||
                riotAccount.puuid() == null) {
            throw new IllegalArgumentException(
                    "Riot 계정을 찾을 수 없습니다."
            );
        }

        List<RiotLeagueEntry> entries =
                riotApiClient.getLeagueEntries(
                        riotAccount.puuid()
                );

        RiotLeagueEntry solo = entries.stream()
                .filter(entry ->
                        "RANKED_SOLO_5x5"
                                .equals(entry.queueType()))
                .findFirst()
                .orElse(null);

        RiotLeagueEntry flex = entries.stream()
                .filter(entry ->
                        "RANKED_FLEX_SR"
                                .equals(entry.queueType()))
                .findFirst()
                .orElse(null);

        account.updateStats(
                "솔로랭크",
                formatRankWithWinRate(solo),

                "자유랭크",
                formatRankWithWinRate(flex),

                "Riot ID",
                account.getAccountName()
        );
    }

    private String formatRankWithWinRate(
            RiotLeagueEntry entry
    ) {
        if (entry == null) {
            return "Unranked";
        }

        int wins =
                entry.wins() == null
                        ? 0
                        : entry.wins();

        int losses =
                entry.losses() == null
                        ? 0
                        : entry.losses();

        int games = wins + losses;

        double winRate =
                games == 0
                        ? 0.0
                        : (wins * 100.0) / games;

        return String.format(
                "%s %s · %d LP · %d승 %d패 · %.1f%%",
                entry.tier(),
                entry.rank(),
                entry.leaguePoints(),
                wins,
                losses,
                winRate
        );
    }

    // =========================================================
    // TFT
    // =========================================================

    private void refreshTft(GameAccount account) {
        String[] riotId =
                account.getAccountName().split("#", 2);

        if (riotId.length != 2) {
            throw new IllegalArgumentException(
                    "Riot ID는 게임이름#태그 형식으로 입력해주세요."
            );
        }

        String gameName = riotId[0].trim();
        String tagLine = riotId[1].trim();

        RiotAccount riotAccount =
                riotApiClient.getTftAccount(
                        gameName,
                        tagLine
                );

        if (riotAccount == null ||
                riotAccount.puuid() == null) {
            throw new IllegalArgumentException(
                    "Riot 계정을 찾을 수 없습니다."
            );
        }

        List<TftLeagueEntry> entries =
                riotApiClient.getTftLeagueEntries(
                        riotAccount.puuid()
                );

        TftLeagueEntry ranked =
                entries.stream()
                        .findFirst()
                        .orElse(null);

        if (ranked == null) {
            account.updateStats(
                    "티어",
                    "Unranked",

                    "전적",
                    "-",

                    "Riot ID",
                    account.getAccountName()
            );

            return;
        }

        int wins =
                ranked.wins() == null
                        ? 0
                        : ranked.wins();

        int losses =
                ranked.losses() == null
                        ? 0
                        : ranked.losses();

        account.updateStats(
                "티어",
                String.format(
                        "%s %s · %d LP",
                        ranked.tier(),
                        ranked.rank(),
                        ranked.leaguePoints()
                ),

                "전적",
                String.format(
                        "%d승 %d패",
                        wins,
                        losses
                ),

                "Riot ID",
                account.getAccountName()
        );
    }

    // =========================================================
    // Eternal Return
    // =========================================================

private void refreshEternalReturn(GameAccount account) {

    // 1. 닉네임 -> UID
    EternalReturnUser user =
            eternalReturnApiClient.getUserByNickname(
                    account.getAccountName()
            );

    // 2. 현재 시즌 자동 조회
    int seasonId =
            eternalReturnApiClient.getCurrentSeasonId();

    // 3. 현재 랭크
    EternalReturnRank rank =
            eternalReturnApiClient.getRankByUserId(
                    user.userId(),
                    seasonId
            );

    // 4. 랭크 통계
    EternalReturnUserStat stats =
            eternalReturnApiClient.getRankedStatsByUserId(
                    user.userId(),
                    seasonId
            );

    // 통계가 없는 경우
    if (stats == null) {
        account.updateEternalReturnStats(
                tierFromMmr(rank.mmr()),

                rank.mmr() == null
                        ? "-"
                        : String.format("%,d RP", rank.mmr()),

                null, // totalGames
                null, // averagePlacement

                null, // 1 code
                null, // 1 name
                null, // 1 games
                null, // 1 averageRank

                null, // 2 code
                null, // 2 name
                null, // 2 games
                null, // 2 averageRank

                null, // 3 code
                null, // 3 name
                null, // 3 games
                null  // 3 averageRank
        );

        return;
    }

    // 5. 많이 플레이한 실험체 TOP 3
    List<EternalReturnCharacterStat> favorites =
            stats.characterStats() == null
                    ? List.of()
                    : stats.characterStats()
                            .stream()
                            .sorted(
                                    Comparator.comparingInt(
                                            (EternalReturnCharacterStat stat) ->
                                                    stat.usages() == null
                                                            ? 0
                                                            : stat.usages()
                                    ).reversed()
                            )
                            .limit(3)
                            .toList();

   EternalReturnCharacterStat first =
        favorites.size() > 0 ? favorites.get(0) : null;

EternalReturnCharacterStat second =
        favorites.size() > 1 ? favorites.get(1) : null;

EternalReturnCharacterStat third =
        favorites.size() > 2 ? favorites.get(2) : null;

    // 6. 실험체 코드 → 한국어 이름
    List<Integer> favoriteCodes =
            favorites.stream()
                    .map(EternalReturnCharacterStat::characterCode)
                    .filter(code -> code != null)
                    .toList();

    Map<Integer, String> characterNames =
            eternalReturnApiClient.getCharacterNames(
                    favoriteCodes
            );

    // 7. DB 저장
    account.updateEternalReturnStats(
            tierFromMmr(rank.mmr()),

            rank.mmr() == null
                    ? "-"
                    : String.format("%,d RP", rank.mmr()),

            stats.totalGames(),
            stats.averageRank(),

            first == null ? null : first.characterCode(),
            first == null
                    ? null
                    : characterNames.get(first.characterCode()),
            first == null ? null : first.totalGames(),
            first == null ? null : first.averageRank(),

            second == null ? null : second.characterCode(),
            second == null
                    ? null
                    : characterNames.get(second.characterCode()),
            second == null ? null : second.totalGames(),
            second == null ? null : second.averageRank(),

            third == null ? null : third.characterCode(),
            third == null
                    ? null
                    : characterNames.get(third.characterCode()),
            third == null ? null : third.totalGames(),
            third == null ? null : third.averageRank()
    );
}

    /**
     * 현재는 API에서 받은 MMR을 카드에 표시할 준비만 합니다.
     * 실제 시즌별 티어 경계값은 API 응답 확인 후
     * 정확한 기준으로 교체하는 것이 안전합니다.
     */
    private String tierFromMmr(Integer mmr) {
        if (mmr == null) {
            return "Unranked";
        }

        return "랭크";
    }

    // =========================================================
    // Lost Ark
    // =========================================================

    private void refreshLostArk(
            GameAccount account
    ) {
        LostArkProfile profile =
                lostArkClient.getProfile(
                        account.getAccountName()
                );

        if (profile == null) {
            throw new IllegalArgumentException(
                    "로스트아크 프로필 응답이 비어 있습니다."
            );
        }

        String character = String.format(
                "Lv.%s %s",
                nullToDash(
                        profile.CharacterLevel()
                ),
                nullToDash(
                        profile.CharacterClassName()
                )
        );

        account.updateStats(
                "아이템 레벨",
                nullToDash(
                        profile.ItemAvgLevel()
                ),

                "전투력",
                nullToDash(
                        profile.CombatPower()
                ),

                "캐릭터",
                character
        );
    }

    private String nullToDash(
            String value
    ) {
        return value == null ||
                value.isBlank()
                ? "-"
                : value;
    }
}