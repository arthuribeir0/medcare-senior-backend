package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.autenticacao.PasswordHasherPort;
import com.medcare.senior.domain.exception.EmailJaCadastradoException;
import com.medcare.senior.domain.exception.SenhaFracaException;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CadastrarCuidadorUseCase {

    // RN-002: mínimo 8 caracteres, ao menos uma maiúscula, uma minúscula e um número
    private static final Pattern SENHA_FORTE = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

    private final CuidadorRepositoryPort cuidadorRepository;
    private final PasswordHasherPort passwordHasher;

    public Cuidador executar(String nome, String email, String senha, String telefone) {
        String emailNormalizado = email.trim().toLowerCase(Locale.ROOT);

        // RN-001
        if (cuidadorRepository.existsByEmail(emailNormalizado)) {
            throw new EmailJaCadastradoException(emailNormalizado);
        }

        // RN-002
        if (!SENHA_FORTE.matcher(senha).matches()) {
            throw new SenhaFracaException();
        }

        Cuidador cuidador = Cuidador.builder()
                .nome(nome.trim())
                .email(emailNormalizado)
                .senhaHash(passwordHasher.hash(senha))
                .telefone(telefone)
                .dataCadastro(LocalDateTime.now())
                .build();

        // Cadastro concorrente com o mesmo e-mail é convertido em EmailJaCadastradoException pelo adapter
        return cuidadorRepository.save(cuidador);
    }
}
