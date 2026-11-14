package com.medcare.senior.application.service;

import com.medcare.senior.domain.email.CodigoPorEmail;
import com.medcare.senior.domain.email.CodigoPorEmailRepositoryPort;
import com.medcare.senior.domain.email.EnviadorDeEmailPort;
import com.medcare.senior.domain.email.FinalidadeDoCodigo;
import com.medcare.senior.domain.exception.CodigoPorEmailInvalidoException;
import com.medcare.senior.domain.exception.EsperaParaReenviarException;
import com.medcare.senior.domain.usuario.Cuidador;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.random.RandomGenerator;

/**
 * Códigos de 6 dígitos enviados ao e-mail do cuidador (confirmar o e-mail e redefinir a senha).
 *
 * <ul>
 *   <li>Valem 15 minutos e um só uso; pedir outro substitui o anterior.</li>
 *   <li>Outro código só depois de 1 minuto do anterior (o e-mail de ninguém vira alvo de envio em massa).</li>
 *   <li>5 códigos errados invalidam o código: é preciso pedir outro. Com a espera de 1 minuto, um ataque testa no
 *       máximo 5 de 1 milhão de códigos por minuto.</li>
 *   <li>Só o hash SHA-256 é gravado, e a comparação leva o mesmo tempo para qualquer código.</li>
 * </ul>
 * Sem transação: a tentativa errada fica gravada mesmo com a exceção que vem em seguida.
 */
@Service
public class CodigosPorEmailService {

    public static final Duration VALIDADE = Duration.ofMinutes(15);
    public static final Duration ESPERA_PARA_REENVIAR = Duration.ofMinutes(1);
    public static final int MAX_TENTATIVAS = 5;

    private final CodigoPorEmailRepositoryPort codigos;
    private final EnviadorDeEmailPort enviador;
    private final Clock clock;
    private final RandomGenerator aleatorio;

    @Autowired
    public CodigosPorEmailService(CodigoPorEmailRepositoryPort codigos, EnviadorDeEmailPort enviador, Clock clock) {
        this(codigos, enviador, clock, new SecureRandom());
    }

    // Permite fixar o código nos testes
    public CodigosPorEmailService(CodigoPorEmailRepositoryPort codigos, EnviadorDeEmailPort enviador, Clock clock,
                                  RandomGenerator aleatorio) {
        this.codigos = codigos;
        this.enviador = enviador;
        this.clock = clock;
        this.aleatorio = aleatorio;
    }

    /**
     * Gera, grava e envia um código novo.
     *
     * @return {@code true} se o e-mail foi entregue ao servidor de envio
     * @throws EsperaParaReenviarException se o anterior foi enviado há menos de 1 minuto
     */
    public boolean enviar(Cuidador cuidador, FinalidadeDoCodigo finalidade) {
        LocalDateTime agora = LocalDateTime.now(clock);
        codigos.buscar(cuidador.getId(), finalidade).ifPresent(anterior -> {
            long faltam = Duration.between(agora, anterior.criadoEm().plus(ESPERA_PARA_REENVIAR)).toSeconds();
            if (faltam > 0) {
                throw new EsperaParaReenviarException(faltam);
            }
        });
        String codigo = "%06d".formatted(aleatorio.nextInt(1_000_000));
        codigos.salvar(new CodigoPorEmail(cuidador.getId(), finalidade, hash(codigo), agora, agora.plus(VALIDADE), 0));
        return enviador.enviarCodigo(cuidador.getEmail(), cuidador.getNome(), finalidade, codigo);
    }

    /**
     * Confere o código informado e, se estiver certo, o consome (não vale de novo).
     *
     * @throws CodigoPorEmailInvalidoException errado (com as tentativas que restam), vencido, inexistente ou bloqueado
     */
    public void conferir(Long cuidadorId, FinalidadeDoCodigo finalidade, String informado) {
        CodigoPorEmail codigo = codigos.buscar(cuidadorId, finalidade)
                .orElseThrow(CodigoPorEmailInvalidoException::pecaOutro);
        if (codigo.expirado(LocalDateTime.now(clock)) || codigo.tentativas() >= MAX_TENTATIVAS) {
            throw CodigoPorEmailInvalidoException.pecaOutro();
        }
        String limpo = informado == null ? "" : informado.trim();
        if (!MessageDigest.isEqual(hash(limpo).getBytes(StandardCharsets.US_ASCII),
                codigo.codigoHash().getBytes(StandardCharsets.US_ASCII))) {
            codigos.registrarTentativa(cuidadorId, finalidade);
            int restantes = MAX_TENTATIVAS - codigo.tentativas() - 1;
            throw restantes > 0
                    ? CodigoPorEmailInvalidoException.incorreto(restantes)
                    : CodigoPorEmailInvalidoException.pecaOutro();
        }
        codigos.apagar(cuidadorId, finalidade);
    }

    static String hash(String codigo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(codigo.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
