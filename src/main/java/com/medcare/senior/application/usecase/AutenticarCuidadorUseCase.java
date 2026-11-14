/*
 * Caso de uso do login do cuidador. Normaliza o e-mail, confere a senha com o hash BCrypt e devolve o JWT (papel
 * CUIDADOR), o ID, o nome e se o e-mail já foi confirmado. Para e-mail inexistente compara com um hash fictício,
 * para o tempo de resposta ser igual ao de uma senha errada e não revelar quais e-mails existem. O erro é sempre o
 * mesmo (credenciais inválidas).
 */
package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.autenticacao.PasswordHasherPort;
import com.medcare.senior.domain.autenticacao.SessaoAutenticada;
import com.medcare.senior.domain.autenticacao.TokenGeneratorPort;
import com.medcare.senior.domain.exception.CredenciaisInvalidasException;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import com.medcare.senior.domain.usuario.Role;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class AutenticarCuidadorUseCase {

    private final CuidadorRepositoryPort cuidadorRepository;
    private final PasswordHasherPort passwordHasher;
    private final TokenGeneratorPort tokenGenerator;

    // Hash usado quando o e-mail não existe, para que o tempo de resposta seja o mesmo
    // de uma senha errada e não seja possível descobrir quais e-mails estão cadastrados.
    private final String hashFicticio;

    public AutenticarCuidadorUseCase(CuidadorRepositoryPort cuidadorRepository,
                                     PasswordHasherPort passwordHasher,
                                     TokenGeneratorPort tokenGenerator) {
        this.cuidadorRepository = cuidadorRepository;
        this.passwordHasher = passwordHasher;
        this.tokenGenerator = tokenGenerator;
        this.hashFicticio = passwordHasher.hash("senha-ficticia-para-tempo-constante");
    }

    /**
     * @return o JWT, o ID e o nome do cuidador autenticado
     */
    public SessaoAutenticada executar(String email, String senha) {
        String emailNormalizado = email.trim().toLowerCase(Locale.ROOT);
        Optional<Cuidador> cuidador = cuidadorRepository.findByEmail(emailNormalizado);

        String hash = cuidador.map(Cuidador::getSenhaHash).orElse(hashFicticio);
        boolean senhaConfere = passwordHasher.matches(senha, hash);

        if (cuidador.isEmpty() || !senhaConfere) {
            throw new CredenciaisInvalidasException();
        }

        Cuidador autenticado = cuidador.get();
        return new SessaoAutenticada(tokenGenerator.generateToken(autenticado.getId(), Role.CUIDADOR),
                autenticado.getId(), autenticado.getNome(), autenticado.isEmailVerificado());
    }
}
