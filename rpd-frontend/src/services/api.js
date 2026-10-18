// Único lugar que conversa com o back-end.
// A URL vem da variável VITE_API_URL (veja o arquivo .env e o docker-compose do front).
const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";

const TOKEN_KEY = "token";

export const tokenStorage = {
    get: () => localStorage.getItem(TOKEN_KEY),
    set: (token) => localStorage.setItem(TOKEN_KEY, token),
    clear: () => localStorage.removeItem(TOKEN_KEY),
};

// Chamada quando o back responde 401 para uma requisição autenticada (token vencido ou inválido).
// O AuthProvider registra aqui a função de logout.
let unauthorizedHandler = () => {};
export function setUnauthorizedHandler(handler) {
    unauthorizedHandler = handler;
}

// O back devolve erros no formato ProblemDetail: { status, detail, errors: [{ field, message }] }
export class ApiError extends Error {
    constructor(message, status, fieldErrors = []) {
        super(message);
        this.name = "ApiError";
        this.status = status;
        this.fieldErrors = fieldErrors;
    }
}

const DEFAULT_MESSAGES = {
    400: "Dados inválidos.",
    401: "Sessão expirada. Faça login novamente.",
    403: "Você não tem permissão para esta ação.",
    404: "Registro não encontrado.",
    409: "A operação não pôde ser concluída por conflito com os dados atuais.",
};

async function request(path, { method = "GET", body, auth = true } = {}) {
    const headers = {};
    if (body !== undefined) headers["Content-Type"] = "application/json";

    // Login e cadastro são públicos: não enviamos o token, para um token vencido não atrapalhar.
    const token = tokenStorage.get();
    if (auth && token) headers.Authorization = `Bearer ${token}`;

    let response;
    try {
        response = await fetch(`${API_URL}${path}`, {
            method,
            headers,
            body: body !== undefined ? JSON.stringify(body) : undefined,
        });
    } catch {
        throw new ApiError("Não foi possível conectar ao servidor. Tente novamente em instantes.", 0);
    }

    if (response.ok) {
        return response.status === 204 ? null : response.json();
    }

    const problem = await response.json().catch(() => null);

    if (response.status === 401 && auth && token) {
        unauthorizedHandler();
    }

    throw new ApiError(
        problem?.detail || DEFAULT_MESSAGES[response.status] || "Erro inesperado no servidor.",
        response.status,
        problem?.errors ?? [],
    );
}

// --- Autenticação ---------------------------------------------------------------------------

export const register = (name, email, password) =>
    request("/auth/register", { method: "POST", body: { name, email, password }, auth: false });

export const login = (email, password) =>
    request("/auth/login", { method: "POST", body: { email, password }, auth: false });

// --- Pedidos --------------------------------------------------------------------------------

// Retorna { content: [pedidos], page: { size, number, totalElements, totalPages } }
export function listOrders({ page = 0, size = 10, sort = "id,desc" } = {}) {
    const params = new URLSearchParams({ page, size, sort });
    return request(`/orders?${params}`);
}

export const getOrder = (id) => request(`/orders/${id}`);

export const createOrder = (order) => request("/orders", { method: "POST", body: order });

export const updateOrderStatus = (id, status) =>
    request(`/orders/${id}/status`, { method: "PATCH", body: { status } });
