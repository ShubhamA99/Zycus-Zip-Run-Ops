package com.ziprun.reassignment.service;

import com.ziprun.reassignment.domain.entity.AppConfig;
import com.ziprun.reassignment.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConfigService {

    public static final String ROUTING_STRATEGY_KEY = "routing-strategy";
    public static final String DEFAULT_STRATEGY = "rule-based";

    public static final String REPLAN_FALLBACK_ENABLED_KEY = "replan.fallback.enabled";
    public static final String REPLAN_RETRY_MAX_ATTEMPTS_KEY = "replan.retry.max-attempts";
    public static final String REPLAN_RETRY_BASE_DELAY_KEY = "replan.retry.base-delay-ms";
    public static final String REPLAN_RETRY_MAX_JITTER_KEY = "replan.retry.max-jitter-ms";

    private static final boolean DEFAULT_FALLBACK_ENABLED = true;
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int DEFAULT_BASE_DELAY_MS = 500;
    private static final int DEFAULT_MAX_JITTER_MS = 300;

    private final AppConfigRepository configRepository;

    public String getRoutingStrategy() {
        return configRepository.findById(ROUTING_STRATEGY_KEY)
                .map(AppConfig::getConfigValue)
                .orElse(DEFAULT_STRATEGY);
    }

    @Transactional
    public String setRoutingStrategy(String strategy) {
        AppConfig config = configRepository.findById(ROUTING_STRATEGY_KEY)
                .orElse(AppConfig.builder().configKey(ROUTING_STRATEGY_KEY).build());
        config.setConfigValue(strategy);
        configRepository.save(config);
        return strategy;
    }

    public List<String> getValidStrategies() {
        return List.of("rule-based", "ai");
    }

    public boolean isReplanFallbackEnabled() {
        return configRepository.findById(REPLAN_FALLBACK_ENABLED_KEY)
                .map(config -> Boolean.parseBoolean(config.getConfigValue()))
                .orElse(DEFAULT_FALLBACK_ENABLED);
    }

    public int getReplanMaxAttempts() {
        return configRepository.findById(REPLAN_RETRY_MAX_ATTEMPTS_KEY)
                .map(config -> Integer.parseInt(config.getConfigValue()))
                .orElse(DEFAULT_MAX_ATTEMPTS);
    }

    public int getReplanBaseDelayMs() {
        return configRepository.findById(REPLAN_RETRY_BASE_DELAY_KEY)
                .map(config -> Integer.parseInt(config.getConfigValue()))
                .orElse(DEFAULT_BASE_DELAY_MS);
    }

    public int getReplanMaxJitterMs() {
        return configRepository.findById(REPLAN_RETRY_MAX_JITTER_KEY)
                .map(config -> Integer.parseInt(config.getConfigValue()))
                .orElse(DEFAULT_MAX_JITTER_MS);
    }

    @Transactional
    public void setReplanFallbackEnabled(boolean enabled) {
        setConfigValue(REPLAN_FALLBACK_ENABLED_KEY, String.valueOf(enabled));
    }

    @Transactional
    public void setReplanMaxAttempts(int maxAttempts) {
        setConfigValue(REPLAN_RETRY_MAX_ATTEMPTS_KEY, String.valueOf(maxAttempts));
    }

    private void setConfigValue(String key, String value) {
        AppConfig config = configRepository.findById(key)
                .orElse(AppConfig.builder().configKey(key).build());
        config.setConfigValue(value);
        configRepository.save(config);
    }
}
