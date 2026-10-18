package com.foody.delivery.rpd.restapi.dto.order;

import com.foody.delivery.rpd.domain.order.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Novo status do pedido")
public record UpdateStatusRequest(
        @Schema(description = "Status de destino. Transições permitidas: RECEBIDO → EM_PREPARO/CANCELADO, "
                + "EM_PREPARO → SAIU_PARA_ENTREGA/CANCELADO, SAIU_PARA_ENTREGA → ENTREGUE.",
                example = "EM_PREPARO")
        @NotNull(message = "Status é obrigatório")
        OrderStatus status) {
}
