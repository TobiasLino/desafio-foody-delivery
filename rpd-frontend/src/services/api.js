const API_URL =
    import.meta.env.API_URL || "http://rpd-backend:8080";

export async function listarPedidos(token) {
    const response = await fetch(`${API_URL}/api/pedidos`, {
        headers: {
            Authorization: `Bearer ${token}`,
        },
    });

    if (!response.ok) {
        throw new Error("Não foi possível carregar os pedidos.");
    }

    return response.json();
}