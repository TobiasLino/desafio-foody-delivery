package com.foody.delivery.rpd.restapi.dto.order;

import com.foody.delivery.rpd.domain.order.Order;
import com.foody.delivery.rpd.restapi.dto.address.AddressResponse;
import com.foody.delivery.rpd.restapi.dto.item.ItemResponse;
import com.foody.delivery.rpd.restapi.dto.user.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Pedido e seu estado atual")
public record OrderResponse(
        @Schema(description = "Identificador do pedido", example = "10")
        Long id,

        @Schema(description = "Status atual do pedido", example = "RECEBIDO",
                allowableValues = {"RECEBIDO", "EM_PREPARO", "SAIU_PARA_ENTREGA", "ENTREGUE", "CANCELADO"})
        String status,

        @Schema(description = "Cliente que fez o pedido")
        UserResponse client,

        @Schema(description = "Endereço de entrega")
        AddressResponse address,

        @Schema(description = "Itens do pedido")
        List<ItemResponse> items
) {

    public static OrderResponse from(final Order order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                UserResponse.from(order.getClient()),
                AddressResponse.from(order.getAddress()),
                order.getItems().stream()
                        .map(ItemResponse::from)
                        .toList()
        );
    }
}
