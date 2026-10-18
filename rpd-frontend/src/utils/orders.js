// Regras de apresentação dos pedidos. As regras de verdade ficam no back-end;
// aqui só decidimos quais botões mostrar.

export const STATUS_LABELS = {
    RECEBIDO: "Recebido",
    EM_PREPARO: "Em preparo",
    SAIU_PARA_ENTREGA: "Saiu para entrega",
    ENTREGUE: "Entregue",
    CANCELADO: "Cancelado",
};

export const ACTION_LABELS = {
    EM_PREPARO: "Iniciar preparo",
    SAIU_PARA_ENTREGA: "Saiu para entrega",
    ENTREGUE: "Marcar como entregue",
    CANCELADO: "Cancelar pedido",
};

// Transições permitidas (mesma tabela do README do back-end).
const NEXT_STATUS = {
    RECEBIDO: ["EM_PREPARO", "CANCELADO"],
    EM_PREPARO: ["SAIU_PARA_ENTREGA", "CANCELADO"],
    SAIU_PARA_ENTREGA: ["ENTREGUE"],
    ENTREGUE: [],
    CANCELADO: [],
};

// Operador: qualquer transição válida. Cliente: apenas cancelar enquanto estiver RECEBIDO.
export function availableActions(order, isOperator) {
    if (isOperator) return NEXT_STATUS[order.status] ?? [];
    return order.status === "RECEBIDO" ? ["CANCELADO"] : [];
}

export function formatCep(cep) {
    return cep && cep.length === 8 ? `${cep.slice(0, 5)}-${cep.slice(5)}` : cep;
}
