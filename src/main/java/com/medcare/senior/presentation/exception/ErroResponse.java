package com.medcare.senior.presentation.exception;

import java.time.LocalDateTime;
import java.util.List;

public record ErroResponse(
        LocalDateTime timestamp,
        int status,
        String erro,
        String mensagem,
        List<CampoInvalido> campos
) {

    public record CampoInvalido(String campo, String mensagem) {
    }
}
