package com.foody.delivery.rpd.restapi.dto.login;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token JWT para usar no cabeçalho Authorization")
public record TokenResponse(
        @Schema(description = "Token JWT, válido por 1 hora", example = "eyJhbGciOiJIUzI1NiJ9...")
        String token) {
}
