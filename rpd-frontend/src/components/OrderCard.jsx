import { useState } from "react";
import OrderStatus from "./OrderStatus.jsx";
import { ACTION_LABELS, availableActions, formatCep } from "../utils/orders.js";

export default function OrderCard({ order, isOperator, onStatusChange }) {
    const [busy, setBusy] = useState(false);

    const { address, client, items } = order;
    const actions = availableActions(order, isOperator);

    async function handleAction(status) {
        if (status === "CANCELADO" && !window.confirm(`Cancelar o pedido #${order.id}?`)) {
            return;
        }

        setBusy(true);
        try {
            await onStatusChange(order.id, status);
        } finally {
            setBusy(false);
        }
    }

    return (
        <article className="order-card">
            <header className="order-card-header">
                <h3>Pedido #{order.id}</h3>
                <OrderStatus status={order.status} />
            </header>

            {isOperator && (
                <p className="muted">
                    Cliente: {client.name} ({client.email})
                </p>
            )}

            <p>
                <strong>Entrega:</strong> {address.street}, {address.number}
                {address.complement ? ` - ${address.complement}` : ""} · {address.neighborhood} ·{" "}
                {address.city}/{address.uf} · CEP {formatCep(address.cep)}
            </p>

            <ul className="order-items">
                {items.map((item) => (
                    <li key={item.id}>{item.description}</li>
                ))}
            </ul>

            {actions.length > 0 && (
                <div className="actions">
                    {actions.map((status) => (
                        <button
                            key={status}
                            type="button"
                            className={status === "CANCELADO" ? "btn btn-danger" : "btn"}
                            disabled={busy}
                            onClick={() => handleAction(status)}
                        >
                            {ACTION_LABELS[status]}
                        </button>
                    ))}
                </div>
            )}
        </article>
    );
}
