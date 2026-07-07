package com.dictuc.feamass.xnet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Refleja Response::error() del backend real: {"error": "mensaje", "errores": [...]?}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorResponseDto {
    public String error;
}
