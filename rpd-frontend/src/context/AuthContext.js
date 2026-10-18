import { createContext, useContext } from "react";

export const AuthContext = createContext(null);

// Uso: const { user, login, logout } = useAuth();
export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error("useAuth deve ser usado dentro de <AuthProvider>");
    }
    return context;
}
