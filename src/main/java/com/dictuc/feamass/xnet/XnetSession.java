package com.dictuc.feamass.xnet;

/**
 * Sesion autenticada contra XNET: sostiene el mismo XnetClient usado para el login
 * (que conserva la cookie de sesion internamente) y el token CSRF requerido para las
 * llamadas que mutan estado. Vive solo en memoria, nunca se persiste a disco.
 */
public final class XnetSession {

    private final XnetClient client;
    private final String csrfToken;
    private final long usuarioDictucId;
    private final String rutUsuario;
    private final String nombreUsuario;

    public XnetSession(XnetClient client, String csrfToken, long usuarioDictucId, String rutUsuario, String nombreUsuario) {
        this.client = client;
        this.csrfToken = csrfToken;
        this.usuarioDictucId = usuarioDictucId;
        this.rutUsuario = rutUsuario;
        this.nombreUsuario = nombreUsuario;
    }

    public XnetClient client() {
        return client;
    }

    public String csrfToken() {
        return csrfToken;
    }

    public long usuarioDictucId() {
        return usuarioDictucId;
    }

    public String rutUsuario() {
        return rutUsuario;
    }

    public String nombreUsuario() {
        return nombreUsuario;
    }
}
