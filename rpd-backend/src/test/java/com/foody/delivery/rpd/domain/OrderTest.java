package com.foody.delivery.rpd.domain;

import com.foody.delivery.rpd.domain.address.Address;
import com.foody.delivery.rpd.domain.item.Item;
import com.foody.delivery.rpd.domain.order.Order;
import com.foody.delivery.rpd.domain.order.OrderStatus;
import com.foody.delivery.rpd.domain.user.User;
import org.junit.jupiter.api.Test;


import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class OrderTest {

    @Test
    void shouldBuildOrderWithDefaultStatus() {
        final Order order = new Order(new User(), new Address());
        assertEquals(OrderStatus.RECEBIDO, order.getStatus());
    }

    @Test
    void shouldInitiateItemsCollection() {
        final Order order = new Order(new User(), new Address());
        assertNull(order.getItems());

        final Set<Item> items = new HashSet<>();
        items.add(new Item("teste"));
        order.addItems(items);

        assertNotNull(order.getItems());
        assertEquals(1, order.getItems().size());
    }

    @Test
    void shouldAccumulateItems() {

        final Order order = new Order(new User(), new Address());
        assertNull(order.getItems());

        final Set<Item> items = new HashSet<>();
        items.add(new Item("teste"));
        order.addItems(items);
        order.addItems(items);

        assertNotNull(order.getItems());
        assertEquals(1, order.getItems().size());
    }
}
