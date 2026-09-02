package com.mygamehub.gamefinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Read-only, ID-keyed comparison of persisted metadata with current Store data. */
@Service
public class SteamMetadataVerificationService {
    private static final Logger log = LoggerFactory.getLogger(SteamMetadataVerificationService.class);
    static final int DEFAULT_SAMPLE_SIZE = 100;
    static final int MAX_SAMPLE_SIZE = 500;

    private final SteamGameRepository games;
    private final SteamStoreDetailClient store;

    public SteamMetadataVerificationService(SteamGameRepository games,
            SteamStoreDetailClient store) {
        this.games = games;
        this.store = store;
    }

    public VerificationSummary verify(int requestedSampleSize, VerificationMode mode) {
        int sampleSize = Math.max(1, Math.min(MAX_SAMPLE_SIZE, requestedSampleSize));
        List<SteamGame> selected = mode == VerificationMode.RECENT
                ? games.findMetadataVerificationRecentSample(PageRequest.of(0, sampleSize))
                : games.findMetadataVerificationRandomSample(PageRequest.of(0, sampleSize));

        LinkedHashMap<Long, SteamGame> candidatesByAppId = new LinkedHashMap<>();
        for (SteamGame game : selected) {
            if (game.getSteamAppId() != null) {
                candidatesByAppId.putIfAbsent(game.getSteamAppId(), game);
            }
        }

        List<VerificationResult> results = new ArrayList<>();
        for (var entry : candidatesByAppId.entrySet()) {
            results.add(verifyOne(entry.getKey(), entry.getValue()));
        }
        VerificationSummary summary = VerificationSummary.from(results);
        log.info("game_finder_metadata_verify_complete mode={} sampled={} matched={} "
                        + "criticalMismatch={} changed={} storeUnavailable={} verificationError={}",
                mode, summary.sampled(), summary.matched(), summary.criticalMismatch(),
                summary.changed(), summary.storeUnavailable(), summary.verificationError());
        return summary;
    }

    private VerificationResult verifyOne(long requestedAppId, SteamGame persisted) {
        try {
            var response = store.get(requestedAppId);
            if (response.isEmpty()) {
                return VerificationResult.of(requestedAppId, persisted.getName(), null, null,
                        VerificationOutcome.UNAVAILABLE, List.of("store_unavailable"));
            }
            SteamStoreDetailClient.StoreDetail actual = response.get();
            if (actual.steamAppId() != requestedAppId) {
                return critical(requestedAppId, persisted, actual,
                        List.of("steam_app_id"));
            }

            List<String> critical = new ArrayList<>();
            if (!sameText(persisted.getName(), actual.name())) critical.add("name");
            if (!sameText(persisted.getStoreType(), actual.type())) critical.add("store_type");
            if (!critical.isEmpty()) return critical(requestedAppId, persisted, actual, critical);

            List<String> changed = changedFields(persisted, actual);
            return VerificationResult.of(requestedAppId, persisted.getName(), actual.steamAppId(),
                    actual.name(), changed.isEmpty() ? VerificationOutcome.MATCH
                            : VerificationOutcome.CHANGED, changed);
        } catch (RuntimeException exception) {
            if (exception instanceof SteamStoreDetailClient.SteamStoreResponseException mismatch) {
                log.error("game_finder_metadata_verify_critical appId={} dbName={} responseAppId={} "
                                + "responseName={} fields=[steam_app_id]", requestedAppId,
                        persisted.getName(), mismatch.responseAppId(), mismatch.responseName());
                return VerificationResult.of(requestedAppId, persisted.getName(),
                        mismatch.responseAppId(), mismatch.responseName(),
                        VerificationOutcome.CRITICAL, List.of("steam_app_id"));
            } else {
                log.warn("game_finder_metadata_verify_error appId={} errorType={}",
                        requestedAppId, safeErrorType(exception));
            }
            return VerificationResult.of(requestedAppId, persisted.getName(), null, null,
                    VerificationOutcome.ERROR, List.of("verification_error"));
        }
    }

