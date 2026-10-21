package com.medcare.senior.domain.mensagem;

import com.medcare.senior.domain.exception.ParametroInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MensagemTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 20, 8, 0, 0, 123_456_789);

    @Test
    void mensagemNovaTiraEspacosDasPontasENasceNaoLidaSemId() {
        Mensagem mensagem = Mensagem.nova(10L, Remetente.CUIDADOR, "  Bom dia!\n ", AGORA);

        assertThat(mensagem.id()).isNull();
        assertThat(mensagem.idosoId()).isEqualTo(10L);
        assertThat(mensagem.remetente()).isEqualTo(Remetente.CUIDADOR);
        assertThat(mensagem.conteudo()).isEqualTo("Bom dia!");
        assertThat(mensagem.lidaEm()).isNull();
    }

    @Test
    void horaDoEnvioEhTruncadaEmMicrossegundosComoNoPostgres() {
        assertThat(Mensagem.nova(10L, Remetente.IDOSO, "Oi", AGORA).criadaEm())
                .isEqualTo(LocalDateTime.of(2026, 10, 20, 8, 0, 0, 123_456_000));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\n\t "})
    void vazioOuSoEspacosEhRecusadoNoCampoConteudo(String conteudo) {
        assertThatThrownBy(() -> Mensagem.nova(10L, Remetente.CUIDADOR, conteudo, AGORA))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessage("A mensagem não pode ser vazia")
                .extracting("campo").isEqualTo("conteudo");
    }

    @Test
    void aceitaAte500CaracteresERecusa501() {
        assertThat(Mensagem.nova(10L, Remetente.CUIDADOR, "a".repeat(500), AGORA).conteudo()).hasSize(500);
        // Espaços nas pontas não contam
        assertThat(Mensagem.nova(10L, Remetente.CUIDADOR, " " + "a".repeat(500) + " ", AGORA).conteudo()).hasSize(500);

        assertThatThrownBy(() -> Mensagem.nova(10L, Remetente.CUIDADOR, "a".repeat(501), AGORA))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessage("A mensagem deve ter no máximo 500 caracteres")
                .extracting("campo").isEqualTo("conteudo");
    }

    @Test
    void tamanhoEhContadoEmCaracteresComoOCheckDoBanco() {
        // Cada emoji ocupa 2 unidades UTF-16, mas é 1 caractere (char_length do PostgreSQL)
        String quinhentosEmojis = "😀".repeat(500);

        assertThat(Mensagem.nova(10L, Remetente.IDOSO, quinhentosEmojis, AGORA).conteudo()).isEqualTo(quinhentosEmojis);
        assertThatThrownBy(() -> Mensagem.nova(10L, Remetente.IDOSO, quinhentosEmojis + "a", AGORA))
                .isInstanceOf(ParametroInvalidoException.class);
    }

    @Test
    void outroLadoDaConversa() {
        assertThat(Remetente.CUIDADOR.outroLado()).isEqualTo(Remetente.IDOSO);
        assertThat(Remetente.IDOSO.outroLado()).isEqualTo(Remetente.CUIDADOR);
    }
}
