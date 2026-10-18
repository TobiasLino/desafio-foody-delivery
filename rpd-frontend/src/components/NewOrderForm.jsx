import { useState } from "react";
import { createOrder } from "../services/api.js";
import ErrorMessage from "./ErrorMessage.jsx";

const EMPTY_ADDRESS = {
    cep: "",
    street: "",
    number: "",
    complement: "",
    neighborhood: "",
    city: "",
    uf: "",
};

// Campos do endereço. As regras (tamanhos, formato) são as mesmas validadas pelo back-end.
const ADDRESS_FIELDS = [
    { name: "cep", label: "CEP", required: true, maxLength: 8, inputMode: "numeric", pattern: "\\d{8}", title: "8 dígitos, sem hífen", placeholder: "12289020" },
    { name: "street", label: "Rua", required: true, maxLength: 250 },
    { name: "number", label: "Número", required: true, maxLength: 10 },
    { name: "complement", label: "Complemento", maxLength: 250 },
    { name: "neighborhood", label: "Bairro", required: true, maxLength: 250 },
    { name: "city", label: "Cidade", required: true, maxLength: 250 },
    { name: "uf", label: "UF", required: true, maxLength: 2, pattern: "[A-Za-z]{2}", title: "2 letras", placeholder: "SP" },
];

export default function NewOrderForm({ onCreated, onCancel }) {
    const [address, setAddress] = useState(EMPTY_ADDRESS);
    const [items, setItems] = useState([""]);
    const [error, setError] = useState(null);
    const [loading, setLoading] = useState(false);

    function handleAddressChange(event) {
        const { name } = event.target;
        let { value } = event.target;

        if (name === "cep") value = value.replace(/\D/g, "");
        if (name === "uf") value = value.toUpperCase().replace(/[^A-Z]/g, "");

        setAddress((previous) => ({ ...previous, [name]: value }));
    }

    function handleItemChange(index, value) {
        setItems((previous) => previous.map((item, i) => (i === index ? value : item)));
    }

    function addItem() {
        setItems((previous) => [...previous, ""]);
    }

    function removeItem(index) {
        setItems((previous) => previous.filter((_, i) => i !== index));
    }

    async function handleSubmit(event) {
        event.preventDefault();
        setError(null);

        const descriptions = items.map((item) => item.trim()).filter(Boolean);
        if (descriptions.length === 0) {
            setError({ message: "Adicione ao menos um item ao pedido." });
            return;
        }

        // Remove espaços das pontas e não envia complemento vazio.
        const cleanAddress = Object.fromEntries(
            Object.entries(address).map(([key, value]) => [key, value.trim()]),
        );
        if (!cleanAddress.complement) delete cleanAddress.complement;

        setLoading(true);
        try {
            const order = await createOrder({
                address: cleanAddress,
                items: descriptions.map((description) => ({ description })),
            });
            onCreated(order);
        } catch (err) {
            setError(err);
        } finally {
            setLoading(false);
        }
    }

    return (
        <form className="card form" onSubmit={handleSubmit}>
            <h2>Novo pedido</h2>

            <fieldset>
                <legend>Endereço de entrega</legend>
                <div className="grid">
                    {ADDRESS_FIELDS.map((field) => (
                        <label key={field.name} className={field.name === "street" ? "span-2" : undefined}>
                            {field.label}
                            <input
                                name={field.name}
                                value={address[field.name]}
                                onChange={handleAddressChange}
                                required={field.required}
                                maxLength={field.maxLength}
                                inputMode={field.inputMode}
                                pattern={field.pattern}
                                title={field.title}
                                placeholder={field.placeholder}
                            />
                        </label>
                    ))}
                </div>
            </fieldset>

            <fieldset>
                <legend>Itens</legend>
                {items.map((item, index) => (
                    // O índice serve de chave porque os itens não têm identificador antes de serem salvos.
                    <div className="item-row" key={index}>
                        <input
                            aria-label={`Item ${index + 1}`}
                            value={item}
                            onChange={(event) => handleItemChange(index, event.target.value)}
                            maxLength={250}
                            placeholder="Ex.: Pizza de calabresa"
                        />
                        <button
                            type="button"
                            className="btn btn-secondary"
                            onClick={() => removeItem(index)}
                            disabled={items.length === 1}
                        >
                            Remover
                        </button>
                    </div>
                ))}
                <button type="button" className="btn btn-secondary" onClick={addItem}>
                    + Adicionar item
                </button>
            </fieldset>

            <ErrorMessage error={error} />

            <div className="actions">
                <button type="submit" className="btn" disabled={loading}>
                    {loading ? "Enviando..." : "Criar pedido"}
                </button>
                <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={loading}>
                    Cancelar
                </button>
            </div>
        </form>
    );
}
