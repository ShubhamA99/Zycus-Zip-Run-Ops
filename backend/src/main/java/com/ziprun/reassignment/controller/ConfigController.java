package com.ziprun.reassignment.controller;

import com.ziprun.reassignment.dto.ConfigResponse;
import com.ziprun.reassignment.dto.UpdateConfigRequest;
import com.ziprun.reassignment.service.ConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @GetMapping("/routing-strategy")
    public ResponseEntity<ConfigResponse> getRoutingStrategy() {
        String strategy = configService.getRoutingStrategy();
        return ResponseEntity.ok(ConfigResponse.builder()
                .key(ConfigService.ROUTING_STRATEGY_KEY)
                .value(strategy)
                .availableOptions(configService.getValidStrategies())
                .build());
    }

    @PutMapping("/routing-strategy")
    public ResponseEntity<ConfigResponse> setRoutingStrategy(
            @Valid @RequestBody UpdateConfigRequest request) {

        if (!configService.getValidStrategies().contains(request.getValue())) {
            throw new IllegalArgumentException(
                    "Invalid strategy: " + request.getValue() +
                    ". Valid options: " + configService.getValidStrategies());
        }

        String updated = configService.setRoutingStrategy(request.getValue());
        return ResponseEntity.ok(ConfigResponse.builder()
                .key(ConfigService.ROUTING_STRATEGY_KEY)
                .value(updated)
                .availableOptions(configService.getValidStrategies())
                .build());
    }
}
