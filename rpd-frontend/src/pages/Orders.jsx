import { useEffect, useState } from "react";
import { listarPedidos } from "../services/api";

export default function Orders() {
    const [pedidos, setPedidos] = useState([]);
    const [erro, setErro] = useState("");

    useEffect(() => {
        async function carregarPedidos() {
            try {
                const token = localStorage.getItem("token");
                const dados = await listarPedidos(token);

                setPedidos(dados);
            } catch (error) {
                setErro(error.message);
            }
        }

        carregarPedidos();
    }, []);

    if (erro) {
        return <p>{erro}</p>;
    }

    return (
        <main>
            <h1>Pedidos</h1>

            {pedidos.map((pedido) => (
                <article key={pedido.id}>
                    <h2>Pedido #{pedido.id}</h2>
                    <p>{pedido.cliente}</p>
                    <p>{pedido.status}</p>
                </article>
            ))}
        </main>
    );
}