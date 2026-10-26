package com.medcare.senior.infrastructure.persistence;

import com.medcare.senior.application.service.GeradorDeDosesService;
import com.medcare.senior.application.usecase.BuscarMedicamentoUseCase;
import com.medcare.senior.application.usecase.ConfirmarDoseUseCase;
import com.medcare.senior.application.usecase.EditarTratamentoUseCase;
import com.medcare.senior.application.usecase.ExcluirContaUseCase;
import com.medcare.senior.application.usecase.RegistrarTokenDispositivoUseCase;
import com.medcare.senior.application.usecase.RemoverTokenDispositivoUseCase;
import com.medcare.senior.application.usecase.RemoverMedicamentoUseCase;
import com.medcare.senior.domain.configuracao.Tolerancia;
import com.medcare.senior.domain.dose.ConfirmadoPor;
import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.DoseDetalhada;
import com.medcare.senior.domain.dose.DoseNaoConfirmavelException;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.dose.DoseEncerrada;
import com.medcare.senior.domain.dose.DoseParaAviso;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.notificacao.MotivoFalhaNotificacao;
import com.medcare.senior.domain.usuario.CodigoVinculo;
import com.medcare.senior.infrastructure.persistence.adapter.CodigoVinculoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.ConfiguracaoAlertaRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.CuidadorRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.DoseRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.EventoNotificacaoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.PacienteRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.MedicamentoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.MensagemRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.TratamentoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.entity.CodigoVinculoEntity;
import com.medcare.senior.infrastructure.persistence.entity.CuidadorEntity;
import com.medcare.senior.infrastructure.persistence.entity.DoseEntity;
import com.medcare.senior.infrastructure.persistence.entity.PacienteEntity;
import com.medcare.senior.infrastructure.persistence.entity.MedicamentoEntity;
import com.medcare.senior.infrastructure.persistence.entity.TratamentoEntity;
import com.medcare.senior.infrastructure.persistence.mapper.CodigoVinculoMapper;
import com.medcare.senior.infrastructure.persistence.mapper.CuidadorMapper;
import com.medcare.senior.infrastructure.persistence.mapper.DoseMapper;
import com.medcare.senior.infrastructure.persistence.mapper.PacienteMapper;
import com.medcare.senior.infrastructure.persistence.mapper.MedicamentoMapper;
import com.medcare.senior.infrastructure.persistence.mapper.TratamentoMapper;
import com.medcare.senior.infrastructure.cache.CacheDeRelatorioDiarioCaffeine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes de persistência contra o PostgreSQL real do projeto (não H2): as migrations do Flyway rodam, as consultas
 * JPQL e os deletes/updates em massa são executados de verdade, e cada teste é desfeito com rollback no final.
 * Os mocks dos testes de use case não detectam erros de JPQL, de contexto de persistência ou de chave estrangeira.
 */
