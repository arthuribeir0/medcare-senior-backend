package com.medcare.senior.infrastructure.persistence;

import com.medcare.senior.application.usecase.ExcluirContaUseCase;
import com.medcare.senior.domain.historico.Pagina;
import com.medcare.senior.domain.mensagem.Mensagem;
import com.medcare.senior.domain.mensagem.Remetente;
import com.medcare.senior.domain.mensagem.ResumoConversa;
import com.medcare.senior.infrastructure.persistence.adapter.CodigoVinculoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.CuidadorRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.DoseRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.EventoNotificacaoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.PacienteRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.MedicamentoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.MensagemRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.TratamentoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.mapper.CodigoVinculoMapper;
import com.medcare.senior.infrastructure.persistence.mapper.CuidadorMapper;
import com.medcare.senior.infrastructure.persistence.mapper.DoseMapper;
import com.medcare.senior.infrastructure.persistence.mapper.PacienteMapper;
import com.medcare.senior.infrastructure.persistence.mapper.MedicamentoMapper;
import com.medcare.senior.infrastructure.persistence.mapper.TratamentoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mensagens (V14) contra o PostgreSQL real: CHECKs da tabela, cascata ao apagar o paciente, exclusão de conta, contadores
 * de não lidas, marcar como lida (idempotente, só o outro lado), conversa paginada e lista de conversas. Cada teste é
 * desfeito com rollback.
 */
@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        MensagemRepositoryAdapter.class,
        CuidadorRepositoryAdapter.class, PacienteRepositoryAdapter.class, MedicamentoRepositoryAdapter.class,
        TratamentoRepositoryAdapter.class, DoseRepositoryAdapter.class, CodigoVinculoRepositoryAdapter.class,
        EventoNotificacaoRepositoryAdapter.class,
        CuidadorMapper.class, PacienteMapper.class, MedicamentoMapper.class, TratamentoMapper.class, DoseMapper.class,
        CodigoVinculoMapper.class,
        ExcluirContaUseCase.class
})
class MensagemPostgresTest {

    // Horários em 2000: nenhum dado de desenvolvimento do banco local interfere
    private static final LocalDateTime T0 = LocalDateTime.of(2000, 1, 10, 8, 0);

    @Autowired
    private MensagemRepositoryAdapter mensagens;

    @Autowired
    private ExcluirContaUseCase excluirContaUseCase;

    @Autowired
    private JdbcTemplate jdbc;

    private Long cuidador;
    private Long ana;
    private Long bruno;

    @BeforeEach
    void criarDados() {
        cuidador = cuidador();
        ana = paciente(cuidador, "ana");
        bruno = paciente(cuidador, "Bruno");
    }

