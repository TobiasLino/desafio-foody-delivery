package com.foody.delivery.rpd.domain;

import com.foody.delivery.rpd.domain.address.Address;
import com.foody.delivery.rpd.domain.order.Order;
import com.foody.delivery.rpd.domain.order.OrderRepository;
import com.foody.delivery.rpd.domain.order.OrderService;
import com.foody.delivery.rpd.domain.order.OrderStatus;
import com.foody.delivery.rpd.domain.user.Role;
import com.foody.delivery.rpd.domain.user.User;
import com.foody.delivery.rpd.domain.user.UserService;
import com.foody.delivery.rpd.infra.exception.InvalidStatusTransitionException;
import com.foody.delivery.rpd.infra.exception.OrderAccessDeniedException;
import com.foody.delivery.rpd.infra.exception.OrderNotFoundException;
import com.foody.delivery.rpd.infra.exception.UserNotFoundException;
import com.foody.delivery.rpd.infra.security.AuthenticatedUser;
import com.foody.delivery.rpd.restapi.dto.address.AddressRequest;
import com.foody.delivery.rpd.restapi.dto.item.ItemRequest;
import com.foody.delivery.rpd.restapi.dto.order.OrderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.nio.file.AccessDeniedException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private UserService userService;

    @Mock
    private OrderRepository orderRepository;

    private AddressRequest getAddressRequest() {
        return new AddressRequest("12289020", "Avenida Brasil", "Centro", "", "1", "Caçapava", "SP");
    }

    private List<ItemRequest> getItems() {
        return new LinkedList<>() {{
            add(new ItemRequest("teste1"));
            add(new ItemRequest("teste2"));
        }};
    }

    private User getUser() {
        return new User("Teste", "teste@teste.com", "hash");
    }

    @Test
    void shouldCreateNewOrder() {
        when(userService.findByEmail("teste@teste.com")).thenReturn(getUser());
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        final Order order = orderService.createNewOrder(new OrderRequest(getAddressRequest(), getItems()), "teste@teste.com");
        assertEquals("teste@teste.com", order.getClient().getEmail());
        assertEquals(OrderStatus.RECEBIDO, order.getStatus());
        assertEquals(2, order.getItems().size());

        final ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        final Order saved = captor.getValue();

        assertEquals("Teste", saved.getClient().getName());
        assertEquals("teste@teste.com", saved.getClient().getEmail());
        assertEquals(OrderStatus.RECEBIDO, saved.getStatus());
        assertEquals(2, saved.getItems().size());
    }

    @Test
    void shouldThrowUserNotFoundExceptionWhenUserWIthEmailNotExists() {
        when(userService.findByEmail("teste@teste.com")).thenThrow(new UserNotFoundException("Usuário com email teste@teste.com não encontrado"));

        assertThrows(UserNotFoundException.class,
                () -> orderService.createNewOrder(new OrderRequest(getAddressRequest(), getItems()), "teste@teste.com"));

        verify(orderRepository, never()).save(any());
    }

    private AuthenticatedUser getAuthenticatedUser() {
        return new AuthenticatedUser("teste@teste.com", true);
    }

    @Test
    void shouldThrowOrderNotFoundExceptionWhenSearchForID() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class,
                () -> orderService.findById(1L, getAuthenticatedUser()));
    }

    //@Test
    void shouldCallRepositoryFindAll() throws AccessDeniedException {
        final Pageable pageable = PageRequest.of(0, 20);
        final Page<Order> expected = new PageImpl<>(List.of());
        when(orderRepository.findAll(pageable)).thenReturn(expected);

        final Page<Order> result = orderService.findAll(null, pageable, getAuthenticatedUser());

        assertSame(expected, result);
        verify(orderRepository).findAll(pageable);
        verify(orderRepository, never()).findByClientId(anyLong(), any());
    }

    //@Test
    void shouldCallRepositoryFindByClient() throws AccessDeniedException {
        final Pageable pageable = PageRequest.of(0, 20);
        final Page<Order> expected = new PageImpl<>(List.of());
        when(orderRepository.findByClientId(1L, pageable)).thenReturn(expected);

        final Page<Order> result = orderService.findAll(1L, pageable, getAuthenticatedUser());

        assertSame(expected, result);
        verify(orderRepository).findByClientId(1L, pageable);
        verify(orderRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void shouldUpdateOrderStatus() throws AccessDeniedException {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(new Order(getUser(), new Address())));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        final Order order = orderService.findById(1L, getAuthenticatedUser());
        assertEquals(OrderStatus.RECEBIDO, order.getStatus());

        final Order updated = orderService.updateStatus(1L, OrderStatus.EM_PREPARO, getAuthenticatedUser());
        assertEquals(OrderStatus.EM_PREPARO, updated.getStatus());

        final ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        final Order saved = captor.getValue();

        assertEquals(OrderStatus.EM_PREPARO, saved.getStatus());
    }

    @Test
    void shouldThrowExceptionWhenInvalidStatus() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(new Order(getUser(), new Address())));

        assertThrows(InvalidStatusTransitionException.class,
                () -> orderService.updateStatus(1L, OrderStatus.ENTREGUE, getAuthenticatedUser()));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenOrderNotExists() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class,
                () -> orderService.updateStatus(1L, OrderStatus.EM_PREPARO, getAuthenticatedUser()));
    }

    @Test
    void clientShouldNotSeeOrderFromAnotherClient() {
        final User owner = new User("Dono", "dono@teste.com", "hash");
        final Order order = new Order(owner, new Address());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(OrderNotFoundException.class,
                () -> orderService.findById(1L, new AuthenticatedUser("outro@teste.com", false)));
    }

    @Test
    void clientCanOnlyCancel() {
        final Order order = new Order(new User("Teste", "teste@teste.com", "hash"), new Address());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(OrderAccessDeniedException.class,
                () -> orderService.updateStatus(1L, OrderStatus.ENTREGUE, new AuthenticatedUser("teste@teste.com", false)));
    }

    @Test
    void clientCannotCancelAfterPreparationStarted() {
        final Order order = new Order(new User("Teste", "teste@teste.com", "hash"), new Address());
        order.updateStatus(OrderStatus.EM_PREPARO);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(InvalidStatusTransitionException.class,
                () -> orderService.updateStatus(1L, OrderStatus.CANCELADO, new AuthenticatedUser("teste@teste.com", false)));
    }
}
