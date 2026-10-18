import "./App.css";
import { useAuth } from "./context/AuthContext.js";
import AuthPage from "./pages/AuthPage.jsx";
import Orders from "./pages/Orders.jsx";

export default function App() {
    const { user, logout } = useAuth();

    return (
        <div className="app">
            <header className="app-header">
                <h1>Foody Delivery</h1>
                {user && (
                    <div className="user-box">
                        <span>{user.email}</span>
                        <span className="role">{user.isOperator ? "Operador" : "Cliente"}</span>
                        <button type="button" className="btn btn-secondary" onClick={logout}>
                            Sair
                        </button>
                    </div>
                )}
            </header>

            <main>{user ? <Orders /> : <AuthPage />}</main>
        </div>
    );
}
