package com.medcare.senior.infrastructure.persistence;

import com.medcare.senior.application.service.GeradorDeDosesService;
import com.medcare.senior.application.usecase.EditarTratamentoUseCase;
import com.medcare.senior.application.usecase.ExcluirContaUseCase;
import com.medcare.senior.application.usecase.RemoverMedicamentoUseCase;
import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.infrastructure.persistence.adapter.CodigoVinculoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.CuidadorRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.DoseRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.IdosoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.MedicamentoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.TratamentoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.entity.CodigoVinculoEntity;
import com.medcare.senior.infrastructure.persistence.entity.CuidadorEntity;
import com.medcare.senior.infrastructure.persistence.entity.DoseEntity;
import com.medcare.senior.infrastructure.persistence.entity.IdosoEntity;
import com.medcare.senior.infrastructure.persistence.entity.MedicamentoEntity;
import com.medcare.senior.infrastructure.persistence.entity.TratamentoEntity;
import com.medcare.senior.infrastructure.persistence.mapper.CodigoVinculoMapper;
import com.medcare.senior.infrastructure.persistence.mapper.CuidadorMapper;
import com.medcare.senior.infrastructure.persistence.mapper.DoseMapper;
import com.medcare.senior.infrastructure.persistence.mapper.IdosoMapper;
import com.medcare.senior.infrastructure.persistence.mapper.MedicamentoMapper;
import com.medcare.senior.infrastructure.persistence.mapper.TratamentoMapper;
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
@Import({
        CuidadorRepositoryAdapter.class, IdosoRepositoryAdapter.class, MedicamentoRepositoryAdapter.class,
        TratamentoRepositoryAdapter.class, DoseRepositoryAdapter.class, CodigoVinculoRepositoryAdapter.class,
        CuidadorMapper.class, IdosoMapper.class, MedicamentoMapper.class, TratamentoMapper.class, DoseMapper.class,
        CodigoVinculoMapper.class,
        ExcluirContaUseCase.class, RemoverMedicamentoUseCase.class, EditarTratamentoUseCase.class,
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

    // ---------------------------------------------------------------------------------------------------------
    // Exclusão de conta (LGPD)
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void exclusaoDeContaApagaTudoDoCuidadorEAnonimizaSomenteAsDosesRegistradas() {
        CuidadorEntity cuidador = cuidador();
        IdosoEntity idoso = idoso(cuidador);
        MedicamentoEntity ativo = medicamento(idoso, null);
        MedicamentoEntity removido = medicamento(idoso, AGORA.minusDays(3));
        TratamentoEntity tratamentoAtivo = tratamento(ativo, null);
        TratamentoEntity tratamentoRemovido = tratamento(removido, AGORA.minusDays(3));
        CodigoVinculoEntity codigo = codigoVinculo(cuidador, idoso);
        DoseEntity pendentePassada = dose(tratamentoAtivo, AGORA.minusDays(1), StatusDose.PENDENTE);
        DoseEntity pendenteFutura = dose(tratamentoAtivo, AGORA.plusDays(1), StatusDose.PENDENTE);
        DoseEntity tomada = dose(tratamentoAtivo, AGORA.minusHours(6), StatusDose.TOMADA);
        DoseEntity naoTomada = dose(tratamentoRemovido, AGORA.minusDays(5), StatusDose.NAO_TOMADA);
        DoseEntity comAtraso = dose(tratamentoRemovido, AGORA.minusDays(4), StatusDose.CONFIRMADA_COM_ATRASO);

        // Dados de outro cuidador não podem ser tocados
        CuidadorEntity outro = cuidador();
        IdosoEntity idosoDoOutro = idoso(outro);
        TratamentoEntity tratamentoDoOutro = tratamento(medicamento(idosoDoOutro, null), null);
        DoseEntity pendenteDoOutro = dose(tratamentoDoOutro, AGORA.plusDays(1), StatusDose.PENDENTE);

        excluirContaUseCase.executar(cuidador.getId());

        assertThat(contar("cuidador", cuidador.getId())).isZero();
        assertThat(contar("idoso", idoso.getId())).isZero();
        assertThat(contar("medicamento", ativo.getId(), removido.getId())).isZero();
        assertThat(contar("tratamento", tratamentoAtivo.getId(), tratamentoRemovido.getId())).isZero();
        assertThat(contar("codigo_vinculo", codigo.getId())).isZero();
        assertThat(contar("dose", pendentePassada.getId(), pendenteFutura.getId())).isZero();

        // Registradas: mantidas, mas sem vínculo com ninguém (ON DELETE SET NULL)
        assertThat(contar("dose", tomada.getId(), naoTomada.getId(), comAtraso.getId())).isEqualTo(3);
        assertThat(tratamentoIdDaDose(tomada.getId())).isNull();
        assertThat(tratamentoIdDaDose(naoTomada.getId())).isNull();
        assertThat(tratamentoIdDaDose(comAtraso.getId())).isNull();

        assertThat(contar("cuidador", outro.getId())).isEqualTo(1);
        assertThat(contar("tratamento", tratamentoDoOutro.getId())).isEqualTo(1);
        assertThat(tratamentoIdDaDose(pendenteDoOutro.getId())).isEqualTo(tratamentoDoOutro.getId());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Remoção lógica
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void remocaoLogicaEscondeMedicamentoETratamentosEPreservaHistoricoDeDoses() {
        CuidadorEntity cuidador = cuidador();
        IdosoEntity idoso = idoso(cuidador);
        MedicamentoEntity medicamento = medicamento(idoso, null);
        MedicamentoEntity outroMedicamento = medicamento(idoso, null);
        TratamentoEntity tratamento = tratamento(medicamento, null);
        DoseEntity pendentePassada = dose(tratamento, AGORA.minusHours(2), StatusDose.PENDENTE);
        DoseEntity pendenteFutura = dose(tratamento, AGORA.plusHours(2), StatusDose.PENDENTE);
        DoseEntity tomada = dose(tratamento, AGORA.minusDays(1), StatusDose.TOMADA);

        removerMedicamentoUseCase.executar(medicamento.getId(), cuidador.getId(), true);

        // Some de todas as consultas
        assertThat(medicamentoAdapter.findById(medicamento.getId())).isEmpty();
        assertThat(medicamentoAdapter.existsById(medicamento.getId())).isFalse();
        assertThat(medicamentoAdapter.findAllByIdosoId(idoso.getId()))
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
        assertThat(contar("dose", pendentePassada.getId(), tomada.getId())).isEqualTo(2);
    }

    @Test
    void findAllAtivosIgnoraTratamentosEncerradosERemovidos() {
        IdosoEntity idoso = idoso(cuidador());
        TratamentoEntity continuo = tratamento(medicamento(idoso, null), null);
        TratamentoEntity terminaHoje = tratamento(medicamento(idoso, null), null, HOJE);
        TratamentoEntity encerrado = tratamento(medicamento(idoso, null), null, HOJE.minusDays(1));
        TratamentoEntity removido = tratamento(medicamento(idoso, null), AGORA.minusDays(1));
        TratamentoEntity deMedicamentoRemovido = tratamento(medicamento(idoso, AGORA.minusDays(1)), null);

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
        TratamentoEntity tratamento = tratamento(medicamento(idoso(cuidador), null), null);
        DoseEntity pendentePassada = dose(tratamento, HOJE.minusDays(1).atTime(8, 0), StatusDose.PENDENTE);
        DoseEntity tomadaHoje = dose(tratamento, HOJE.atTime(8, 0), StatusDose.TOMADA);
        DoseEntity pendenteFutura = dose(tratamento, HOJE.plusDays(1).atTime(8, 0), StatusDose.PENDENTE);
        DoseEntity registradaFutura = dose(tratamento, HOJE.plusDays(2).atTime(8, 0), StatusDose.TOMADA);

        editarTratamentoUseCase.executar(tratamento.getId(), HOJE.plusDays(2), List.of(LocalTime.of(9, 0)),
                List.copyOf(EnumSet.allOf(DayOfWeek.class)), cuidador.getId());

        assertThat(contar("dose", pendenteFutura.getId())).isZero();
        assertThat(contar("dose", pendentePassada.getId(), tomadaHoje.getId(), registradaFutura.getId())).isEqualTo(3);
        // Hoje 09:00 já passou (agora = 14:30); geradas: amanhã e depois de amanhã às 09:00
        assertThat(horariosPendentesFuturos(tratamento.getId()))
                .containsExactly(HOJE.plusDays(1).atTime(9, 0), HOJE.plusDays(2).atTime(9, 0));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Unicidade e anonimização no banco
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void indiceUnicoImpedeDuasDosesNoMesmoHorarioDoMesmoTratamento() {
        TratamentoEntity tratamento = tratamento(medicamento(idoso(cuidador()), null), null);
        LocalDateTime horario = AGORA.plusDays(1);

        assertThatThrownBy(() -> doseAdapter.saveAll(List.of(
                Dose.pendente(tratamento.getId(), horario),
                Dose.pendente(tratamento.getId(), horario))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dosesAnonimizadasNoMesmoHorarioNaoConflitam() {
        LocalDateTime horario = AGORA.minusDays(1);
        String insert = "INSERT INTO dose (tratamento_id, horario_programado, status) VALUES (NULL, ?1, 'TOMADA')";

        em.getEntityManager().createNativeQuery(insert).setParameter(1, horario).executeUpdate();
        em.getEntityManager().createNativeQuery(insert).setParameter(1, horario).executeUpdate();

        Number total = (Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM dose WHERE tratamento_id IS NULL AND horario_programado = ?1")
                .setParameter(1, horario).getSingleResult();
        assertThat(total.longValue()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void apagarTratamentoAnonimizaAsDosesPorOnDeleteSetNull() {
        TratamentoEntity tratamento = tratamento(medicamento(idoso(cuidador()), null), null);
        DoseEntity tomada = dose(tratamento, AGORA.minusDays(1), StatusDose.TOMADA);
        em.flush();
        em.clear();

        em.getEntityManager().createNativeQuery("DELETE FROM tratamento WHERE id = ?1")
                .setParameter(1, tratamento.getId()).executeUpdate();

        assertThat(contar("dose", tomada.getId())).isEqualTo(1);
        assertThat(tratamentoIdDaDose(tomada.getId())).isNull();
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

    private IdosoEntity idoso(CuidadorEntity cuidador) {
        return em.persist(IdosoEntity.builder()
                .nome("Idoso " + UUID.randomUUID())
                .dataNascimento(LocalDate.of(1945, 3, 12))
                .cuidador(cuidador)
                .build());
    }

    private MedicamentoEntity medicamento(IdosoEntity idoso, LocalDateTime removidoEm) {
        return em.persist(MedicamentoEntity.builder()
                .nome("Losartana")
                .dosagem("50mg")
                .tipo("Comprimido")
                .idoso(idoso)
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
                .build());
    }

    private CodigoVinculoEntity codigoVinculo(CuidadorEntity cuidador, IdosoEntity idoso) {
        return em.persist(CodigoVinculoEntity.builder()
                .codigoUnico("123456")
                .cuidador(cuidador)
                .idoso(idoso)
                .expiraEm(AGORA.plusDays(1))
                .usado(false)
                .build());
    }

    private long contar(String tabela, Long... ids) {
        Number total = (Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM " + tabela + " WHERE id IN (?1)")
                .setParameter(1, List.of(ids))
                .getSingleResult();
        return total.longValue();
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
}
