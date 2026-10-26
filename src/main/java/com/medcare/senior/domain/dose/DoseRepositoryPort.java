package com.medcare.senior.domain.dose;

import com.medcare.senior.domain.historico.FiltroHistorico;
import com.medcare.senior.domain.historico.Pagina;
import com.medcare.senior.domain.relatorio.ContagemDeDoses;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Doses registradas são imutáveis (RN-008): a única atualização possível é a transição a partir de PENDENTE
 * ({@link #registrarSePendente}), e as únicas remoções atingem SOMENTE doses PENDENTES.
 */
public interface DoseRepositoryPort {

    /**
     * Grava doses PENDENTES novas em lote. Horário que já tem dose no mesmo tratamento é ignorado (índice único), o que
     * deixa a geração idempotente mesmo quando duas gerações do mesmo tratamento correm ao mesmo tempo.
     */
    void inserirPendentes(List<Dose> doses);

    /**
     * Horários que já têm dose (de qualquer status) para o tratamento, a partir de {@code desde} (inclusive).
     * Usado para a geração ser idempotente.
     */
    Set<LocalDateTime> findHorariosProgramadosByTratamentoIdDesde(Long tratamentoId, LocalDateTime desde);

    /**
     * Até {@code limite} doses PENDENTES que devem ser avisadas em {@code agora}: horário + tolerância do cuidador dono
     * do paciente (ou {@code toleranciaPadrao}, se ele não tem uma própria) <= agora, ainda não encerradas (horário +
     * tolerância + {@code margemPosAviso} > agora) e sem evento de notificação. Das mais antigas para as mais novas.
     * Ignora doses anonimizadas e de tratamento ou medicamento removido logicamente.
     *
     * @param toleranciaPadrao tolerância de quem não configurou a sua (app.alertas.tolerancia-minutos)
     * @param margemPosAviso   app.alertas.margem-pos-aviso-minutos
     */
    List<DoseParaAviso> findPendentesParaAviso(LocalDateTime agora, Duration toleranciaPadrao, Duration margemPosAviso,
                                               int limite);

    /**
     * Encerra como NAO_TOMADA até {@code limite} doses PENDENTES vencidas em {@code agora} (horário + tolerância do
     * cuidador, ou a padrão, + {@code margemPosAviso} <= agora), das mais antigas para as mais novas, em um único
     * UPDATE atômico condicionado a status = 'PENDENTE': uma confirmação gravada antes vence e a dose fica de fora.
     * Ignora doses anonimizadas e de tratamento ou medicamento removido logicamente.
     *
     * @return as doses que este comando encerrou
     */
    List<DoseEncerrada> encerrarVencidas(LocalDateTime agora, Duration toleranciaPadrao, Duration margemPosAviso,
                                         int limite);

    /**
     * Doses do paciente com horário programado em [{@code inicio}, {@code fim}), com paciente, medicamento e a tolerância
     * própria do cuidador (nula = padrão), em uma única consulta, ordenadas por horário. Ignora doses anonimizadas (sem tratamento) e de tratamento ou medicamento
     * removido logicamente.
     */
    List<DoseDetalhada> findDetalhadasDoPacienteEntre(Long pacienteId, LocalDateTime inicio, LocalDateTime fim);

    /**
     * Página do histórico de doses dos pacientes do cuidador (UC-011), da mais recente para a mais antiga, em uma consulta
     * com projeção (mais a contagem). Inclui doses de tratamento ou medicamento removidos logicamente (o histórico é
     * preservado, RN-008); doses anonimizadas não têm dono e ficam de fora.
     */
    Pagina<DoseDetalhada> findHistorico(Long cuidadorId, FiltroHistorico filtro);

    /**
     * Doses do paciente no intervalo [inicio, fim), agrupadas por medicamento e status gravado (relatório de adesão).
     * Como o histórico, inclui medicamentos e tratamentos removidos (RN-008); a dose anonimizada não tem dono.
     */
    List<ContagemDeDoses> contarPorMedicamentoEStatus(Long pacienteId, LocalDateTime inicio, LocalDateTime fim);

    /**
     * Entrega as doses do paciente no intervalo [inicio, fim), da mais antiga para a mais nova, em lotes de até
     * {@code tamanhoDoLote} (nunca todas na memória). Mesmos filtros de {@link #contarPorMedicamentoEStatus}.
     */
    void percorrerDoPaciente(Long pacienteId, LocalDateTime inicio, LocalDateTime fim, int tamanhoDoLote,
                          Consumer<List<DoseDetalhada>> lote);

    /**
     * A dose, SOMENTE se pertencer ao paciente (dose -> tratamento -> medicamento -> paciente) e o tratamento e o
     * medicamento não estiverem removidos. Dose de outro paciente, anonimizada ou de item removido volta vazia.
     */
    Optional<DoseDetalhada> findDetalhadaByIdAndPacienteId(Long doseId, Long pacienteId);

    /**
     * A dose, SOMENTE se o paciente dela for do cuidador (dose -> tratamento -> medicamento -> paciente -> cuidador) e o
     * tratamento e o medicamento não estiverem removidos. Dose de paciente de outro cuidador, anonimizada ou de item
     * removido volta vazia.
     */
    Optional<DoseDetalhada> findDetalhadaByIdAndCuidadorId(Long doseId, Long cuidadorId);

    /**
     * Grava a versão registrada da dose (status, hora de confirmação e quem confirmou) SOMENTE se no banco ela ainda estiver
     * PENDENTE, em um único UPDATE atômico ({@code ... WHERE id = ? AND status = 'PENDENTE'}). Se outra transação
     * registrou a dose antes (ex.: confirmação do paciente ou do cuidador), nada muda.
     *
     * @param doseRegistrada dose já em um status registrado, criada por uma transição de {@link Dose}
     * @return quantas linhas mudaram: 1 se a transição aconteceu, 0 se a dose não existe ou não estava PENDENTE
     * @throws IllegalArgumentException se {@code doseRegistrada} ainda estiver PENDENTE ou não tiver id
     */
    int registrarSePendente(Dose doseRegistrada);

    /**
     * Status atual da dose, lido do banco (sem cache), ou vazio se ela não existir.
     */
    Optional<StatusDose> findStatusById(Long doseId);

    /**
     * Apaga as doses PENDENTES do tratamento com horário programado depois de {@code agora}.
     * Doses registradas e doses já passadas não são tocadas.
     */
    void deletePendentesFuturasByTratamentoId(Long tratamentoId, LocalDateTime agora);

    /**
     * Apaga as doses PENDENTES futuras de todos os tratamentos do medicamento (remoção lógica do medicamento).
     */
    void deletePendentesFuturasByMedicamentoId(Long medicamentoId, LocalDateTime agora);

    /**
     * Apaga TODAS as doses PENDENTES (passadas e futuras) dos tratamentos do cuidador (exclusão de conta, LGPD).
     * As registradas ficam e são anonimizadas pelo banco (ON DELETE SET NULL) quando os tratamentos são apagados.
     */
    void deletePendentesByCuidadorId(Long cuidadorId);
}
