package com.foody.delivery.rpd.restapi.dto.item;

import com.foody.delivery.rpd.domain.item.Item;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Item do pedido")
public record ItemResponse(
        @Schema(description = "Identificador do item", example = "10") Long id,
        @Schema(description = "Descrição do item", example = "Pizza de calabresa") String description
) {

    public static ItemResponse from(final Item item) {
        return new ItemResponse(item.getId(), item.getDescription());
    }
}
