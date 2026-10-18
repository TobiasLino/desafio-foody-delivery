package com.foody.delivery.rpd.infra.exception;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(final Long id) {
        super("Pedido " + id + " não encontrado");
    }
}
