import { STATUS_LABELS } from "../utils/orders.js";

// Selo colorido com o status do pedido. As cores estão em App.css (.status-recebido, .status-em_preparo...).
export default function OrderStatus({ status }) {
    return (
        <span className={`status status-${status.toLowerCase()}`}>
            {STATUS_LABELS[status] ?? status}
        </span>
    );
}
