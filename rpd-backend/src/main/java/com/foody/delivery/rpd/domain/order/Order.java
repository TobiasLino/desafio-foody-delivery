package com.foody.delivery.rpd.domain.order;

import com.foody.delivery.rpd.domain.address.Address;
import com.foody.delivery.rpd.domain.item.Item;
import com.foody.delivery.rpd.domain.user.User;
import com.foody.delivery.rpd.infra.exception.InvalidStatusTransitionException;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "rpd_order")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter(AccessLevel.NONE)
@Builder
public class Order implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "id_address", referencedColumnName = "id")
    private Address address;

    @ManyToOne
    @JoinColumn(name = "id_client", referencedColumnName = "id")
    private User client;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(name = "rpd_order_item",
            joinColumns = @JoinColumn(name = "id_order"),
            inverseJoinColumns = @JoinColumn(name = "id_item"))
    private Set<Item> items;


    public void updateStatus(final OrderStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(this.status, newStatus);
        }
        this.status = newStatus;
    }

    public void addItems(final Set<Item> items) {
        if (getItems() == null) {
            this.items = new HashSet<>();
        }
        this.items.addAll(items);
    }

    public Order(User client, Address address) {
        this.client = client;
        this.address = address;
        this.status = OrderStatus.RECEBIDO;
    }
}
