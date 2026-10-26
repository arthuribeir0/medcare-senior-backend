package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.Confirmabilidade;
import com.medcare.senior.domain.dose.ConfirmadoPor;
import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.DoseAvaliada;
import com.medcare.senior.domain.dose.DoseDetalhada;
import com.medcare.senior.domain.dose.DoseNaoConfirmavelException;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import com.medcare.senior.domain.relatorio.CacheDeRelatorioDiarioPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Confirmação de dose pelo paciente (a própria dose) ou pelo cuidador (dose de um paciente dele). As mesmas regras valem
 * para os dois: a janela e o status (CONFIRMADA_NO_HORARIO até o fim da tolerância, depois CONFIRMADA_COM_ATRASO, até
 * horário + tolerância + margem pós-aviso) vêm da {@link PoliticaDeJanela} com o Clock da aplicação, e a dose grava
 * quem confirmou.
 *
 * <p>NÃO é @Transactional de propósito: a gravação é um único UPDATE atômico condicionado a status = 'PENDENTE'
 * ({@link DoseRepositoryPort#registrarSePendente}). Se outra transação registrou a dose entre a leitura e o UPDATE
 * (outra confirmação, do paciente ou do cuidador, ou o job marcando NAO_TOMADA), nenhuma linha muda e a resposta é "já
 * registrada" (RN-008).
 */
@Service
public class ConfirmarDoseUseCase {

    private final DoseRepositoryPort doseRepository;
    private final PacienteRepositoryPort pacienteRepository;
    private final PoliticaDeJanela politicaDeJanela;
    private final CacheDeRelatorioDiarioPort cacheDeRelatorio;
    private final Clock clock;

    public ConfirmarDoseUseCase(DoseRepositoryPort doseRepository,
                                PacienteRepositoryPort pacienteRepository,
                                PoliticaDeJanela politicaDeJanela,
                                CacheDeRelatorioDiarioPort cacheDeRelatorio,
                                Clock clock) {
        this.doseRepository = doseRepository;
        this.pacienteRepository = pacienteRepository;
        this.politicaDeJanela = politicaDeJanela;
        this.cacheDeRelatorio = cacheDeRelatorio;
        this.clock = clock;
    }

    /**
     * Confirmação pelo próprio paciente.
     *
     * @param doseId  dose a confirmar
     * @param pacienteId ID do paciente autenticado, extraído do JWT
     * @return a dose confirmada, avaliada no mesmo instante da confirmação
     * @throws SessaoInvalidaException       se o paciente do token não existir mais
     * @throws RecursoNaoEncontradoException se a dose não existir, for de outro paciente ou de item removido
     * @throws DoseNaoConfirmavelException   se a dose já estiver registrada, ou fora da janela de confirmação
     */
    public DoseAvaliada executar(Long doseId, Long pacienteId) {
        if (pacienteRepository.findById(pacienteId).isEmpty()) {
            throw new SessaoInvalidaException();
        }
        // Dose de outro paciente responde igual a dose inexistente, para não revelar quais IDs existem
        DoseDetalhada detalhe = doseRepository.findDetalhadaByIdAndPacienteId(doseId, pacienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Dose não encontrada"));
        return confirmar(detalhe, ConfirmadoPor.PACIENTE);
    }

    /**
     * Confirmação pelo cuidador, na tela da dose de um paciente dele. A conta do cuidador já foi conferida antes do
     * controller (token de conta excluída responde 401).
     *
     * @param doseId     dose a confirmar
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     * @throws RecursoNaoEncontradoException se a dose não existir, for de paciente de outro cuidador ou de item removido
     * @throws DoseNaoConfirmavelException   se a dose já estiver registrada, ou fora da janela de confirmação
     */
    public DoseAvaliada executarComoCuidador(Long doseId, Long cuidadorId) {
        // Dose de paciente de outro cuidador responde igual a dose inexistente (mesmo 404, mesma mensagem)
        DoseDetalhada detalhe = doseRepository.findDetalhadaByIdAndCuidadorId(doseId, cuidadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Dose não encontrada"));
        return confirmar(detalhe, ConfirmadoPor.CUIDADOR);
    }

    private DoseAvaliada confirmar(DoseDetalhada detalhe, ConfirmadoPor quem) {
        Dose dose = detalhe.dose();
        LocalDateTime agora = LocalDateTime.now(clock);
        // Tolerância do cuidador dono do paciente (ou a padrão), lida junto com a dose: define o encerramento e se a
        // confirmação é no horário ou com atraso
        Duration tolerancia = politicaDeJanela.toleranciaEfetiva(detalhe.toleranciaMinutosDoCuidador());
        Confirmabilidade confirmabilidade = politicaDeJanela.confirmabilidade(dose.getStatus(),
                dose.getHorarioProgramado(), agora, tolerancia);
        if (confirmabilidade != Confirmabilidade.OK) {
            throw new DoseNaoConfirmavelException(confirmabilidade);
        }

        Dose confirmada = dose.confirmar(politicaDeJanela.statusDaConfirmacao(dose.getHorarioProgramado(), agora, tolerancia),
                agora, quem);
        if (doseRepository.registrarSePendente(confirmada) == 0) {
            // Perdeu a corrida: a dose foi registrada entre a leitura e o UPDATE
            throw new DoseNaoConfirmavelException(Confirmabilidade.JA_REGISTRADA);
        }
        // Já gravada (o UPDATE tem a própria transação): o relatório diário do paciente não pode mostrar a dose pendente
        cacheDeRelatorio.descartarDoPaciente(detalhe.pacienteId());

        return politicaDeJanela.avaliar(detalhe.comDose(confirmada), agora, tolerancia);
    }
}
