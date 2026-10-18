package com.foody.delivery.rpd.restapi.dto;

import com.foody.delivery.rpd.domain.address.Address;
import com.foody.delivery.rpd.domain.item.Item;
import com.foody.delivery.rpd.domain.order.Order;
import com.foody.delivery.rpd.domain.order.OrderStatus;
import com.foody.delivery.rpd.domain.user.Role;
import com.foody.delivery.rpd.domain.user.User;
import com.foody.delivery.rpd.restapi.dto.address.AddressRequest;
import com.foody.delivery.rpd.restapi.dto.address.AddressResponse;
import com.foody.delivery.rpd.restapi.dto.item.ItemResponse;
import com.foody.delivery.rpd.restapi.dto.order.OrderResponse;
import com.foody.delivery.rpd.restapi.dto.user.UserResponse;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;


public class DTOMappingTest {

    @Test
    void shouldCopyAllFieldToAddress() {
        final AddressRequest request = new AddressRequest(
                "12289020",
                "Avenida Brasil",
                "Centro",
                null,
                "1",
                "Caçapava",
                "SP");
        final Address address = request.toAddress();

        assertEquals("12289020", address.getCep());
        assertEquals("Avenida Brasil", address.getStreet());
        assertEquals("Centro", address.getNeighborhood());
        assertNull(address.getComplement());
        assertEquals("1", address.getNumber());
        assertEquals("Caçapava", address.getCity());
        assertEquals("SP", address.getUf());
    }

    @Test
    void shouldBuildAResponseFromAddress() {
        final Address address = Address.builder()
                .id(1L)
                .cep("12289020")
                .street("Avenida Brasil")
                .neighborhood("Centro")
                .complement("casa")
                .number("1")
                .city("Caçapava")
                .uf("SP")
                .build();
        final AddressResponse response = AddressResponse.from(address);

        assertEquals(1L, response.id());
        assertEquals("12289020", response.cep());
        assertEquals("Avenida Brasil", response.street());
        assertEquals("Centro", response.neighborhood());
        assertEquals("casa", response.complement());
        assertEquals("1", response.number());
        assertEquals("Caçapava", response.city());
        assertEquals("SP", response.uf());
    }

    @Test
    void shouldBuildAResponseFromItem() {
        final Item item = new Item(1L, "teste");
        final ItemResponse response = ItemResponse.from(item);

        assertEquals(1L, response.id());
        assertEquals("teste", response.description());
    }

    @Test
    void shouldBuildAResponseFromOrder() {
        final Order order = new Order(1L, OrderStatus.RECEBIDO, new Address(), new User(), Set.of(new Item()));
        final OrderResponse response = OrderResponse.from(order);

        assertEquals(1L, response.id());
        assertEquals(OrderStatus.RECEBIDO.name(), response.status());
        assertNotNull(response.client());
        assertNotNull(response.address());
        assertNotNull(response.items());
    }

    @Test
    void shouldBuildAResponseFromUser() {
        final User user = new User("Teste", "teste@teste.com", "hash", Role.CLIENT);
        user.setId(1L);
        final UserResponse response = UserResponse.from(user);

        assertEquals(1L, response.id());
        assertEquals("Teste", response.name());
        assertEquals("teste@teste.com", response.email());
        assertEquals(Role.CLIENT.name(), response.role());
    }
}
