package com.foody.delivery.rpd.restapi.dto.order;

import com.foody.delivery.rpd.restapi.dto.address.AddressRequest;
import com.foody.delivery.rpd.restapi.dto.item.ItemRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "Dados para criação de um pedido. O cliente é o usuário autenticado (vem do token).")
public record OrderRequest(
        @Schema(description = "Endereço de entrega")
        @Valid @NotNull AddressRequest address,

        @Schema(description = "Itens do pedido (mínimo de 1)")
        @Valid @NotEmpty List<ItemRequest> items
) {
}
