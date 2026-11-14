/*
 * "Esqueci minha senha": se o e-mail tem conta, envia o código para redefinir a senha. A resposta é sempre a
 * mesma, com ou sem conta, para não revelar quais e-mails estão cadastrados.
 */
package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.CodigosPorEmailService;
import com.medcare.senior.domain.email.FinalidadeDoCodigo;
import com.medcare.senior.domain.email.FormatoDeEmail;
import com.medcare.senior.domain.exception.EsperaParaReenviarException;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SolicitarRedefinicaoDeSenhaUseCase {

    private final CuidadorRepositoryPort cuidadorRepository;
    private final CodigosPorEmailService codigos;

    /**
     * "Esqueci minha senha": se o e-mail tem conta, envia um código para redefinir a senha. Para quem pede, o resultado
     * é sempre o mesmo (com ou sem conta, dentro ou fora da espera de 1 minuto): não revela quais e-mails estão
     * cadastrados.
     */
    public void executar(String email) {
        if (!FormatoDeEmail.valido(email)) {
            return;
        }
        cuidadorRepository.findByEmail(FormatoDeEmail.normalizar(email)).ifPresent(cuidador -> {
            try {
                codigos.enviar(cuidador, FinalidadeDoCodigo.REDEFINICAO_SENHA);
            } catch (EsperaParaReenviarException e) {
                // O código anterior, de menos de 1 minuto, continua valendo
            }
        });
    }
}
