package com.foody.delivery.rpd.infra.exception;

import com.foody.delivery.rpd.domain.order.OrderStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(final OrderStatus from, final OrderStatus to) {
        super("Transição de status inválida: " + from + " → " + to);
    }
}
