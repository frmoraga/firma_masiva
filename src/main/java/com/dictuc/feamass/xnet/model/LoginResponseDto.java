package com.dictuc.feamass.xnet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Refleja InformeController::perfilActual() en el backend real de XNET (audiencia admin). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginResponseDto {
    public UsuarioDictucDto usuario;
    public List<String> permisos;
    public String csrfToken;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UsuarioDictucDto {
        public long id;
        public String rut;
        public String nombre;
        public long unidadId;
        public boolean habilitadoParaFirmar;
        public String email;
        public boolean activo;
    }
}
