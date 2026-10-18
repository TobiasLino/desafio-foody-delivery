import { useCallback, useEffect, useState } from "react";
import ErrorMessage from "../components/ErrorMessage.jsx";
import NewOrderForm from "../components/NewOrderForm.jsx";
import OrderCard from "../components/OrderCard.jsx";
import { useAuth } from "../context/AuthContext.js";
import { listOrders, updateOrderStatus } from "../services/api.js";

const PAGE_SIZE = 10;

// Lista os pedidos (com paginação), permite criar um novo e atualizar o status.
// Cliente vê só os próprios pedidos; operador vê todos (quem filtra é o back-end).
export default function Orders() {
    const { user } = useAuth();
    const [orders, setOrders] = useState([]);
    const [pageInfo, setPageInfo] = useState({ number: 0, totalPages: 0, totalElements: 0 });
    const [pageNumber, setPageNumber] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [showForm, setShowForm] = useState(false);

    const loadOrders = useCallback(async (number) => {
        setLoading(true);
        setError(null);
        try {
            const data = await listOrders({ page: number, size: PAGE_SIZE });
            setOrders(data.content);
            setPageInfo(data.page);
        } catch (err) {
            setError(err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadOrders(pageNumber);
    }, [loadOrders, pageNumber]);

    function handleCreated() {
        setShowForm(false);
        // Os pedidos vêm do mais novo para o mais antigo, então o novo aparece na primeira página.
        if (pageNumber === 0) {
            loadOrders(0);
        } else {
            setPageNumber(0);
        }
    }

    async function handleStatusChange(orderId, status) {
        setError(null);
        try {
            const updated = await updateOrderStatus(orderId, status);
            setOrders((previous) => previous.map((order) => (order.id === updated.id ? updated : order)));
        } catch (err) {
            setError(err);
        }
    }

    return (
        <section>
            <div className="toolbar">
                <h2>{user.isOperator ? "Todos os pedidos" : "Meus pedidos"}</h2>
                <div className="actions">
                    <button type="button" className="btn btn-secondary" onClick={() => loadOrders(pageNumber)} disabled={loading}>
                        Atualizar
                    </button>
                    {!showForm && (
                        <button type="button" className="btn" onClick={() => setShowForm(true)}>
                            Novo pedido
                        </button>
                    )}
                </div>
            </div>

            {showForm && <NewOrderForm onCreated={handleCreated} onCancel={() => setShowForm(false)} />}

            <ErrorMessage error={error} />

            {loading && orders.length === 0 && <p className="muted">Carregando pedidos...</p>}

            {!loading && !error && orders.length === 0 && (
                <p className="muted">Nenhum pedido por aqui ainda. Clique em &quot;Novo pedido&quot; para começar.</p>
            )}

            <div className="order-list">
                {orders.map((order) => (
                    <OrderCard
                        key={order.id}
                        order={order}
                        isOperator={user.isOperator}
                        onStatusChange={handleStatusChange}
                    />
                ))}
            </div>

            {pageInfo.totalPages > 1 && (
                <nav className="pagination" aria-label="Paginação">
                    <button
                        type="button"
                        className="btn btn-secondary"
                        onClick={() => setPageNumber((current) => current - 1)}
                        disabled={loading || pageNumber === 0}
                    >
                        Anterior
                    </button>
                    <span>
                        Página {pageInfo.number + 1} de {pageInfo.totalPages} ({pageInfo.totalElements} pedidos)
                    </span>
                    <button
                        type="button"
                        className="btn btn-secondary"
                        onClick={() => setPageNumber((current) => current + 1)}
                        disabled={loading || pageNumber + 1 >= pageInfo.totalPages}
                    >
                        Próxima
                    </button>
                </nav>
            )}
        </section>
    );
}
