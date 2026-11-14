package com.medcare.senior.domain.usuario;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cuidador {

    private Long id;
    private String nome;
    private String email;
    private String senhaHash;
    private String telefone;
    private LocalDateTime dataCadastro;
    private boolean consentimentoLgpd;
    private LocalDateTime dataConsentimento;

    /**
     * Token FCM do aparelho do cuidador, para avisos push. Dado pessoal (LGPD). {@code null} = sem aparelho registrado.
     */
    private String fcmToken;

    /**
     * O cuidador confirmou o e-mail com o código enviado a ele (ou redefiniu a senha por e-mail). Sem isso, não cadastra
     * pacientes. Contas anteriores à confirmação contam como confirmadas (ver V20).
     */
    @Builder.Default
    private boolean emailVerificado = true;
}
