// Lê os dados do token JWT (sem validar a assinatura: quem valida é o back-end).
// Serve apenas para a interface saber quem está logado, qual o papel e quando o token expira.
export function parseToken(token) {
    if (!token) return null;

    try {
        const base64 = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
        const claims = JSON.parse(atob(base64));

        if (!claims.sub || !claims.exp) return null;
        if (claims.exp * 1000 <= Date.now()) return null; // token expirado

        return {
            email: claims.sub,
            expiresAt: claims.exp * 1000,
            roles: (claims.scope || "").split(" ").filter(Boolean),
        };
    } catch {
        return null;
    }
}
