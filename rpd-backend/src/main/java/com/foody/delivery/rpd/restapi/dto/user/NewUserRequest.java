package com.foody.delivery.rpd.restapi.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para cadastro de um novo usuário")
public record NewUserRequest(
        @Schema(description = "Nome completo do usuário", example = "Maria Silva")
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 250, message = "Nome deve ter no máximo 250 caracteres")
        String name,

        @Schema(description = "Email de acesso, único no sistema", example = "maria@email.com")
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Formato de e-mail inválido")
        @Size(max = 250)
        String email,

        @Schema(description = "Senha de acesso", example = "senha1234", format = "password")
        @NotBlank(message = "Senha é obrigatório")
        @Size(min = 8, max = 72)
        String password) {
}