    private Long cuidador() {
        return jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                + "data_consentimento) VALUES ('Cuidador mensagens', ?, 'hash', now(), true, now()) RETURNING id",
                Long.class, "mensagens-" + UUID.randomUUID() + "@teste.com");
    }

    private Long paciente(Long cuidadorId, String nome) {
        return jdbc.queryForObject("INSERT INTO paciente (nome, data_nascimento, cuidador_id) VALUES (?, ?, ?) RETURNING id",
                Long.class, nome, LocalDate.of(1945, 3, 12), cuidadorId);
    }

    private Mensagem enviar(Long pacienteId, Remetente remetente, String texto, LocalDateTime quando) {
        return mensagens.salvar(Mensagem.nova(pacienteId, remetente, texto, quando));
    }

    private long contar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    void salvarDevolveOIdEOQueFoiGravado() {
        Mensagem salva = enviar(ana, Remetente.CUIDADOR, "Bom dia", T0);

        assertThat(salva.id()).isNotNull();
        assertThat(salva.criadaEm()).isEqualTo(T0);
        assertThat(salva.lidaEm()).isNull();
        assertThat(jdbc.queryForObject("SELECT conteudo FROM mensagem WHERE id = ?", String.class, salva.id()))
                .isEqualTo("Bom dia");
        assertThat(jdbc.queryForObject("SELECT remetente FROM mensagem WHERE id = ?", String.class, salva.id()))
                .isEqualTo("CUIDADOR");
    }

    @Test
    void checkAceita500CaracteresMesmoComEmojis() {
        jdbc.update("INSERT INTO mensagem (paciente_id, remetente, conteudo, criada_em) VALUES (?, 'PACIENTE', ?, ?)",
                ana, "😀".repeat(500), T0);

        assertThat(contar("SELECT COUNT(*) FROM mensagem WHERE paciente_id = ?", ana)).isEqualTo(1);
    }

    @Test
    void checkRecusaConteudoComMaisDe500Caracteres() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO mensagem (paciente_id, remetente, conteudo, criada_em) "
                + "VALUES (?, 'PACIENTE', ?, ?)", ana, "a".repeat(501), T0))
                .hasMessageContaining("ck_mensagem_conteudo");
    }

    @Test
    void checkRecusaConteudoVazio() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO mensagem (paciente_id, remetente, conteudo, criada_em) "
                + "VALUES (?, 'PACIENTE', '', ?)", ana, T0))
                .hasMessageContaining("ck_mensagem_conteudo");
    }

    @Test
    void checkRecusaRemetenteDesconhecido() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO mensagem (paciente_id, remetente, conteudo, criada_em) "
                + "VALUES (?, 'MEDICO', 'oi', ?)", ana, T0))
                .hasMessageContaining("ck_mensagem_remetente");
    }

    @Test
    void apagarOPacienteApagaAsMensagensDeleEmCascata() {
        enviar(ana, Remetente.CUIDADOR, "Oi, Ana", T0);
        enviar(bruno, Remetente.CUIDADOR, "Oi, Bruno", T0);

        jdbc.update("DELETE FROM paciente WHERE id = ?", ana);

        assertThat(contar("SELECT COUNT(*) FROM mensagem WHERE paciente_id = ?", ana)).isZero();
        assertThat(contar("SELECT COUNT(*) FROM mensagem WHERE paciente_id = ?", bruno)).isEqualTo(1);
    }

    @Test
    void exclusaoDeContaApagaAsMensagensDosPacientesDoCuidadorSemQuebrarPorFk() {
        enviar(ana, Remetente.CUIDADOR, "Oi", T0);
        enviar(ana, Remetente.PACIENTE, "Oi!", T0.plusMinutes(1));
        enviar(bruno, Remetente.PACIENTE, "Bom dia", T0);
        Long pacienteDeOutro = paciente(cuidador(), "Carla");
        enviar(pacienteDeOutro, Remetente.CUIDADOR, "Fica", T0);

        excluirContaUseCase.executar(cuidador);

        assertThat(contar("SELECT COUNT(*) FROM mensagem WHERE paciente_id IN (?, ?)", ana, bruno)).isZero();
        assertThat(contar("SELECT COUNT(*) FROM paciente WHERE id IN (?, ?)", ana, bruno)).isZero();
        assertThat(contar("SELECT COUNT(*) FROM mensagem WHERE paciente_id = ?", pacienteDeOutro)).isEqualTo(1);
    }

    @Test
    void conversaPaginadaDaMaisRecenteParaAMaisAntiga() {
        Mensagem m1 = enviar(ana, Remetente.CUIDADOR, "1", T0);
        Mensagem m2 = enviar(ana, Remetente.PACIENTE, "2", T0.plusMinutes(1));
        Mensagem m3 = enviar(ana, Remetente.CUIDADOR, "3", T0.plusMinutes(2));
        // Mesmo horário: o id maior (mais novo) vem antes
        Mensagem m4 = enviar(ana, Remetente.PACIENTE, "4", T0.plusMinutes(2));
        enviar(bruno, Remetente.CUIDADOR, "outra conversa", T0.plusMinutes(5));

        Pagina<Mensagem> primeira = mensagens.findConversa(ana, 0, 3);
        Pagina<Mensagem> segunda = mensagens.findConversa(ana, 1, 3);

        assertThat(primeira.conteudo()).extracting(Mensagem::id).containsExactly(m4.id(), m3.id(), m2.id());
        assertThat(segunda.conteudo()).extracting(Mensagem::id).containsExactly(m1.id());
        assertThat(primeira.totalElementos()).isEqualTo(4);
        assertThat(primeira.totalPaginas()).isEqualTo(2);
        assertThat(mensagens.findConversa(ana, 5, 3).conteudo()).isEmpty();
        assertThat(mensagens.findConversa(paciente(cuidador, "Sem mensagens"), 0, 20).totalElementos()).isZero();
    }

    @Test
    void marcarComoLidasSoMudaAsDoRemetenteAindaNaoLidasEEhIdempotente() {
        LocalDateTime leitura = T0.plusHours(1);
        Mensagem doPaciente1 = enviar(ana, Remetente.PACIENTE, "a", T0);
        Mensagem doPaciente2 = enviar(ana, Remetente.PACIENTE, "b", T0.plusMinutes(1));
        Mensagem doCuidador = enviar(ana, Remetente.CUIDADOR, "c", T0.plusMinutes(2));
        Mensagem deOutroPaciente = enviar(bruno, Remetente.PACIENTE, "d", T0);

        // O cuidador abre a conversa da Ana: lê as que a Ana escreveu
        assertThat(mensagens.marcarComoLidas(ana, Remetente.PACIENTE, leitura)).isEqualTo(2);
        assertThat(mensagens.marcarComoLidas(ana, Remetente.PACIENTE, leitura.plusHours(1))).isZero();

        assertThat(lidaEm(doPaciente1.id())).isEqualTo(leitura);
        assertThat(lidaEm(doPaciente2.id())).isEqualTo(leitura);
        assertThat(lidaEm(doCuidador.id())).isNull();
        assertThat(lidaEm(deOutroPaciente.id())).isNull();
    }

    private LocalDateTime lidaEm(Long mensagemId) {
        return jdbc.queryForObject("SELECT lida_em FROM mensagem WHERE id = ?", LocalDateTime.class, mensagemId);
    }

    @Test
    void contadoresDeNaoLidasPorConversaEDoCuidador() {
        enviar(ana, Remetente.PACIENTE, "a", T0);
        enviar(ana, Remetente.PACIENTE, "b", T0);
        enviar(ana, Remetente.CUIDADOR, "c", T0);
        enviar(bruno, Remetente.PACIENTE, "d", T0);
        Long pacienteDeOutro = paciente(cuidador(), "Carla");
        enviar(pacienteDeOutro, Remetente.PACIENTE, "não conta", T0);

        assertThat(mensagens.contarNaoLidas(ana, Remetente.PACIENTE)).isEqualTo(2);
        assertThat(mensagens.contarNaoLidas(ana, Remetente.CUIDADOR)).isEqualTo(1);
        assertThat(mensagens.contarNaoLidasDoCuidador(cuidador)).isEqualTo(3);

        mensagens.marcarComoLidas(ana, Remetente.PACIENTE, T0.plusHours(1));

        assertThat(mensagens.contarNaoLidas(ana, Remetente.PACIENTE)).isZero();
        assertThat(mensagens.contarNaoLidasDoCuidador(cuidador)).isEqualTo(1);
    }

    @Test
    void conversasDoCuidadorTrazemUltimaMensagemNaoLidasESemMensagensPorUltimoPorNome() {
        Long zelia = paciente(cuidador, "Zélia");
        enviar(ana, Remetente.CUIDADOR, "antiga", T0);
        enviar(ana, Remetente.PACIENTE, "resposta da Ana", T0.plusMinutes(10));
        enviar(bruno, Remetente.PACIENTE, "do Bruno", T0.plusMinutes(5));
        paciente(cuidador(), "De outro cuidador");

        // Ana (mensagem mais recente), Bruno, e os sem mensagens em ordem de nome sem diferenciar maiúsculas
        Long anaMaiuscula = paciente(cuidador, "Amanda");
        assertThat(mensagens.findConversasDoCuidador(cuidador)).extracting(ResumoConversa::pacienteId)
                .containsExactly(ana, bruno, anaMaiuscula, zelia);

        ResumoConversa daAna = mensagens.findConversasDoCuidador(cuidador).get(0);
        assertThat(daAna.pacienteNome()).isEqualTo("ana");
        assertThat(daAna.ultimaMensagem()).isEqualTo(new ResumoConversa.UltimaMensagem("resposta da Ana", Remetente.PACIENTE,
                T0.plusMinutes(10)));
        assertThat(daAna.naoLidas()).isEqualTo(1);
        ResumoConversa daZelia = mensagens.findConversasDoCuidador(cuidador).get(3);
        assertThat(daZelia.ultimaMensagem()).isNull();
        assertThat(daZelia.naoLidas()).isZero();
        assertThat(mensagens.findConversasDoCuidador(cuidador())).isEmpty();
    }

    @Test
    void conversaDoPacienteContaAsNaoLidasDoCuidador() {
        enviar(ana, Remetente.CUIDADOR, "a", T0);
        enviar(ana, Remetente.CUIDADOR, "b", T0.plusMinutes(1));
        enviar(ana, Remetente.PACIENTE, "c", T0.plusMinutes(2));

        ResumoConversa conversa = mensagens.findConversaDoPaciente(ana);

        assertThat(conversa.pacienteId()).isEqualTo(ana);
        assertThat(conversa.naoLidas()).isEqualTo(2);
        assertThat(conversa.ultimaMensagem().conteudo()).isEqualTo("c");
        assertThat(mensagens.findConversaDoPaciente(bruno).ultimaMensagem()).isNull();
    }
}
