import OrderStatus from "./OrderStatus.jsx";

function OrderCard({ order }) {
  return (
    <article className="order-card">
      <h2>Pedido #{order.id}</h2>
      <p>Cliente: {order.cliente}</p>
      <OrderStatus status={order.status} />
    </article>
  );
}