package com.mygamehub.gameidentity.rank;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class RankDistributionLoader {

    private static final String RESOURCE_PATH =
            "rank-distributions.json";

    private final ObjectMapper objectMapper;

    private volatile RankDistributionConfig cachedConfig;

    public RankDistributionLoader(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    public RankDistributionConfig load() {
        RankDistributionConfig current =
                cachedConfig;

        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (cachedConfig != null) {
                return cachedConfig;
            }

            cachedConfig = readConfig();

            return cachedConfig;
        }
    }

    private RankDistributionConfig readConfig() {
        ClassPathResource resource =
                new ClassPathResource(
                        RESOURCE_PATH
                );

        if (!resource.exists()) {
            throw new IllegalStateException(
                    "티어 분포 리소스를 찾을 수 없습니다: "
                            + RESOURCE_PATH
            );
        }

        try (InputStream inputStream =
                     resource.getInputStream()) {

            RankDistributionConfig config =
                    objectMapper.readValue(
                            inputStream,
                            RankDistributionConfig.class
                    );

            validate(config);

            System.out.println(
                    "========== RANK DISTRIBUTION LOADED =========="
            );
            System.out.println(
                    "Version : " + config.version()
            );
            System.out.println(
                    "Games   : " + config.games().keySet()
            );
            System.out.println(
                    "================================================"
            );

            return config;

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "티어 분포 리소스를 읽는 중 오류가 발생했습니다.",
                    exception
            );
        }
    }

    private void validate(
            RankDistributionConfig config
    ) {
        if (config == null) {
            throw new IllegalStateException(
                    "티어 분포 설정이 비어 있습니다."
            );
        }

        if (config.games() == null ||
                config.games().isEmpty()) {
            throw new IllegalStateException(
                    "티어 분포 설정에 게임 정보가 없습니다."
            );
        }
    }
}