package com.foody.delivery.rpd.restapi.dto.item;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Item do pedido")
public record ItemRequest(
        @Schema(description = "Descrição do item", example = "Pizza de calabresa")
        @NotBlank @Size(max = 250) String description
) {
}
