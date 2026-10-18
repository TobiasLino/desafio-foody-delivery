package com.foody.delivery.rpd.restapi.dto.user;

import com.foody.delivery.rpd.domain.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados públicos de um usuário")
public record UserResponse(
        @Schema(description = "Identificador do usuário", example = "11")
        Long id,

        @Schema(description = "Nome completo", example = "Maria Silva")
        String name,

        @Schema(description = "E-mail do usuário", example = "maria@email.com")
        String email,

        @Schema(description = "Papel do usuário no sistema", example = "CLIENT",
                allowableValues = {"CLIENT", "OPERATOR"})
        String role) {

    public static UserResponse from(final User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}
