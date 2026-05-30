package com.ziprun.reassignment.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigResponse {
    private String key;
    private String value;
    private List<String> availableOptions;
}
