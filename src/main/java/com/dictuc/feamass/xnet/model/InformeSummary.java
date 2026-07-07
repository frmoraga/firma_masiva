package com.dictuc.feamass.xnet.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/** Refleja una fila de InformeRepository::listarPendientesFirmaPara() en el backend real. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InformeSummary {
    public long id;
    public String rutSolicitante;
    public String rutFacturador;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    public LocalDateTime fechaCreacion;

    public String estado;
    public String unidadNombre;
    public String categoriaInternaNombre;
}
