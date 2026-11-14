package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.CodigosPorEmailService;
import com.medcare.senior.domain.autenticacao.PasswordHasherPort;
import com.medcare.senior.domain.email.FinalidadeDoCodigo;
import com.medcare.senior.domain.email.FormatoDeEmail;
import com.medcare.senior.domain.email.PoliticaDeSenha;
import com.medcare.senior.domain.email.VerificadorDeDominioPort;
import com.medcare.senior.domain.exception.ConsentimentoLgpdObrigatorioException;
import com.medcare.senior.domain.exception.EmailInvalidoException;
import com.medcare.senior.domain.exception.EmailJaCadastradoException;
import com.medcare.senior.domain.exception.SenhaFracaException;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class CadastrarCuidadorUseCase {

    private final CuidadorRepositoryPort cuidadorRepository;
    private final PasswordHasherPort passwordHasher;
    private final Clock clock;
    private final VerificadorDeDominioPort verificadorDeDominio;
    private final CodigosPorEmailService codigos;

    /** Sem conferência do domínio e sem envio do código (testes das regras do cadastro). */
    public CadastrarCuidadorUseCase(CuidadorRepositoryPort cuidadorRepository, PasswordHasherPort passwordHasher,
                                    Clock clock) {
        this(cuidadorRepository, passwordHasher, clock, dominio -> true, null);
    }

    @Autowired
    public CadastrarCuidadorUseCase(CuidadorRepositoryPort cuidadorRepository, PasswordHasherPort passwordHasher,
                                    Clock clock, VerificadorDeDominioPort verificadorDeDominio,
                                    CodigosPorEmailService codigos) {
        this.cuidadorRepository = cuidadorRepository;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
        this.verificadorDeDominio = verificadorDeDominio;
        this.codigos = codigos;
    }

    /**
     * Cria a conta com o e-mail ainda NÃO confirmado e envia o código de confirmação a ele. Sem confirmar, o cuidador
     * entra no app mas não cadastra pacientes.
     *
     * @param consentimentoLgpd aceite do termo de consentimento; só {@code true} é aceito ({@code null} é recusa)
     * @throws ConsentimentoLgpdObrigatorioException se o termo não foi aceito
     * @throws EmailInvalidoException                formato fora do aceito ou domínio que não existe
     */
    public Cuidador executar(String nome, String email, String senha, String telefone, Boolean consentimentoLgpd) {
        // LGPD: verificado primeiro, para nem consultar o e-mail de quem não consentiu
        if (!Boolean.TRUE.equals(consentimentoLgpd)) {
            throw new ConsentimentoLgpdObrigatorioException();
        }

        if (!FormatoDeEmail.valido(email)) {
            throw new EmailInvalidoException("O e-mail informado é inválido");
        }
        String emailNormalizado = FormatoDeEmail.normalizar(email);

        // RN-001
        if (cuidadorRepository.existsByEmail(emailNormalizado)) {
            throw new EmailJaCadastradoException(emailNormalizado);
        }

        // RN-002
        if (!PoliticaDeSenha.forte(senha)) {
            throw new SenhaFracaException();
        }

        // Domínio digitado errado ("gmial.com") não recebe o código: barrado aqui, com a explicação
        String dominio = FormatoDeEmail.dominio(emailNormalizado);
        if (!verificadorDeDominio.dominioRecebeEmail(dominio)) {
            throw new EmailInvalidoException("O domínio \"" + dominio + "\" não existe. Confira o e-mail.");
        }

        LocalDateTime agora = LocalDateTime.now(clock);
        Cuidador cuidador = Cuidador.builder()
                .nome(nome.trim())
                .email(emailNormalizado)
                .senhaHash(passwordHasher.hash(senha))
                .telefone(telefone)
                .dataCadastro(agora)
                .consentimentoLgpd(true)
                .dataConsentimento(agora)
                .emailVerificado(false)
                .build();

        // Cadastro concorrente com o mesmo e-mail é convertido em EmailJaCadastradoException pelo adapter
        Cuidador salvo = cuidadorRepository.save(cuidador);
        // Falha no envio não desfaz o cadastro: o app oferece "Reenviar código"
        if (codigos != null) {
            codigos.enviar(salvo, FinalidadeDoCodigo.VERIFICACAO);
        }
        return salvo;
    }
}
