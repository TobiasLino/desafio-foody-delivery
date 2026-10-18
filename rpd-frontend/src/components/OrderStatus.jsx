function OrderStatus({ status }) {
    return (
        <span className={`status status-${status.toLowerCase()}`}>
      {status.replaceAll("_", " ")}
    </span>
    );
}

export default OrderStatus;