import { useCallback, useEffect, useMemo, useState } from "react";
import { AuthContext } from "./AuthContext.js";
import * as api from "../services/api.js";
import { parseToken } from "../utils/jwt.js";

// Token guardado de uma visita anterior só vale se ainda não expirou.
function loadStoredToken() {
    const token = api.tokenStorage.get();
    return parseToken(token) ? token : null;
}

export function AuthProvider({ children }) {
    const [token, setToken] = useState(loadStoredToken);

    const logout = useCallback(() => {
        api.tokenStorage.clear();
        setToken(null);
    }, []);

    const login = useCallback(async (email, password) => {
        const { token: newToken } = await api.login(email, password);
        api.tokenStorage.set(newToken);
        setToken(newToken);
    }, []);

    // Se o back responder 401 com o usuário logado, encerra a sessão.
    useEffect(() => {
        api.setUnauthorizedHandler(logout);
    }, [logout]);

    const user = useMemo(() => {
        const claims = parseToken(token);
        if (!claims) return null;
        return {
            email: claims.email,
            isOperator: claims.roles.includes("ROLE_OPERATOR"),
            expiresAt: claims.expiresAt,
        };
    }, [token]);

    // Desloga sozinho quando o token expirar.
    useEffect(() => {
        if (!user) return undefined;
        const remaining = Math.min(user.expiresAt - Date.now(), 2 ** 31 - 1);
        const timer = setTimeout(logout, Math.max(remaining, 0));
        return () => clearTimeout(timer);
    }, [user, logout]);

    const value = useMemo(() => ({ user, login, logout }), [user, login, logout]);

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
