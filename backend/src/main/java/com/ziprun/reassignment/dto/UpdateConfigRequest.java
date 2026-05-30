package com.ziprun.reassignment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConfigRequest {

    @NotBlank(message = "Value is required")
    private String value;
}
