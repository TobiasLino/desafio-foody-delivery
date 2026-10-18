package com.foody.delivery.rpd.domain.order;

import com.foody.delivery.rpd.domain.item.Item;
import com.foody.delivery.rpd.domain.user.User;
import com.foody.delivery.rpd.domain.user.UserService;
import com.foody.delivery.rpd.infra.exception.InvalidStatusTransitionException;
import com.foody.delivery.rpd.infra.exception.OrderAccessDeniedException;
import com.foody.delivery.rpd.infra.exception.OrderNotFoundException;
import com.foody.delivery.rpd.infra.security.AuthenticatedUser;
import com.foody.delivery.rpd.restapi.dto.order.OrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.AccessDeniedException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;

    private final UserService userService;

    public Page<Order> findAll(final Long clientId, Pageable pageable, final AuthenticatedUser requester) throws OrderAccessDeniedException {
        if (requester.operator()) {
            return clientId != null ? findByClient(clientId, pageable) : repository.findAll(pageable);
        }

        final Long owner = userService.findByEmail(requester.email()).getId();
        if (clientId != null && !clientId.equals(owner)) {
            throw new OrderAccessDeniedException("Você só pode consultar seus próprios pedidos");
        }
        return findByClient(owner, pageable);
    }

    private Order findById(final Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Order findById(final Long id, final AuthenticatedUser requester) {
        final Order order = findById(id);
        if (!requester.operator() && !order.getClient().getEmail().equals(requester.email())) {
            throw new OrderNotFoundException(id);
        }
        return order;
    }

    public Page<Order> findByClient(final Long clientId, Pageable pageable) {
        return repository.findByClientId(clientId, pageable);
    }

    @Transactional
    public Order updateStatus(final Long id, final OrderStatus status, final AuthenticatedUser requester) throws AccessDeniedException {
        final Order order = findById(id, requester);

        if (!requester.operator()) {
            if (status != OrderStatus.CANCELADO) {
                throw new OrderAccessDeniedException("Clientes só podem cancelar pedidos");
            }
            if (order.getStatus() != OrderStatus.RECEBIDO) {
                throw new InvalidStatusTransitionException(order.getStatus(), status);
            }
        }

        order.updateStatus(status);
        return repository.save(order);
    }

    @Transactional
    public Order createNewOrder(final OrderRequest request, final String userEmail) {
        final User client = userService.findByEmail(userEmail);

        final Order order = new Order(client, request.address().toAddress());

        order.addItems(request.items().stream()
                .map(itemRequest -> new Item(itemRequest.description()))
                .collect(Collectors.toSet()));

        return repository.save(order);
    }
}
