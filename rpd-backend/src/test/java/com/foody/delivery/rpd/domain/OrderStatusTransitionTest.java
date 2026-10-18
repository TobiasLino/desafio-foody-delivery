package com.foody.delivery.rpd.domain;


import com.foody.delivery.rpd.domain.address.Address;
import com.foody.delivery.rpd.domain.order.Order;
import com.foody.delivery.rpd.domain.order.OrderStatus;
import com.foody.delivery.rpd.domain.user.User;
import com.foody.delivery.rpd.infra.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

public class OrderStatusTransitionTest {

    @ParameterizedTest
    @CsvSource({
            "RECEBIDO, EM_PREPARO",
            "RECEBIDO, CANCELADO",
            "EM_PREPARO, SAIU_PARA_ENTREGA",
            "EM_PREPARO, CANCELADO",
            "SAIU_PARA_ENTREGA, ENTREGUE"
    })
    void shouldAllowValidTransitions(OrderStatus from, OrderStatus to) {
        assertTrue(from.canTransitionTo(to));
    }

    @ParameterizedTest
    @CsvSource({
            "RECEBIDO, ENTREGUE",
            "RECEBIDO, RECEBIDO",
            "EM_PREPARO, RECEBIDO",
            "SAIU_PARA_ENTREGA, CANCELADO",
            "ENTREGUE, RECEBIDO",
            "ENTREGUE, CANCELADO",
            "CANCELADO, EM_PREPARO"
    })
    void shouldRejectInvalidTransitions(OrderStatus from, OrderStatus to) {
        assertFalse(from.canTransitionTo(to));
    }

    @Test
    void shouldThrowWhenTransitionIsInvalid() {
        final Order order = new Order(new User(), new Address());

        assertThrows(InvalidStatusTransitionException.class,
                () -> order.updateStatus(OrderStatus.ENTREGUE));
    }
}
