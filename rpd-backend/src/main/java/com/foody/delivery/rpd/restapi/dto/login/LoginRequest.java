package com.foody.delivery.rpd.restapi.dto.login;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciais de login")
public record LoginRequest(
        @Schema(example = "maria@email.com")
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Formato de e-mail inválido")
        String email,

        @Schema(example = "senha1234", format = "password") @NotBlank(message = "Senha é obrigatório")
        String password) {
}