    private VerificationResult critical(long requestedAppId, SteamGame persisted,
            SteamStoreDetailClient.StoreDetail actual, List<String> fields) {
        log.error("game_finder_metadata_verify_critical appId={} dbName={} responseAppId={} "
                        + "responseName={} fields={}", requestedAppId, persisted.getName(),
                actual.steamAppId(), actual.name(), fields);
        return VerificationResult.of(requestedAppId, persisted.getName(), actual.steamAppId(),
                actual.name(), VerificationOutcome.CRITICAL, fields);
    }

    private List<String> changedFields(SteamGame game, SteamStoreDetailClient.StoreDetail value) {
        List<String> fields = new ArrayList<>();
        changed(fields, "price_original", game.getPriceOriginal(), value.original());
        changed(fields, "price_current", game.getPriceCurrent(), value.current());
        changed(fields, "discount_percent", game.getDiscountPercent(), value.discount());
        changed(fields, "price_currency", game.getPriceCurrency(), value.currency());
        changed(fields, "is_free", game.getIsFree(), value.free());
        changed(fields, "release_date", game.getReleaseDate(), value.releaseDate());
        changed(fields, "release_date_text", game.getReleaseDateText(), value.releaseText());
        changed(fields, "coming_soon", game.isComingSoon(), value.comingSoon());
        changed(fields, "required_age", game.getRequiredAge(), value.requiredAge());
        changed(fields, "adult_status", game.getAdultStatus(), value.adult());
        changed(fields, "header_image_url", game.getHeaderImageUrl(), value.image());
        changed(fields, "short_description", game.getShortDescription(), value.description());
        changed(fields, "early_access", game.getEarlyAccess(), value.earlyAccess());
        changed(fields, "genres", game.genreSet(), value.genres());
        changed(fields, "categories", game.categorySet(), value.categories());
        changed(fields, "single_player", game.getSinglePlayer(), value.single());
        changed(fields, "multiplayer", game.getMultiplayer(), value.multiplayer());
        changed(fields, "online_coop", game.getOnlineCoop(), value.onlineCoop());
        changed(fields, "offline_coop", game.getOfflineCoop(), value.offlineCoop());
        return List.copyOf(fields);
    }

    private void changed(List<String> fields, String name, Object left, Object right) {
        if (!Objects.equals(left, right)) fields.add(name);
    }

    private boolean sameText(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String safeErrorType(RuntimeException exception) {
        if (exception instanceof org.springframework.web.client.HttpStatusCodeException http) {
            return "HTTP_" + http.getStatusCode().value();
        }
        if (exception instanceof RestClientException) return "STORE_CLIENT_ERROR";
        return exception.getClass().getSimpleName();
    }

    public enum VerificationMode { RANDOM, RECENT }
    public enum VerificationOutcome { MATCH, CHANGED, CRITICAL, UNAVAILABLE, ERROR }

    public record VerificationResult(long steamAppId, String databaseName,
            Long responseSteamAppId, String responseName, VerificationOutcome outcome,
            List<String> mismatchedFields) {
        static VerificationResult of(long steamAppId, String databaseName,
                Long responseSteamAppId, String responseName, VerificationOutcome outcome,
                List<String> fields) {
            return new VerificationResult(steamAppId, databaseName, responseSteamAppId,
                    responseName, outcome, List.copyOf(new LinkedHashSet<>(fields)));
        }
    }

    public record VerificationSummary(int sampled, int matched, int criticalMismatch,
            int changed, int storeUnavailable, int verificationError,
            List<VerificationResult> results) {
        static VerificationSummary from(List<VerificationResult> results) {
            return new VerificationSummary(results.size(), count(results, VerificationOutcome.MATCH),
                    count(results, VerificationOutcome.CRITICAL),
                    count(results, VerificationOutcome.CHANGED),
                    count(results, VerificationOutcome.UNAVAILABLE),
                    count(results, VerificationOutcome.ERROR), List.copyOf(results));
        }

        private static int count(List<VerificationResult> values, VerificationOutcome outcome) {
            return (int) values.stream().filter(value -> value.outcome() == outcome).count();
        }
    }
}