@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CacheDeRelatorioDiarioCaffeine.class, 
        CuidadorRepositoryAdapter.class, PacienteRepositoryAdapter.class, MedicamentoRepositoryAdapter.class,
        TratamentoRepositoryAdapter.class, DoseRepositoryAdapter.class, CodigoVinculoRepositoryAdapter.class,
        EventoNotificacaoRepositoryAdapter.class, ConfiguracaoAlertaRepositoryAdapter.class,
        MensagemRepositoryAdapter.class,
        CuidadorMapper.class, PacienteMapper.class, MedicamentoMapper.class, TratamentoMapper.class, DoseMapper.class,
        CodigoVinculoMapper.class,
        ExcluirContaUseCase.class, RemoverMedicamentoUseCase.class, EditarTratamentoUseCase.class,
        RegistrarTokenDispositivoUseCase.class, BuscarMedicamentoUseCase.class, RemoverTokenDispositivoUseCase.class,
        ConfirmarDoseUseCase.class,
        GeradorDeDosesService.class,
        PersistenciaPostgresTest.RelogioFixo.class
})
class PersistenciaPostgresTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    // Quinta-feira, 14:30
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 8, 14, 30);
    private static final LocalDate HOJE = AGORA.toLocalDate();
    private static final List<String> TODOS_OS_DIAS = EnumSet.allOf(DayOfWeek.class).stream().map(Enum::name).toList();

    @TestConfiguration
    static class RelogioFixo {
        @Bean
        Clock clock() {
            return Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO);
        }

        @Bean
        PoliticaDeJanela politicaDeJanela() {
            return new PoliticaDeJanela(java.time.Duration.ofMinutes(60), java.time.Duration.ofMinutes(30),
                    java.time.Duration.ofMinutes(30));
        }
    }

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ExcluirContaUseCase excluirContaUseCase;

    @Autowired
    private RemoverMedicamentoUseCase removerMedicamentoUseCase;

    @Autowired
    private EditarTratamentoUseCase editarTratamentoUseCase;

    @Autowired
    private MedicamentoRepositoryAdapter medicamentoAdapter;

    @Autowired
    private TratamentoRepositoryAdapter tratamentoAdapter;

    @Autowired
    private DoseRepositoryAdapter doseAdapter;

    @Autowired
    private EventoNotificacaoRepositoryAdapter eventoAdapter;

    @Autowired
    private ConfiguracaoAlertaRepositoryAdapter configuracaoAdapter;

    @Autowired
    private CuidadorRepositoryAdapter cuidadorAdapter;

    @Autowired
    private RegistrarTokenDispositivoUseCase registrarTokenDispositivoUseCase;

    @Autowired
    private CodigoVinculoRepositoryAdapter codigoVinculoAdapter;

    @Autowired
    private PacienteRepositoryAdapter pacienteAdapter;

    @Autowired
    private RemoverTokenDispositivoUseCase removerTokenDispositivoUseCase;

    @Autowired
    private ConfirmarDoseUseCase confirmarDoseUseCase;

    @Autowired
    private BuscarMedicamentoUseCase buscarMedicamentoUseCase;

    // ---------------------------------------------------------------------------------------------------------
    // Exclusão de conta (LGPD)
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void exclusaoDeContaApagaTudoDoCuidadorEAnonimizaSomenteAsDosesRegistradas() {
        CuidadorEntity cuidador = cuidador();
        PacienteEntity paciente = paciente(cuidador);
        MedicamentoEntity ativo = medicamento(paciente, null);
        MedicamentoEntity removido = medicamento(paciente, AGORA.minusDays(3));
        TratamentoEntity tratamentoAtivo = tratamento(ativo, null);
        TratamentoEntity tratamentoRemovido = tratamento(removido, AGORA.minusDays(3));
        CodigoVinculoEntity codigo = codigoVinculo(cuidador, paciente);
        DoseEntity pendentePassada = dose(tratamentoAtivo, AGORA.minusDays(1), StatusDose.PENDENTE);
        DoseEntity pendenteFutura = dose(tratamentoAtivo, AGORA.plusDays(1), StatusDose.PENDENTE);
        DoseEntity noHorario = dose(tratamentoAtivo, AGORA.minusHours(6), StatusDose.CONFIRMADA_NO_HORARIO);
        DoseEntity naoTomada = dose(tratamentoRemovido, AGORA.minusDays(5), StatusDose.NAO_TOMADA);
        DoseEntity comAtraso = dose(tratamentoRemovido, AGORA.minusDays(4), StatusDose.CONFIRMADA_COM_ATRASO);

        // Dados de outro cuidador não podem ser tocados
        CuidadorEntity outro = cuidador();
        PacienteEntity pacienteDoOutro = paciente(outro);
        TratamentoEntity tratamentoDoOutro = tratamento(medicamento(pacienteDoOutro, null), null);
        DoseEntity pendenteDoOutro = dose(tratamentoDoOutro, AGORA.plusDays(1), StatusDose.PENDENTE);

        excluirContaUseCase.executar(cuidador.getId());

        assertThat(contar("cuidador", cuidador.getId())).isZero();
        assertThat(contar("paciente", paciente.getId())).isZero();
        assertThat(contar("medicamento", ativo.getId(), removido.getId())).isZero();
        assertThat(contar("tratamento", tratamentoAtivo.getId(), tratamentoRemovido.getId())).isZero();
        assertThat(contar("codigo_vinculo", codigo.getId())).isZero();
        assertThat(contar("dose", pendentePassada.getId(), pendenteFutura.getId())).isZero();

        // Registradas: mantidas, mas sem vínculo com ninguém (ON DELETE SET NULL)
        assertThat(contar("dose", noHorario.getId(), naoTomada.getId(), comAtraso.getId())).isEqualTo(3);
        assertThat(tratamentoIdDaDose(noHorario.getId())).isNull();
        assertThat(tratamentoIdDaDose(naoTomada.getId())).isNull();
        assertThat(tratamentoIdDaDose(comAtraso.getId())).isNull();

        assertThat(contar("cuidador", outro.getId())).isEqualTo(1);
        assertThat(contar("tratamento", tratamentoDoOutro.getId())).isEqualTo(1);
        assertThat(tratamentoIdDaDose(pendenteDoOutro.getId())).isEqualTo(tratamentoDoOutro.getId());
    }

    @Test
    void exclusaoDeContaApagaOTokenDeDispositivo() {
        String token = "fcm-" + UUID.randomUUID();
        CuidadorEntity cuidador = cuidador();
        cuidadorAdapter.atualizarFcmToken(cuidador.getId(), token);
        assertThat(cuidadoresComToken(token)).isEqualTo(1);

        excluirContaUseCase.executar(cuidador.getId());

        // O token ficava na linha do cuidador, que foi apagada: não sobra cópia em lugar nenhum
        assertThat(cuidadoresComToken(token)).isZero();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Token de dispositivo (FCM)
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void registrarTokenGravaNoCuidadorERetiraDeOutraConta() {
        String token = "fcm-" + UUID.randomUUID();
        CuidadorEntity contaAnterior = cuidador();
        CuidadorEntity contaNova = cuidador();
        CuidadorEntity semRelacao = cuidador();
        cuidadorAdapter.atualizarFcmToken(contaAnterior.getId(), token);
        cuidadorAdapter.atualizarFcmToken(semRelacao.getId(), "fcm-outro-aparelho");

        registrarTokenDispositivoUseCase.executar(contaNova.getId(), token);

        assertThat(cuidadorAdapter.findById(contaNova.getId())).get()
                .extracting(c -> c.getFcmToken()).isEqualTo(token);
        assertThat(cuidadorAdapter.findById(contaAnterior.getId())).get()
                .extracting(c -> c.getFcmToken()).isNull();
        assertThat(cuidadorAdapter.findById(semRelacao.getId())).get()
                .extracting(c -> c.getFcmToken()).isEqualTo("fcm-outro-aparelho");
    }

    @Test
    void removerFcmTokenSeIgualSoApagaSeOTokenAindaForOMesmo() {
        CuidadorEntity cuidador = cuidador();
        cuidadorAdapter.atualizarFcmToken(cuidador.getId(), "fcm-novo");

        // O push falhou com o token antigo, mas o cuidador já registrou outro: o novo fica
        cuidadorAdapter.removerFcmTokenSeIgual(cuidador.getId(), "fcm-antigo");
        assertThat(cuidadorAdapter.findById(cuidador.getId())).get()
                .extracting(c -> c.getFcmToken()).isEqualTo("fcm-novo");

        cuidadorAdapter.removerFcmTokenSeIgual(cuidador.getId(), "fcm-novo");
        assertThat(cuidadorAdapter.findById(cuidador.getId())).get()
                .extracting(c -> c.getFcmToken()).isNull();
    }

    @Test
    void removerTokenApagaSoODoCuidadorEEhIdempotente() {
        CuidadorEntity cuidador = cuidador();
        CuidadorEntity outro = cuidador();
        cuidadorAdapter.atualizarFcmToken(cuidador.getId(), "fcm-meu-aparelho");
        cuidadorAdapter.atualizarFcmToken(outro.getId(), "fcm-outro-aparelho");

        removerTokenDispositivoUseCase.executar(cuidador.getId());
        assertThat(cuidadorAdapter.findById(cuidador.getId())).get()
                .extracting(c -> c.getFcmToken()).isNull();

        // De novo, já sem token: nenhum erro e nada muda
        removerTokenDispositivoUseCase.executar(cuidador.getId());
        assertThat(cuidadorAdapter.findById(cuidador.getId())).get()
                .extracting(c -> c.getFcmToken()).isNull();

        assertThat(cuidadorAdapter.findById(outro.getId())).get()
                .extracting(c -> c.getFcmToken()).isEqualTo("fcm-outro-aparelho");
    }

    @Test
    void atualizarFcmTokenDeCuidadorInexistenteNaoAlteraNada() {
        assertThat(cuidadorAdapter.atualizarFcmToken(Long.MAX_VALUE, "fcm-qualquer")).isFalse();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Leituras do app: pacientes do cuidador e medicamento por id
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void listarPacientesTrazSoOsDoCuidadorEmOrdemDeNomeSemDiferenciarMaiusculas() {
        CuidadorEntity cuidador = cuidador();
        PacienteEntity zeca = pacienteComNome(cuidador, "Zeca");
        PacienteEntity ana = pacienteComNome(cuidador, "ana");
        PacienteEntity bruno = pacienteComNome(cuidador, "Bruno");
        PacienteEntity doOutro = pacienteComNome(cuidador(), "Aaron");

        assertThat(pacienteAdapter.findAllByCuidadorId(cuidador.getId()))
                .extracting(i -> i.getId())
                .containsExactly(ana.getId(), bruno.getId(), zeca.getId())
                .doesNotContain(doOutro.getId());
    }

    @Test
    void buscarMedicamentoIgnoraRemovidoEDeOutroCuidador() {
        CuidadorEntity cuidador = cuidador();
        PacienteEntity paciente = paciente(cuidador);
        MedicamentoEntity ativo = medicamento(paciente, null);
        MedicamentoEntity removido = medicamento(paciente, AGORA.minusDays(1));
        MedicamentoEntity doOutro = medicamento(paciente(cuidador()), null);

        assertThat(buscarMedicamentoUseCase.executar(ativo.getId(), cuidador.getId()).getId()).isEqualTo(ativo.getId());
        assertThatThrownBy(() -> buscarMedicamentoUseCase.executar(removido.getId(), cuidador.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> buscarMedicamentoUseCase.executar(doOutro.getId(), cuidador.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Doses do dia para o app: uma consulta com paciente e medicamento
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void dosesDoDiaTrazSoODiaDoPacienteSemRemovidasNemAnonimizadasEmOrdemDeHorario() {
        // Dia em 2000: nenhum dado de desenvolvimento do banco local interfere
        LocalDate dia = LocalDate.of(2000, 3, 15);
        PacienteEntity paciente = paciente(cuidador());
        MedicamentoEntity medicamento = medicamento(paciente, null);
        TratamentoEntity tratamento = tratamento(medicamento, null);
        DoseEntity noite = dose(tratamento, dia.atTime(20, 0), StatusDose.PENDENTE);
        DoseEntity meiaNoite = dose(tratamento, dia.atStartOfDay(), StatusDose.CONFIRMADA_NO_HORARIO);
        DoseEntity manha = dose(tratamento, dia.atTime(8, 0), StatusDose.CONFIRMADA_COM_ATRASO);
        DoseEntity diaSeguinte = dose(tratamento, dia.plusDays(1).atStartOfDay(), StatusDose.PENDENTE);
        DoseEntity diaAnterior = dose(tratamento, dia.minusDays(1).atTime(23, 59), StatusDose.PENDENTE);
        DoseEntity deTratamentoRemovido = dose(tratamento(medicamento, dia.atStartOfDay()), dia.atTime(9, 0), StatusDose.PENDENTE);
        DoseEntity deMedicamentoRemovido = dose(tratamento(medicamento(paciente, dia.atStartOfDay()), null), dia.atTime(10, 0),
                StatusDose.PENDENTE);
        DoseEntity deOutroPaciente = dose(tratamento(medicamento(paciente(cuidador()), null), null), dia.atTime(11, 0),
                StatusDose.PENDENTE);
        em.getEntityManager()
                .createNativeQuery("INSERT INTO dose (tratamento_id, horario_programado, status, confirmado_por) VALUES (NULL, ?1, 'CONFIRMADA_NO_HORARIO', 'PACIENTE')")
                .setParameter(1, dia.atTime(12, 0))
                .executeUpdate();

        List<DoseDetalhada> doses = doseAdapter.findDetalhadasDoPacienteEntre(paciente.getId(), dia.atStartOfDay(),
                dia.plusDays(1).atStartOfDay());

        assertThat(doses).extracting(d -> d.dose().getId())
                .containsExactly(meiaNoite.getId(), manha.getId(), noite.getId())
                .doesNotContain(diaSeguinte.getId(), diaAnterior.getId(), deTratamentoRemovido.getId(),
                        deMedicamentoRemovido.getId(), deOutroPaciente.getId());
        DoseDetalhada primeira = doses.get(0);
        assertThat(primeira.pacienteId()).isEqualTo(paciente.getId());
        assertThat(primeira.pacienteNome()).isEqualTo(paciente.getNome());
        assertThat(primeira.medicamentoId()).isEqualTo(medicamento.getId());
        assertThat(primeira.medicamentoNome()).isEqualTo("Losartana");
        assertThat(primeira.dosagem()).isEqualTo("50mg");
        assertThat(primeira.tipo()).isEqualTo("COMPRIMIDO");
        assertThat(primeira.dose().getTratamentoId()).isEqualTo(tratamento.getId());
        assertThat(primeira.dose().getStatus()).isEqualTo(StatusDose.CONFIRMADA_NO_HORARIO);
        assertThat(primeira.dose().getHoraConfirmacao()).isEqualTo(dia.atStartOfDay().plusMinutes(5));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Confirmação de dose pelo paciente: UPDATE atômico e escopo
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void confirmarGravaConfirmadaNoHorarioComAHoraDoServidor() {
        PacienteEntity paciente = paciente(cuidador());
        DoseEntity pendente = dose(tratamento(medicamento(paciente, null), null), AGORA.minusMinutes(10), StatusDose.PENDENTE);

        confirmarDoseUseCase.executar(pendente.getId(), paciente.getId());

        assertThat(statusDaDose(pendente.getId())).isEqualTo("CONFIRMADA_NO_HORARIO");
        assertThat(horaConfirmacaoDaDose(pendente.getId())).isEqualTo(AGORA);
    }

    @Test
    void confirmarNaoMexeNoEstoqueDoMedicamento() {
        // o DRS só prevê o estoque como campo opcional do cadastro (UC-004, RN-005); não há baixa automática
        PacienteEntity paciente = paciente(cuidador());
        MedicamentoEntity medicamento = medicamento(paciente, null);
        em.getEntityManager().createNativeQuery("UPDATE medicamento SET quantidade_estoque = 30 WHERE id = ?1")
                .setParameter(1, medicamento.getId())
                .executeUpdate();
        DoseEntity pendente = dose(tratamento(medicamento, null), AGORA.minusMinutes(10), StatusDose.PENDENTE);

        confirmarDoseUseCase.executar(pendente.getId(), paciente.getId());

        Number estoque = (Number) em.getEntityManager()
                .createNativeQuery("SELECT quantidade_estoque FROM medicamento WHERE id = ?1")
                .setParameter(1, medicamento.getId())
                .getSingleResult();
        assertThat(statusDaDose(pendente.getId())).isEqualTo("CONFIRMADA_NO_HORARIO");
        assertThat(estoque.intValue()).isEqualTo(30);
    }

    @Test
    void confirmarNaoSobrescreveDoseJaConfirmada() {
        PacienteEntity paciente = paciente(cuidador());
        DoseEntity noHorario = dose(tratamento(medicamento(paciente, null), null), AGORA.minusMinutes(10), StatusDose.CONFIRMADA_NO_HORARIO);
        LocalDateTime confirmadaEm = horaConfirmacaoDaDose(noHorario.getId());

        assertThatThrownBy(() -> confirmarDoseUseCase.executar(noHorario.getId(), paciente.getId()))
                .isInstanceOf(DoseNaoConfirmavelException.class);
        assertThat(statusDaDose(noHorario.getId())).isEqualTo("CONFIRMADA_NO_HORARIO");
        assertThat(horaConfirmacaoDaDose(noHorario.getId())).isEqualTo(confirmadaEm);

        // Mesmo com leitura "velha" (vista como PENDENTE), o UPDATE condicional não altera a dose já registrada
        assertThat(doseAdapter.registrarSePendente(domainPendente(noHorario).confirmar(StatusDose.CONFIRMADA_COM_ATRASO, AGORA, ConfirmadoPor.PACIENTE)))
                .isZero();
        assertThat(statusDaDose(noHorario.getId())).isEqualTo("CONFIRMADA_NO_HORARIO");
    }

    @Test
    void confirmarDoseDeOutroPacienteOuDeRemedioRemovidoEhNaoEncontrada() {
        PacienteEntity paciente = paciente(cuidador());
        DoseEntity doOutro = dose(tratamento(medicamento(paciente(cuidador()), null), null), AGORA, StatusDose.PENDENTE);
        DoseEntity deRemovido = dose(tratamento(medicamento(paciente, AGORA.minusDays(1)), null), AGORA, StatusDose.PENDENTE);

        assertThatThrownBy(() -> confirmarDoseUseCase.executar(doOutro.getId(), paciente.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> confirmarDoseUseCase.executar(deRemovido.getId(), paciente.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(statusDaDose(doOutro.getId())).isEqualTo("PENDENTE");
        assertThat(statusDaDose(deRemovido.getId())).isEqualTo("PENDENTE");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Código de vínculo: UPDATE em massa e contexto de persistência
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void marcarComoUsadoAtualizaOCodigoJaCarregadoNaMesmaTransacao() {
        CuidadorEntity cuidador = cuidador();
        CodigoVinculoEntity entity = codigoVinculo(cuidador, paciente(cuidador), codigoAleatorio());
        // Carrega o código na sessão, como o ValidarCodigoVinculoUseCase faz antes de marcar
        CodigoVinculo carregado = codigoVinculoAdapter.findMaisRecenteByCodigoUnico(entity.getCodigoUnico()).orElseThrow();
        assertThat(carregado.isUsado()).isFalse();

        assertThat(codigoVinculoAdapter.marcarComoUsado(entity.getId(), AGORA)).isTrue();

        // Relida na MESMA transação: sem limpar o contexto, a entidade gerenciada continuaria usado=false
        assertThat(codigoVinculoAdapter.findMaisRecenteByCodigoUnico(entity.getCodigoUnico())).get()
                .extracting(CodigoVinculo::isUsado).isEqualTo(true);
    }

    @Test
    void codigoDeVinculoSoPodeSerMarcadoComoUsadoUmaVez() {
        CuidadorEntity cuidador = cuidador();
        CodigoVinculoEntity entity = codigoVinculo(cuidador, paciente(cuidador), codigoAleatorio());

        // RN11: uso único; a segunda marcação (ex.: requisição concorrente) não altera nenhuma linha
        assertThat(codigoVinculoAdapter.marcarComoUsado(entity.getId(), AGORA)).isTrue();
        assertThat(codigoVinculoAdapter.marcarComoUsado(entity.getId(), AGORA)).isFalse();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Remoção lógica
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void remocaoLogicaEscondeMedicamentoETratamentosEPreservaHistoricoDeDoses() {
        CuidadorEntity cuidador = cuidador();
        PacienteEntity paciente = paciente(cuidador);
        MedicamentoEntity medicamento = medicamento(paciente, null);
        MedicamentoEntity outroMedicamento = medicamento(paciente, null);
        TratamentoEntity tratamento = tratamento(medicamento, null);
        DoseEntity pendentePassada = dose(tratamento, AGORA.minusHours(2), StatusDose.PENDENTE);
        DoseEntity pendenteFutura = dose(tratamento, AGORA.plusHours(2), StatusDose.PENDENTE);
        DoseEntity noHorario = dose(tratamento, AGORA.minusDays(1), StatusDose.CONFIRMADA_NO_HORARIO);

        removerMedicamentoUseCase.executar(medicamento.getId(), cuidador.getId(), true);

        // Some de todas as consultas
        assertThat(medicamentoAdapter.findById(medicamento.getId())).isEmpty();
        assertThat(medicamentoAdapter.existsById(medicamento.getId())).isFalse();
        assertThat(medicamentoAdapter.findAllByPacienteId(paciente.getId()))
                .extracting(m -> m.getId()).containsExactly(outroMedicamento.getId());
        assertThat(tratamentoAdapter.findAllByMedicamentoId(medicamento.getId())).isEmpty();
        assertThat(tratamentoAdapter.findByIdAndCuidadorId(tratamento.getId(), cuidador.getId())).isEmpty();
        assertThat(tratamentoAdapter.findAllAtivos(HOJE)).extracting(t -> t.getId()).doesNotContain(tratamento.getId());

        // Mas as linhas continuam no banco, marcadas com a data da remoção
        assertThat(removidoEm("medicamento", medicamento.getId())).isEqualTo(AGORA);
        assertThat(removidoEm("tratamento", tratamento.getId())).isEqualTo(AGORA);
        assertThat(removidoEm("medicamento", outroMedicamento.getId())).isNull();

        // Doses: só a pendente futura é apagada
        assertThat(contar("dose", pendenteFutura.getId())).isZero();
        assertThat(contar("dose", pendentePassada.getId(), noHorario.getId())).isEqualTo(2);
    }

    @Test
    void findAllAtivosIgnoraTratamentosEncerradosERemovidos() {
        PacienteEntity paciente = paciente(cuidador());
        TratamentoEntity continuo = tratamento(medicamento(paciente, null), null);
        TratamentoEntity terminaHoje = tratamento(medicamento(paciente, null), null, HOJE);
        TratamentoEntity encerrado = tratamento(medicamento(paciente, null), null, HOJE.minusDays(1));
        TratamentoEntity removido = tratamento(medicamento(paciente, null), AGORA.minusDays(1));
        TratamentoEntity deMedicamentoRemovido = tratamento(medicamento(paciente, AGORA.minusDays(1)), null);

        List<Long> ativos = tratamentoAdapter.findAllAtivos(HOJE).stream().map(t -> t.getId()).toList();

        assertThat(ativos).contains(continuo.getId(), terminaHoje.getId())
                .doesNotContain(encerrado.getId(), removido.getId(), deMedicamentoRemovido.getId());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Edição de tratamento com regeneração de doses
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void edicaoRegeneraSomenteAsDosesPendentesFuturas() {
        CuidadorEntity cuidador = cuidador();
        TratamentoEntity tratamento = tratamento(medicamento(paciente(cuidador), null), null);
        DoseEntity pendentePassada = dose(tratamento, HOJE.minusDays(1).atTime(8, 0), StatusDose.PENDENTE);
        DoseEntity noHorarioHoje = dose(tratamento, HOJE.atTime(8, 0), StatusDose.CONFIRMADA_NO_HORARIO);
        DoseEntity pendenteFutura = dose(tratamento, HOJE.plusDays(1).atTime(8, 0), StatusDose.PENDENTE);
        DoseEntity registradaFutura = dose(tratamento, HOJE.plusDays(2).atTime(8, 0), StatusDose.CONFIRMADA_NO_HORARIO);

        editarTratamentoUseCase.executar(tratamento.getId(), HOJE.plusDays(2), List.of(LocalTime.of(9, 0)),
                List.copyOf(EnumSet.allOf(DayOfWeek.class)), cuidador.getId());

        assertThat(contar("dose", pendenteFutura.getId())).isZero();
        assertThat(contar("dose", pendentePassada.getId(), noHorarioHoje.getId(), registradaFutura.getId())).isEqualTo(3);
        // Hoje 09:00 já passou (agora = 14:30); geradas: amanhã e depois de amanhã às 09:00
        assertThat(horariosPendentesFuturos(tratamento.getId()))
                .containsExactly(HOJE.plusDays(1).atTime(9, 0), HOJE.plusDays(2).atTime(9, 0));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Unicidade e anonimização no banco
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void indiceUnicoImpedeDuasDosesNoMesmoHorarioDoMesmoTratamento() {
        TratamentoEntity tratamento = tratamento(medicamento(paciente(cuidador()), null), null);
        LocalDateTime horario = AGORA.plusDays(1);

        assertThatThrownBy(() -> doseAdapter.saveAll(List.of(
                Dose.pendente(tratamento.getId(), horario),
                Dose.pendente(tratamento.getId(), horario))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dosesAnonimizadasNoMesmoHorarioNaoConflitam() {
        LocalDateTime horario = AGORA.minusDays(1);
        String insert = "INSERT INTO dose (tratamento_id, horario_programado, status, confirmado_por) VALUES (NULL, ?1, 'CONFIRMADA_NO_HORARIO', 'PACIENTE')";

        em.getEntityManager().createNativeQuery(insert).setParameter(1, horario).executeUpdate();
        em.getEntityManager().createNativeQuery(insert).setParameter(1, horario).executeUpdate();

        Number total = (Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM dose WHERE tratamento_id IS NULL AND horario_programado = ?1")
                .setParameter(1, horario).getSingleResult();
        assertThat(total.longValue()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void apagarTratamentoAnonimizaAsDosesPorOnDeleteSetNull() {
        TratamentoEntity tratamento = tratamento(medicamento(paciente(cuidador()), null), null);
        DoseEntity noHorario = dose(tratamento, AGORA.minusDays(1), StatusDose.CONFIRMADA_NO_HORARIO);
        em.flush();
        em.clear();

        em.getEntityManager().createNativeQuery("DELETE FROM tratamento WHERE id = ?1")
                .setParameter(1, tratamento.getId()).executeUpdate();

        assertThat(contar("dose", noHorario.getId())).isEqualTo(1);
        assertThat(tratamentoIdDaDose(noHorario.getId())).isNull();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Varredura de doses: buscas de aviso e de encerramento e UPDATE atômico PENDENTE -> NAO_TOMADA
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void doseVistaPeloCuidadorSoSeOPacienteForDele() {
        CuidadorEntity dono = cuidador();
        PacienteEntity paciente = paciente(dono);
        DoseEntity dose = dose(tratamento(medicamento(paciente, null), null), AGORA.minusHours(1), StatusDose.PENDENTE);
        CuidadorEntity outro = cuidador();

        assertThat(doseAdapter.findDetalhadaByIdAndCuidadorId(dose.getId(), dono.getId()))
                .hasValueSatisfying(detalhe -> {
                    assertThat(detalhe.pacienteId()).isEqualTo(paciente.getId());
                    assertThat(detalhe.dose().getConfirmadoPor()).isNull();
                });
        assertThat(doseAdapter.findDetalhadaByIdAndCuidadorId(dose.getId(), outro.getId())).isEmpty();
        assertThat(doseAdapter.findDetalhadaByIdAndCuidadorId(Long.MAX_VALUE, dono.getId())).isEmpty();
        // Remédio removido: a dose some também para o dono
        DoseEntity deRemovido = dose(tratamento(medicamento(paciente, AGORA), null), AGORA.minusHours(1), StatusDose.PENDENTE);
        assertThat(doseAdapter.findDetalhadaByIdAndCuidadorId(deRemovido.getId(), dono.getId())).isEmpty();
    }

    @Test
    void registrarSePendenteGravaQuemConfirmouELeituraDevolve() {
        PacienteEntity paciente = paciente(cuidador());
        DoseEntity pendente = dose(tratamento(medicamento(paciente, null), null), AGORA.minusMinutes(10), StatusDose.PENDENTE);

        int alteradas = doseAdapter.registrarSePendente(domainPendente(pendente)
                .confirmar(StatusDose.CONFIRMADA_NO_HORARIO, AGORA, ConfirmadoPor.CUIDADOR));

        assertThat(alteradas).isEqualTo(1);
        assertThat(em.getEntityManager().createNativeQuery("SELECT confirmado_por FROM dose WHERE id = ?1")
                .setParameter(1, pendente.getId()).getSingleResult()).isEqualTo("CUIDADOR");
        assertThat(doseAdapter.findDetalhadaByIdAndPacienteId(pendente.getId(), paciente.getId()))
                .hasValueSatisfying(detalhe -> assertThat(detalhe.dose().getConfirmadoPor()).isEqualTo(ConfirmadoPor.CUIDADOR));
    }

    @Test
    void registrarSePendenteMarcaDosePendenteComoNaoTomada() {
        TratamentoEntity tratamento = tratamento(medicamento(paciente(cuidador()), null), null);
        DoseEntity pendente = dose(tratamento, AGORA.minusHours(1), StatusDose.PENDENTE);

        int alteradas = doseAdapter.registrarSePendente(domainPendente(pendente).marcarComoNaoTomada());

        assertThat(alteradas).isEqualTo(1);
        assertThat(statusDaDose(pendente.getId())).isEqualTo("NAO_TOMADA");
    }

    @Test
    void registrarSePendenteNaoSobrescreveDoseJaConfirmada() {
        TratamentoEntity tratamento = tratamento(medicamento(paciente(cuidador()), null), null);
        DoseEntity noHorario = dose(tratamento, AGORA.minusHours(1), StatusDose.CONFIRMADA_NO_HORARIO);
        // Leitura "velha": o verificador viu a dose como PENDENTE antes da confirmação concorrente
        Dose lidaComoPendente = domainPendente(noHorario);

        int alteradas = doseAdapter.registrarSePendente(lidaComoPendente.marcarComoNaoTomada());

        assertThat(alteradas).isZero();
        assertThat(statusDaDose(noHorario.getId())).isEqualTo("CONFIRMADA_NO_HORARIO");
    }

    @Test
    void findPendentesParaAvisoPegaSoAsQuePassaramDaToleranciaSemEventoNemEncerramento() {
        // Horários em 2000: nenhum dado de desenvolvimento do banco local interfere na busca
        LocalDateTime agora = LocalDateTime.of(2000, 1, 10, 12, 0);
        java.time.Duration tolerancia = java.time.Duration.ofMinutes(30);
        CuidadorEntity cuidador = cuidador();
        PacienteEntity paciente = paciente(cuidador);
        MedicamentoEntity medicamento = medicamento(paciente, null);
        TratamentoEntity tratamento = tratamento(medicamento, null);
        // Aviso: horário + 30 <= 12:00 -> horário <= 11:30 (inclusive). Ainda aberta: horário + 30 + 30 > 12:00 ->
        // horário > 11:00 (em 11:00 exatas a dose já encerrou)
        DoseEntity maisAntiga = dose(tratamento, agora.minusMinutes(60).plusSeconds(1), StatusDose.PENDENTE);
        DoseEntity noLimiteDoAviso = dose(tratamento, agora.minus(tolerancia), StatusDose.PENDENTE);
        DoseEntity dentroDaTolerancia = dose(tratamento, agora.minus(tolerancia).plusSeconds(1), StatusDose.PENDENTE);
        DoseEntity noLimiteDoEncerramento = dose(tratamento, agora.minusMinutes(60), StatusDose.PENDENTE);
        DoseEntity jaAvisada = dose(tratamento, agora.minusMinutes(45), StatusDose.PENDENTE);
        eventoAdapter.criarSeAusente(jaAvisada.getId());
        DoseEntity noHorario = dose(tratamento, agora.minusMinutes(50), StatusDose.CONFIRMADA_NO_HORARIO);
        DoseEntity deTratamentoRemovido = dose(tratamento(medicamento, agora), agora.minusMinutes(35), StatusDose.PENDENTE);
        DoseEntity deMedicamentoRemovido = dose(tratamento(medicamento(paciente, agora), null), agora.minusMinutes(40),
                StatusDose.PENDENTE);
        em.getEntityManager()
                .createNativeQuery("INSERT INTO dose (tratamento_id, horario_programado, status) VALUES (NULL, ?1, 'PENDENTE')")
                .setParameter(1, agora.minusMinutes(42))
                .executeUpdate();

        List<DoseParaAviso> paraAviso = doseAdapter.findPendentesParaAviso(agora, tolerancia,
                java.time.Duration.ofMinutes(30), 100);

        assertThat(paraAviso).extracting(DoseParaAviso::doseId)
                .containsExactly(maisAntiga.getId(), noLimiteDoAviso.getId())
                .doesNotContain(dentroDaTolerancia.getId(), noLimiteDoEncerramento.getId(), jaAvisada.getId(),
                        noHorario.getId(), deTratamentoRemovido.getId(), deMedicamentoRemovido.getId());
        DoseParaAviso primeira = paraAviso.get(0);
        assertThat(primeira.cuidadorId()).isEqualTo(cuidador.getId());
        assertThat(primeira.pacienteId()).isEqualTo(paciente.getId());
        assertThat(primeira.nomePaciente()).isEqualTo(paciente.getNome());
        assertThat(primeira.horarioProgramado()).isEqualTo(agora.minusMinutes(60).plusSeconds(1));
    }

    @Test
    void findPendentesParaAvisoRespeitaOLimiteComecandoPelasMaisAntigas() {
        LocalDateTime agora = LocalDateTime.of(2000, 1, 10, 12, 0);
        TratamentoEntity tratamento = tratamento(medicamento(paciente(cuidador()), null), null);
        DoseEntity primeira = dose(tratamento, agora.minusMinutes(50), StatusDose.PENDENTE);
        DoseEntity segunda = dose(tratamento, agora.minusMinutes(45), StatusDose.PENDENTE);
        dose(tratamento, agora.minusMinutes(40), StatusDose.PENDENTE);

        List<DoseParaAviso> paraAviso = doseAdapter.findPendentesParaAviso(agora, java.time.Duration.ofMinutes(30),
                java.time.Duration.ofMinutes(30), 2);

        assertThat(paraAviso).extracting(DoseParaAviso::doseId).containsExactly(primeira.getId(), segunda.getId());
    }

    @Test
    void encerrarVencidasMarcaNaoTomadaSoAsVencidasPendentesENaoRemovidas() {
        LocalDateTime agora = LocalDateTime.of(2000, 1, 10, 12, 0);
        PacienteEntity paciente = paciente(cuidador());
        MedicamentoEntity medicamento = medicamento(paciente, null);
        TratamentoEntity tratamento = tratamento(medicamento, null);
        // Padrão 30 + margem 30: vence quando horário + 60 <= 12:00, ou seja, horário <= 11:00 (inclusive)
        DoseEntity maisAntiga = dose(tratamento, agora.minusDays(1), StatusDose.PENDENTE);
        DoseEntity noLimite = dose(tratamento, agora.minusMinutes(60), StatusDose.PENDENTE);
        DoseEntity umSegundoAntesDeVencer = dose(tratamento, agora.minusMinutes(60).plusSeconds(1), StatusDose.PENDENTE);
        DoseEntity noHorario = dose(tratamento, agora.minusHours(3), StatusDose.CONFIRMADA_NO_HORARIO);
        DoseEntity deTratamentoRemovido = dose(tratamento(medicamento, agora), agora.minusHours(2), StatusDose.PENDENTE);
        DoseEntity deMedicamentoRemovido = dose(tratamento(medicamento(paciente, agora), null), agora.minusHours(4),
                StatusDose.PENDENTE);

        List<DoseEncerrada> encerradas = doseAdapter.encerrarVencidas(agora, java.time.Duration.ofMinutes(30),
                java.time.Duration.ofMinutes(30), 100);

        assertThat(encerradas).containsExactlyInAnyOrder(new DoseEncerrada(maisAntiga.getId(), paciente.getId()),
                new DoseEncerrada(noLimite.getId(), paciente.getId()));
        assertThat(statusDaDose(maisAntiga.getId())).isEqualTo("NAO_TOMADA");
        assertThat(statusDaDose(noLimite.getId())).isEqualTo("NAO_TOMADA");
        assertThat(horaConfirmacaoDaDose(noLimite.getId())).isNull();
        assertThat(statusDaDose(umSegundoAntesDeVencer.getId())).isEqualTo("PENDENTE");
        assertThat(statusDaDose(noHorario.getId())).isEqualTo("CONFIRMADA_NO_HORARIO");
        assertThat(statusDaDose(deTratamentoRemovido.getId())).isEqualTo("PENDENTE");
        assertThat(statusDaDose(deMedicamentoRemovido.getId())).isEqualTo("PENDENTE");
    }

    @Test
    void encerrarVencidasUsaAToleranciaDoCuidadorDonoDeCadaPaciente() {
        LocalDateTime agora = LocalDateTime.of(2000, 1, 10, 12, 0);
        java.time.Duration padrao = java.time.Duration.ofMinutes(30);
        java.time.Duration margem = java.time.Duration.ofMinutes(30);
        CuidadorEntity comCinco = cuidador();
        CuidadorEntity comCentoEVinte = cuidador();
        configuracaoAdapter.salvarToleranciaMinutos(comCinco.getId(), Tolerancia.MINIMO_MINUTOS);
        configuracaoAdapter.salvarToleranciaMinutos(comCentoEVinte.getId(), Tolerancia.MAXIMO_MINUTOS);
        TratamentoEntity doCinco = tratamento(medicamento(paciente(comCinco), null), null);
        TratamentoEntity doCentoEVinte = tratamento(medicamento(paciente(comCentoEVinte), null), null);
        // 5 + 30: vence às 11:25 (inclusive). Com a padrão (60 no total) ainda estaria aberta
        DoseEntity cincoNoLimite = dose(doCinco, agora.minusMinutes(35), StatusDose.PENDENTE);
        DoseEntity cincoAberta = dose(doCinco, agora.minusMinutes(35).plusSeconds(1), StatusDose.PENDENTE);
        // 120 + 30: vence às 09:30 (inclusive). Com a padrão já estaria vencida
        DoseEntity centoEVinteNoLimite = dose(doCentoEVinte, agora.minusMinutes(150), StatusDose.PENDENTE);
        DoseEntity centoEVinteAberta = dose(doCentoEVinte, agora.minusMinutes(150).plusSeconds(1), StatusDose.PENDENTE);

        List<Long> encerradas = doseAdapter.encerrarVencidas(agora, padrao, margem, 100).stream()
                .map(DoseEncerrada::doseId)
                .toList();

        assertThat(encerradas).contains(cincoNoLimite.getId(), centoEVinteNoLimite.getId())
                .doesNotContain(cincoAberta.getId(), centoEVinteAberta.getId());
        assertThat(statusDaDose(cincoAberta.getId())).isEqualTo("PENDENTE");
        assertThat(statusDaDose(centoEVinteAberta.getId())).isEqualTo("PENDENTE");
    }

    @Test
    void encerrarVencidasRespeitaOLimiteComecandoPelasMaisAntigas() {
        LocalDateTime agora = LocalDateTime.of(2000, 1, 10, 12, 0);
        TratamentoEntity tratamento = tratamento(medicamento(paciente(cuidador()), null), null);
        DoseEntity primeira = dose(tratamento, agora.minusHours(4), StatusDose.PENDENTE);
        DoseEntity segunda = dose(tratamento, agora.minusHours(3), StatusDose.PENDENTE);
        DoseEntity terceira = dose(tratamento, agora.minusHours(2), StatusDose.PENDENTE);

        List<DoseEncerrada> encerradas = doseAdapter.encerrarVencidas(agora, java.time.Duration.ofMinutes(30),
                java.time.Duration.ofMinutes(30), 2);

        assertThat(encerradas).extracting(DoseEncerrada::doseId).containsExactlyInAnyOrder(primeira.getId(), segunda.getId());
        assertThat(statusDaDose(terceira.getId())).isEqualTo("PENDENTE");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Evento de notificação (V12): idempotência, reivindicação e finalização atômicas
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void criarEventoSeAusenteEhIdempotentePorDose() {
        DoseEntity dose = dose(tratamento(medicamento(paciente(cuidador()), null), null), AGORA.minusHours(1),
                StatusDose.PENDENTE);

        java.util.Optional<Long> criado = eventoAdapter.criarSeAusente(dose.getId());
        java.util.Optional<Long> repetido = eventoAdapter.criarSeAusente(dose.getId());

        assertThat(criado).isPresent();
        assertThat(repetido).isEmpty();
        assertThat(eventosDaDose(dose.getId())).isEqualTo(1);
        assertThat(colunaDoEvento(criado.get(), "status")).isEqualTo("PENDENTE");
        assertThat(((Number) colunaDoEvento(criado.get(), "tentativas")).intValue()).isZero();
        assertThat(colunaDoEvento(criado.get(), "ultima_tentativa")).isNull();
        // O aviso não muda a dose
        assertThat(statusDaDose(dose.getId())).isEqualTo("PENDENTE");
    }

    @Test
    void reivindicarSoGanhaComAsTentativasEsperadasEEnquantoPendente() {
        DoseEntity dose = dose(tratamento(medicamento(paciente(cuidador()), null), null), AGORA.minusHours(1),
                StatusDose.PENDENTE);
        Long eventoId = eventoAdapter.criarSeAusente(dose.getId()).orElseThrow();

        assertThat(eventoAdapter.reivindicar(eventoId, 0, AGORA)).isTrue();
        // Segunda reivindicação com a mesma expectativa (outro job concorrente): perde
        assertThat(eventoAdapter.reivindicar(eventoId, 0, AGORA)).isFalse();
        assertThat(((Number) colunaDoEvento(eventoId, "tentativas")).intValue()).isEqualTo(1);
        assertThat(paraLocalDateTime(colunaDoEvento(eventoId, "ultima_tentativa"))).isEqualTo(AGORA);

        eventoAdapter.marcarEnviado(eventoId);
        assertThat(colunaDoEvento(eventoId, "status")).isEqualTo("ENVIADO");
        assertThat(eventoAdapter.reivindicar(eventoId, 1, AGORA)).isFalse();
        // Finalizar de novo não muda um evento que já saiu de PENDENTE
        eventoAdapter.marcarFalha(eventoId, MotivoFalhaNotificacao.SEM_TOKEN);
        assertThat(colunaDoEvento(eventoId, "status")).isEqualTo("ENVIADO");
        assertThat(colunaDoEvento(eventoId, "motivo_falha")).isNull();
    }

    @Test
    void marcarFalhaGravaOMotivo() {
        DoseEntity dose = dose(tratamento(medicamento(paciente(cuidador()), null), null), AGORA.minusHours(1),
                StatusDose.PENDENTE);
        Long eventoId = eventoAdapter.criarSeAusente(dose.getId()).orElseThrow();

        eventoAdapter.marcarFalha(eventoId, MotivoFalhaNotificacao.TOKEN_INVALIDO);

        assertThat(colunaDoEvento(eventoId, "status")).isEqualTo("FALHA");
        assertThat(colunaDoEvento(eventoId, "motivo_falha")).isEqualTo("TOKEN_INVALIDO");
    }

    @Test
    void checkDaTabelaDeEventoRejeitaStatusDesconhecido() {
        DoseEntity dose = dose(tratamento(medicamento(paciente(cuidador()), null), null), AGORA.minusHours(1),
                StatusDose.PENDENTE);

        assertThatThrownBy(() -> em.getEntityManager()
                .createNativeQuery("INSERT INTO evento_notificacao (status, tentativas, dose_id) VALUES ('ENVIANDO', 0, ?1)")
                .setParameter(1, dose.getId())
                .executeUpdate())
                .hasMessageContaining("ck_evento_notificacao_status");
    }

    @Test
    void checkDaTabelaDeEventoRejeitaTentativasNegativas() {
        DoseEntity dose = dose(tratamento(medicamento(paciente(cuidador()), null), null), AGORA.minusHours(1),
                StatusDose.PENDENTE);

        assertThatThrownBy(() -> em.getEntityManager()
                .createNativeQuery("INSERT INTO evento_notificacao (status, tentativas, dose_id) VALUES ('PENDENTE', -1, ?1)")
                .setParameter(1, dose.getId())
                .executeUpdate())
                .hasMessageContaining("ck_evento_notificacao_tentativas");
    }

    @Test
    void uniqueDaTabelaDeEventoImpedeDoisEventosParaAMesmaDose() {
        DoseEntity dose = dose(tratamento(medicamento(paciente(cuidador()), null), null), AGORA.minusHours(1),
                StatusDose.PENDENTE);
        eventoAdapter.criarSeAusente(dose.getId()).orElseThrow();

        // Sem o ON CONFLICT do adapter, o banco recusa o segundo evento da mesma dose
        assertThatThrownBy(() -> em.getEntityManager()
                .createNativeQuery("INSERT INTO evento_notificacao (status, tentativas, dose_id) VALUES ('PENDENTE', 0, ?1)")
                .setParameter(1, dose.getId())
                .executeUpdate())
                .hasMessageContaining("uk_evento_notificacao_dose");
    }

    @Test
    void apagarADoseApagaOEventoPorOnDeleteCascade() {
        TratamentoEntity tratamento = tratamento(medicamento(paciente(cuidador()), null), null);
        DoseEntity dose = dose(tratamento, AGORA.minusHours(1), StatusDose.PENDENTE);
        Long evento = eventoAdapter.criarSeAusente(dose.getId()).orElseThrow();
        DoseEntity outra = dose(tratamento, AGORA.minusHours(2), StatusDose.PENDENTE);
        Long eventoDaOutra = eventoAdapter.criarSeAusente(outra.getId()).orElseThrow();

        em.getEntityManager().createNativeQuery("DELETE FROM dose WHERE id = ?1")
                .setParameter(1, dose.getId())
                .executeUpdate();

        assertThat(contar("evento_notificacao", evento)).isZero();
        assertThat(contar("evento_notificacao", eventoDaOutra)).isEqualTo(1);
    }

    @Test
    void exclusaoDeContaComEventosPendenteEnviadoEFalhaApagaOsEventosDoCuidador() {
        CuidadorEntity cuidador = cuidador();
        TratamentoEntity tratamento = tratamento(medicamento(paciente(cuidador), null), null);
        // Evento em dose ainda PENDENTE (aviso feito, dose não encerrada)
        DoseEntity pendente = dose(tratamento, AGORA.minusHours(1), StatusDose.PENDENTE);
        Long eventoPendente = eventoAdapter.criarSeAusente(pendente.getId()).orElseThrow();
        // Evento ENVIADO em dose que depois foi confirmada com atraso (registrada: a dose fica, anonimizada)
        DoseEntity comAtraso = dose(tratamento, AGORA.minusHours(2), StatusDose.CONFIRMADA_COM_ATRASO);
        Long eventoEnviado = eventoAdapter.criarSeAusente(comAtraso.getId()).orElseThrow();
        eventoAdapter.marcarEnviado(eventoEnviado);
        // Evento FALHA em dose encerrada como NAO_TOMADA
        DoseEntity naoTomada = dose(tratamento, AGORA.minusHours(5), StatusDose.NAO_TOMADA);
        Long eventoFalha = eventoAdapter.criarSeAusente(naoTomada.getId()).orElseThrow();
        eventoAdapter.marcarFalha(eventoFalha, MotivoFalhaNotificacao.SEM_TOKEN);

        CuidadorEntity outro = cuidador();
        DoseEntity doseDoOutro = dose(tratamento(medicamento(paciente(outro), null), null), AGORA.minusHours(1),
                StatusDose.PENDENTE);
        Long eventoDoOutro = eventoAdapter.criarSeAusente(doseDoOutro.getId()).orElseThrow();

        excluirContaUseCase.executar(cuidador.getId());

        assertThat(contar("cuidador", cuidador.getId())).isZero();
        assertThat(contar("evento_notificacao", eventoPendente, eventoEnviado, eventoFalha)).isZero();
        assertThat(contar("dose", pendente.getId())).isZero();
        assertThat(contar("dose", comAtraso.getId(), naoTomada.getId())).isEqualTo(2);
        assertThat(tratamentoIdDaDose(comAtraso.getId())).isNull();
        assertThat(contar("evento_notificacao", eventoDoOutro)).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Configuração de alerta (V13): tolerância por cuidador
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void semConfiguracaoNaoHaToleranciaPropriaESalvarDuasVezesAtualiza() {
        CuidadorEntity cuidador = cuidador();

        assertThat(configuracaoAdapter.findToleranciaMinutos(cuidador.getId())).isEmpty();

        configuracaoAdapter.salvarToleranciaMinutos(cuidador.getId(), 45);
        configuracaoAdapter.salvarToleranciaMinutos(cuidador.getId(), 90);

        assertThat(configuracaoAdapter.findToleranciaMinutos(cuidador.getId())).contains(90);
        assertThat(contarPorCuidador("configuracao_alerta", cuidador.getId())).isEqualTo(1);
    }

    @Test
    void checkDaConfiguracaoRecusaToleranciaForaDe5a120() {
        CuidadorEntity cuidador = cuidador();

        assertThatThrownBy(() -> configuracaoAdapter.salvarToleranciaMinutos(cuidador.getId(), 4))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void checkDaConfiguracaoRecusaToleranciaAcimaDe120() {
        CuidadorEntity cuidador = cuidador();

        assertThatThrownBy(() -> configuracaoAdapter.salvarToleranciaMinutos(cuidador.getId(), 121))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void checkDaConfiguracaoAceitaOsLimitesInclusivos5e120() {
        CuidadorEntity comCinco = cuidador();
        CuidadorEntity comCentoEVinte = cuidador();

        configuracaoAdapter.salvarToleranciaMinutos(comCinco.getId(), 5);
        configuracaoAdapter.salvarToleranciaMinutos(comCentoEVinte.getId(), 120);

        assertThat(configuracaoAdapter.findToleranciaMinutos(comCinco.getId())).contains(5);
        assertThat(configuracaoAdapter.findToleranciaMinutos(comCentoEVinte.getId())).contains(120);
    }

    @Test
    void apagarOCuidadorApagaAConfiguracaoPorOnDeleteCascade() {
        CuidadorEntity cuidador = cuidador();
        configuracaoAdapter.salvarToleranciaMinutos(cuidador.getId(), 15);

        // Direto no banco, sem o use case: a FK com ON DELETE CASCADE garante a limpeza
        em.getEntityManager().createNativeQuery("DELETE FROM cuidador WHERE id = ?1")
                .setParameter(1, cuidador.getId())
                .executeUpdate();

        assertThat(contarPorCuidador("configuracao_alerta", cuidador.getId())).isZero();
    }

    @Test
    void exclusaoDeContaApagaAConfiguracaoDeAlerta() {
        CuidadorEntity cuidador = cuidador();
        configuracaoAdapter.salvarToleranciaMinutos(cuidador.getId(), 15);
        CuidadorEntity outro = cuidador();
        configuracaoAdapter.salvarToleranciaMinutos(outro.getId(), 20);

        excluirContaUseCase.executar(cuidador.getId());

        assertThat(contarPorCuidador("configuracao_alerta", cuidador.getId())).isZero();
        assertThat(configuracaoAdapter.findToleranciaMinutos(outro.getId())).contains(20);
    }

    @Test
    void avisoUsaAToleranciaDoCuidadorDonoDeCadaPaciente() {
        LocalDateTime agora = LocalDateTime.of(2000, 1, 10, 12, 0);
        java.time.Duration padrao = java.time.Duration.ofMinutes(30);
        CuidadorEntity comDez = cuidador();
        CuidadorEntity comSessenta = cuidador();
        CuidadorEntity semConfiguracao = cuidador();
        configuracaoAdapter.salvarToleranciaMinutos(comDez.getId(), 10);
        configuracaoAdapter.salvarToleranciaMinutos(comSessenta.getId(), 60);
        // Todas às 11:45: 11:55 (10 min) já passou; 12:15 (padrão 30) e 12:45 (60) ainda não
        TratamentoEntity tratamentoDoDez = tratamento(medicamento(paciente(comDez), null), null);
        DoseEntity doDez = dose(tratamentoDoDez, agora.minusMinutes(15), StatusDose.PENDENTE);
        TratamentoEntity tratamentoDoSessenta = tratamento(medicamento(paciente(comSessenta), null), null);
        DoseEntity doSessenta = dose(tratamentoDoSessenta, agora.minusMinutes(15), StatusDose.PENDENTE);
        // O encerramento também é por cuidador: 11:15 + 10 + 30 = 11:55 já encerrou (com a padrão ainda estaria aberta);
        // 11:00 + 60 = 12:00 é aviso (com a padrão, 11:00 + 30 + 30 = 12:00 já teria encerrado)
        DoseEntity doDezEncerrada = dose(tratamentoDoDez, agora.minusMinutes(45), StatusDose.PENDENTE);
        DoseEntity doSessentaNoAviso = dose(tratamentoDoSessenta, agora.minusMinutes(60), StatusDose.PENDENTE);
        TratamentoEntity tratamentoPadrao = tratamento(medicamento(paciente(semConfiguracao), null), null);
        DoseEntity doPadraoNoPrazo = dose(tratamentoPadrao, agora.minusMinutes(15), StatusDose.PENDENTE);
        // 11:30 + 30 (padrão) = 12:00: no limite, já é aviso
        DoseEntity doPadraoNoLimite = dose(tratamentoPadrao, agora.minusMinutes(30), StatusDose.PENDENTE);

        List<Long> paraAviso = doseAdapter.findPendentesParaAviso(agora, padrao, java.time.Duration.ofMinutes(30), 100)
                .stream()
                .map(DoseParaAviso::doseId)
                .toList();

        assertThat(paraAviso).contains(doDez.getId(), doPadraoNoLimite.getId(), doSessentaNoAviso.getId())
                .doesNotContain(doSessenta.getId(), doPadraoNoPrazo.getId(), doDezEncerrada.getId());
    }

    @Test
    void dosesDetalhadasTrazemAToleranciaPropriaDoCuidadorOuNula() {
        CuidadorEntity comConfiguracao = cuidador();
        configuracaoAdapter.salvarToleranciaMinutos(comConfiguracao.getId(), 60);
        PacienteEntity pacienteComConfiguracao = paciente(comConfiguracao);
        DoseEntity dose = dose(tratamento(medicamento(pacienteComConfiguracao, null), null), AGORA.minusHours(1),
                StatusDose.PENDENTE);
        PacienteEntity pacienteSemConfiguracao = paciente(cuidador());
        dose(tratamento(medicamento(pacienteSemConfiguracao, null), null), AGORA.minusHours(1), StatusDose.PENDENTE);

        assertThat(doseAdapter.findDetalhadasDoPacienteEntre(pacienteComConfiguracao.getId(), HOJE.atStartOfDay(),
                HOJE.plusDays(1).atStartOfDay())).singleElement()
                .satisfies(d -> assertThat(d.toleranciaMinutosDoCuidador()).isEqualTo(60));
        assertThat(doseAdapter.findDetalhadaByIdAndPacienteId(dose.getId(), pacienteComConfiguracao.getId()))
                .hasValueSatisfying(d -> assertThat(d.toleranciaMinutosDoCuidador()).isEqualTo(60));
        assertThat(doseAdapter.findDetalhadasDoPacienteEntre(pacienteSemConfiguracao.getId(), HOJE.atStartOfDay(),
                HOJE.plusDays(1).atStartOfDay())).singleElement()
                .satisfies(d -> assertThat(d.toleranciaMinutosDoCuidador()).isNull());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Montagem de dados e consultas de verificação (SQL nativo: lê o estado real do banco)
    // ---------------------------------------------------------------------------------------------------------

    private CuidadorEntity cuidador() {
        return em.persist(CuidadorEntity.builder()
                .nome("Cuidador JPA")
                .email("jpa-" + UUID.randomUUID() + "@teste.com")
                .senhaHash("hash")
                .dataCadastro(AGORA.minusMonths(1))
                .consentimentoLgpd(true)
                .dataConsentimento(AGORA.minusMonths(1))
                .build());
    }

    private PacienteEntity paciente(CuidadorEntity cuidador) {
        return em.persist(PacienteEntity.builder()
                .nome("Paciente " + UUID.randomUUID())
                .dataNascimento(LocalDate.of(1945, 3, 12))
                .cuidador(cuidador)
                .build());
    }

    private PacienteEntity pacienteComNome(CuidadorEntity cuidador, String nome) {
        return em.persist(PacienteEntity.builder()
                .nome(nome)
                .dataNascimento(LocalDate.of(1945, 3, 12))
                .cuidador(cuidador)
                .build());
    }

    private MedicamentoEntity medicamento(PacienteEntity paciente, LocalDateTime removidoEm) {
        return em.persist(MedicamentoEntity.builder()
                .nome("Losartana")
                .dosagem("50mg")
                .tipo("COMPRIMIDO")
                .paciente(paciente)
                .removidoEm(removidoEm)
                .build());
    }

    private TratamentoEntity tratamento(MedicamentoEntity medicamento, LocalDateTime removidoEm) {
        return tratamento(medicamento, removidoEm, null);
    }

    private TratamentoEntity tratamento(MedicamentoEntity medicamento, LocalDateTime removidoEm, LocalDate dataFim) {
        return em.persist(TratamentoEntity.builder()
                .medicamento(medicamento)
                .dataInicio(HOJE.minusDays(10))
                .dataFim(dataFim)
                .horarios(List.of("08:00"))
                .diasSemana(TODOS_OS_DIAS)
                .removidoEm(removidoEm)
                .build());
    }

    private DoseEntity dose(TratamentoEntity tratamento, LocalDateTime horario, StatusDose status) {
        return em.persist(DoseEntity.builder()
                .tratamento(tratamento)
                .horarioProgramado(horario)
                .horaConfirmacao(status.isRegistrada() ? horario.plusMinutes(5) : null)
                .status(status)
                .confirmadoPor(status == StatusDose.CONFIRMADA_NO_HORARIO || status == StatusDose.CONFIRMADA_COM_ATRASO
                        ? ConfirmadoPor.PACIENTE : null)
                .build());
    }

    private CodigoVinculoEntity codigoVinculo(CuidadorEntity cuidador, PacienteEntity paciente) {
        return codigoVinculo(cuidador, paciente, "123456");
    }

    private CodigoVinculoEntity codigoVinculo(CuidadorEntity cuidador, PacienteEntity paciente, String codigoUnico) {
        return em.persist(CodigoVinculoEntity.builder()
                .codigoUnico(codigoUnico)
                .cuidador(cuidador)
                .paciente(paciente)
                .expiraEm(AGORA.plusDays(1))
                .usado(false)
                .build());
    }

    private static String codigoAleatorio() {
        return String.format("%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private long contar(String tabela, Long... ids) {
        Number total = (Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM " + tabela + " WHERE id IN (?1)")
                .setParameter(1, List.of(ids))
                .getSingleResult();
        return total.longValue();
    }

    private static Dose domainPendente(DoseEntity entity) {
        return Dose.builder()
                .id(entity.getId())
                .tratamentoId(entity.getTratamento().getId())
                .horarioProgramado(entity.getHorarioProgramado())
                .status(StatusDose.PENDENTE)
                .build();
    }

    private long cuidadoresComToken(String token) {
        Number total = (Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM cuidador WHERE fcm_token = ?1")
                .setParameter(1, token)
                .getSingleResult();
        return total.longValue();
    }

    private String statusDaDose(Long doseId) {
        return (String) em.getEntityManager()
                .createNativeQuery("SELECT status FROM dose WHERE id = ?1")
                .setParameter(1, doseId)
                .getSingleResult();
    }

    private LocalDateTime horaConfirmacaoDaDose(Long doseId) {
        Object valor = em.getEntityManager()
                .createNativeQuery("SELECT hora_confirmacao FROM dose WHERE id = ?1")
                .setParameter(1, doseId)
                .getSingleResult();
        if (valor == null) {
            return null;
        }
        return valor instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) valor;
    }

    private Long tratamentoIdDaDose(Long doseId) {
        Object valor = em.getEntityManager()
                .createNativeQuery("SELECT tratamento_id FROM dose WHERE id = ?1")
                .setParameter(1, doseId)
                .getSingleResult();
        return valor == null ? null : ((Number) valor).longValue();
    }

    private LocalDateTime removidoEm(String tabela, Long id) {
        Object valor = em.getEntityManager()
                .createNativeQuery("SELECT removido_em FROM " + tabela + " WHERE id = ?1")
                .setParameter(1, id)
                .getSingleResult();
        if (valor == null) {
            return null;
        }
        return valor instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) valor;
    }

    @SuppressWarnings("unchecked")
    private List<LocalDateTime> horariosPendentesFuturos(Long tratamentoId) {
        List<Object> valores = em.getEntityManager()
                .createNativeQuery("SELECT horario_programado FROM dose WHERE tratamento_id = ?1 AND status = 'PENDENTE' "
                        + "AND horario_programado > ?2 ORDER BY horario_programado")
                .setParameter(1, tratamentoId)
                .setParameter(2, AGORA)
                .getResultList();
        return valores.stream()
                .map(valor -> valor instanceof java.sql.Timestamp timestamp
                        ? timestamp.toLocalDateTime()
                        : (LocalDateTime) valor)
                .toList();
    }

    private long contarPorCuidador(String tabela, Long cuidadorId) {
        Number total = (Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM " + tabela + " WHERE cuidador_id = ?1")
                .setParameter(1, cuidadorId)
                .getSingleResult();
        return total.longValue();
    }

    private long eventosDaDose(Long doseId) {
        Number total = (Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM evento_notificacao WHERE dose_id = ?1")
                .setParameter(1, doseId)
                .getSingleResult();
        return total.longValue();
    }

    private Object colunaDoEvento(Long eventoId, String coluna) {
        return em.getEntityManager()
                .createNativeQuery("SELECT " + coluna + " FROM evento_notificacao WHERE id = ?1")
                .setParameter(1, eventoId)
                .getSingleResult();
    }

    private static LocalDateTime paraLocalDateTime(Object valor) {
        if (valor == null) {
            return null;
        }
        return valor instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) valor;
    }
}
