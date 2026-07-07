package com.dictuc.feamass.xnet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** El backend devuelve la fila completa de informe; solo nos interesan estos campos. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmarResponseDto {
    public long id;
    public String estado;
}
