/*
 * Troca a senha de quem recebeu o código no e-mail ("esqueci minha senha") e confirma o e-mail, já que o código
 * chegou nele. A senha nova precisa ser forte (RN-002) e é conferida antes de gastar o código. E-mail sem conta
 * responde igual a código inválido.
 */
package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.CodigosPorEmailService;
import com.medcare.senior.domain.autenticacao.PasswordHasherPort;
import com.medcare.senior.domain.email.FinalidadeDoCodigo;
import com.medcare.senior.domain.email.FormatoDeEmail;
import com.medcare.senior.domain.email.PoliticaDeSenha;
import com.medcare.senior.domain.exception.CodigoPorEmailInvalidoException;
import com.medcare.senior.domain.exception.SenhaFracaException;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RedefinirSenhaUseCase {

    private final CuidadorRepositoryPort cuidadorRepository;
    private final CodigosPorEmailService codigos;
    private final PasswordHasherPort passwordHasher;

    /**
     * Troca a senha de quem recebeu o código no e-mail e confirma o e-mail (o código chegou nele). E-mail sem conta
     * responde como código que não vale mais.
     *
     * @throws SenhaFracaException             senha nova fora da RN-002 (conferida antes; o código não é gasto)
     * @throws CodigoPorEmailInvalidoException código errado, vencido, bloqueado ou e-mail sem conta
     */
    public void executar(String email, String codigo, String novaSenha) {
        if (!PoliticaDeSenha.forte(novaSenha)) {
            throw new SenhaFracaException();
        }
        Optional<Cuidador> conta = FormatoDeEmail.valido(email)
                ? cuidadorRepository.findByEmail(FormatoDeEmail.normalizar(email))
                : Optional.empty();
        Cuidador cuidador = conta.orElseThrow(CodigoPorEmailInvalidoException::pecaOutro);
        codigos.conferir(cuidador.getId(), FinalidadeDoCodigo.REDEFINICAO_SENHA, codigo);
        cuidadorRepository.redefinirSenha(cuidador.getId(), passwordHasher.hash(novaSenha));
    }
}
