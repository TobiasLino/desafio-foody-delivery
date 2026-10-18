// Mostra o erro devolvido pelo back-end: a mensagem principal e, se houver, os erros por campo.
const FIELD_LABELS = {
    name: "Nome",
    email: "E-mail",
    password: "Senha",
    items: "Itens",
    status: "Status",
    "address.cep": "CEP",
    "address.street": "Rua",
    "address.neighborhood": "Bairro",
    "address.complement": "Complemento",
    "address.number": "Número",
    "address.city": "Cidade",
    "address.uf": "UF",
};

function labelFor(field) {
    // Ex.: "items[0].description" -> "Item 1"
    const item = /^items\[(\d+)\]/.exec(field);
    if (item) return `Item ${Number(item[1]) + 1}`;
    return FIELD_LABELS[field] ?? field;
}

export default function ErrorMessage({ error }) {
    if (!error) return null;

    return (
        <div className="error" role="alert">
            <p>{error.message}</p>
            {error.fieldErrors?.length > 0 && (
                <ul>
                    {error.fieldErrors.map((fieldError) => (
                        <li key={`${fieldError.field}-${fieldError.message}`}>
                            <strong>{labelFor(fieldError.field)}:</strong> {fieldError.message}
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}
