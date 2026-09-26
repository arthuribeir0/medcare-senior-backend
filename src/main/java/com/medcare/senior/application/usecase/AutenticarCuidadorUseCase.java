package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.autenticacao.PasswordHasherPort;
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
     * @return o JWT do cuidador autenticado
     */
    public String executar(String email, String senha) {
        String emailNormalizado = email.trim().toLowerCase(Locale.ROOT);
        Optional<Cuidador> cuidador = cuidadorRepository.findByEmail(emailNormalizado);

        String hash = cuidador.map(Cuidador::getSenhaHash).orElse(hashFicticio);
        boolean senhaConfere = passwordHasher.matches(senha, hash);

        if (cuidador.isEmpty() || !senhaConfere) {
            throw new CredenciaisInvalidasException();
        }

        return tokenGenerator.generateToken(cuidador.get().getId(), Role.CUIDADOR);
    }
}
