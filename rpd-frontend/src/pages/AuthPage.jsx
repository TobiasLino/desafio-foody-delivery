import { useState } from "react";
import ErrorMessage from "../components/ErrorMessage.jsx";
import { useAuth } from "../context/AuthContext.js";
import { register } from "../services/api.js";

// Tela de login e cadastro. Depois de cadastrar, já faz o login automaticamente.
export default function AuthPage() {
    const { login } = useAuth();
    const [mode, setMode] = useState("login");
    const [form, setForm] = useState({ name: "", email: "", password: "" });
    const [error, setError] = useState(null);
    const [loading, setLoading] = useState(false);

    const isRegister = mode === "register";

    function handleChange(event) {
        const { name, value } = event.target;
        setForm((previous) => ({ ...previous, [name]: value }));
    }

    function switchMode() {
        setMode(isRegister ? "login" : "register");
        setError(null);
    }

    async function handleSubmit(event) {
        event.preventDefault();
        setError(null);
        setLoading(true);

        try {
            if (isRegister) {
                await register(form.name.trim(), form.email.trim(), form.password);
            }
            await login(form.email.trim(), form.password);
        } catch (err) {
            setError(err);
        } finally {
            setLoading(false);
        }
    }

    return (
        <form className="card form auth-form" onSubmit={handleSubmit}>
            <h2>{isRegister ? "Criar conta" : "Entrar"}</h2>

            {isRegister && (
                <label>
                    Nome
                    <input name="name" value={form.name} onChange={handleChange} required maxLength={250} autoComplete="name" />
                </label>
            )}

            <label>
                E-mail
                <input name="email" type="email" value={form.email} onChange={handleChange} required maxLength={250} autoComplete="email" />
            </label>

            <label>
                Senha
                <input
                    name="password"
                    type="password"
                    value={form.password}
                    onChange={handleChange}
                    required
                    minLength={isRegister ? 8 : undefined}
                    maxLength={72}
                    autoComplete={isRegister ? "new-password" : "current-password"}
                />
                {isRegister && <small className="muted">Entre 8 e 72 caracteres.</small>}
            </label>

            <ErrorMessage error={error} />

            <button type="submit" className="btn" disabled={loading}>
                {loading ? "Aguarde..." : isRegister ? "Cadastrar" : "Entrar"}
            </button>

            <button type="button" className="link" onClick={switchMode}>
                {isRegister ? "Já tenho conta" : "Ainda não tenho conta"}
            </button>
        </form>
    );
}
