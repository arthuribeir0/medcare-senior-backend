package com.medcare.senior.application.service;

import com.medcare.senior.domain.email.CodigoPorEmail;
import com.medcare.senior.domain.email.CodigoPorEmailRepositoryPort;
import com.medcare.senior.domain.email.EnviadorDeEmailPort;
import com.medcare.senior.domain.email.FinalidadeDoCodigo;
import com.medcare.senior.domain.exception.CodigoPorEmailInvalidoException;
import com.medcare.senior.domain.exception.EsperaParaReenviarException;
import com.medcare.senior.domain.usuario.Cuidador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

import static com.medcare.senior.domain.email.FinalidadeDoCodigo.REDEFINICAO_SENHA;
import static com.medcare.senior.domain.email.FinalidadeDoCodigo.VERIFICACAO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Códigos do e-mail: geração, espera para reenviar, validade, tentativas e o que fica gravado. */
class CodigosPorEmailServiceTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 14, 10, 0);
    private static final Cuidador ANA = Cuidador.builder().id(7L).nome("Ana Souza").email("ana@email.com").build();

    private final Map<String, CodigoPorEmail> gravados = new HashMap<>();
    private final List<String> enviados = new ArrayList<>();
    private RelogioMovel relogio;
    private CodigosPorEmailService servico;

    @BeforeEach
    void setUp() {
        relogio = new RelogioMovel(AGORA.atZone(FUSO).toInstant());
        CodigoPorEmailRepositoryPort repositorio = new CodigoPorEmailRepositoryPort() {
            @Override
            public Optional<CodigoPorEmail> buscar(Long cuidadorId, FinalidadeDoCodigo finalidade) {
                return Optional.ofNullable(gravados.get(cuidadorId + finalidade.name()));
            }

            @Override
            public void salvar(CodigoPorEmail codigo) {
                gravados.put(codigo.cuidadorId() + codigo.finalidade().name(), codigo);
            }

            @Override
            public void registrarTentativa(Long cuidadorId, FinalidadeDoCodigo finalidade) {
                CodigoPorEmail c = gravados.get(cuidadorId + finalidade.name());
                gravados.put(cuidadorId + finalidade.name(), new CodigoPorEmail(c.cuidadorId(), c.finalidade(),
                        c.codigoHash(), c.criadoEm(), c.expiraEm(), c.tentativas() + 1));
            }

            @Override
            public void apagar(Long cuidadorId, FinalidadeDoCodigo finalidade) {
                gravados.remove(cuidadorId + finalidade.name());
            }
        };
        EnviadorDeEmailPort enviador = (destinatario, nome, finalidade, codigo) -> enviados.add(destinatario + "|" + finalidade + "|" + codigo);
        servico = new CodigosPorEmailService(repositorio, enviador, relogio, sequencia(482915, 105, 777777));
    }

    @Test
    void enviaUmCodigoDeSeisDigitosEGuardaSoOHash() {
        assertThat(servico.enviar(ANA, VERIFICACAO)).isTrue();

        assertThat(enviados).containsExactly("ana@email.com|VERIFICACAO|482915");
        CodigoPorEmail gravado = gravados.get("7VERIFICACAO");
        assertThat(gravado.codigoHash()).hasSize(64).doesNotContain("482915")
                .isEqualTo(CodigosPorEmailService.hash("482915"));
        assertThat(gravado.expiraEm()).isEqualTo(AGORA.plusMinutes(15));
        assertThat(gravado.tentativas()).isZero();
    }

    @Test
    void codigoComZerosAEsquerdaTemSempreSeisDigitos() {
        servico.enviar(ANA, VERIFICACAO);
        relogio.avancarSegundos(60);
        servico.enviar(ANA, VERIFICACAO);
        assertThat(enviados.get(1)).endsWith("|000105");
    }

    @Test
    void outroCodigoSoDepoisDeUmMinutoESubstituiOAnterior() {
        servico.enviar(ANA, VERIFICACAO);
        relogio.avancarSegundos(20);

        assertThatThrownBy(() -> servico.enviar(ANA, VERIFICACAO))
                .isInstanceOf(EsperaParaReenviarException.class)
                .satisfies(e -> assertThat(((EsperaParaReenviarException) e).getSegundos()).isEqualTo(40));
        // A outra finalidade tem espera própria
        assertThatCode(() -> servico.enviar(ANA, REDEFINICAO_SENHA)).doesNotThrowAnyException();

        relogio.avancarSegundos(40);
        servico.enviar(ANA, VERIFICACAO);
        assertThatThrownBy(() -> servico.conferir(7L, VERIFICACAO, "482915"))
                .isInstanceOf(CodigoPorEmailInvalidoException.class);
    }

    @Test
    void codigoCertoValeUmaVezSo() {
        servico.enviar(ANA, VERIFICACAO);

        assertThatCode(() -> servico.conferir(7L, VERIFICACAO, " 482915 ")).doesNotThrowAnyException();
        assertThatThrownBy(() -> servico.conferir(7L, VERIFICACAO, "482915"))
                .isInstanceOf(CodigoPorEmailInvalidoException.class)
                .hasMessage("Este código não vale mais. Peça um novo código.");
    }

    @Test
    void codigoDeUmaFinalidadeNaoValeParaAOutra() {
        servico.enviar(ANA, VERIFICACAO);
        assertThatThrownBy(() -> servico.conferir(7L, REDEFINICAO_SENHA, "482915"))
                .isInstanceOf(CodigoPorEmailInvalidoException.class);
    }

    @Test
    void codigoVencidoNaoVale() {
        servico.enviar(ANA, VERIFICACAO);
        relogio.avancarSegundos(15 * 60);
        assertThatThrownBy(() -> servico.conferir(7L, VERIFICACAO, "482915"))
                .isInstanceOf(CodigoPorEmailInvalidoException.class)
                .hasMessageContaining("Peça um novo código");
    }

    @Test
    void cincoErrosBloqueiamOCodigoAteOCertoDeixaDeValer() {
        servico.enviar(ANA, VERIFICACAO);

        assertThatThrownBy(() -> servico.conferir(7L, VERIFICACAO, "000000"))
                .hasMessage("Código incorreto. Você tem mais 4 tentativas.");
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> servico.conferir(7L, VERIFICACAO, "000000"))
                    .isInstanceOf(CodigoPorEmailInvalidoException.class);
        }
        assertThat(gravados.get("7VERIFICACAO").tentativas()).isEqualTo(4);
        assertThatThrownBy(() -> servico.conferir(7L, VERIFICACAO, "000000"))
                .hasMessage("Este código não vale mais. Peça um novo código.");

        assertThatThrownBy(() -> servico.conferir(7L, VERIFICACAO, "482915"))
                .hasMessage("Este código não vale mais. Peça um novo código.");
    }

    @Test
    void penultimaTentativaAvisaQueSobraUma() {
        servico.enviar(ANA, VERIFICACAO);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> servico.conferir(7L, VERIFICACAO, "1")).isInstanceOf(CodigoPorEmailInvalidoException.class);
        }
        assertThatThrownBy(() -> servico.conferir(7L, VERIFICACAO, "1"))
                .hasMessage("Código incorreto. Você tem mais 1 tentativa.");
    }

    @Test
    void semCodigoGravadoPedeOutro() {
        assertThatThrownBy(() -> servico.conferir(7L, VERIFICACAO, "482915"))
                .isInstanceOf(CodigoPorEmailInvalidoException.class);
        assertThatThrownBy(() -> servico.conferir(7L, VERIFICACAO, null))
                .isInstanceOf(CodigoPorEmailInvalidoException.class);
    }

    private static RandomGenerator sequencia(int... valores) {
        int[] proximo = {0};
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int limite) {
                return valores[proximo[0]++ % valores.length];
            }
        };
    }

    private static final class RelogioMovel extends Clock {
        private Instant agora;

        RelogioMovel(Instant agora) {
            this.agora = agora;
        }

        void avancarSegundos(long segundos) {
            agora = agora.plusSeconds(segundos);
        }

        @Override
        public ZoneId getZone() {
            return FUSO;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
