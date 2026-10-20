package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.Confirmabilidade;
import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.DoseAvaliada;
import com.medcare.senior.domain.dose.DoseDetalhada;
import com.medcare.senior.domain.dose.DoseNaoConfirmavelException;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Confirmação de dose pelo idoso. A janela e o status (TOMADA até o fim da tolerância, depois CONFIRMADA_COM_ATRASO)
 * vêm da {@link PoliticaDeJanela} com o Clock da aplicação.
 *
 * <p>NÃO é @Transactional de propósito: a gravação é um único UPDATE atômico condicionado a status = 'PENDENTE'
 * ({@link DoseRepositoryPort#registrarSePendente}). Se outra transação registrou a dose entre a leitura e o UPDATE
 * (outra confirmação, ou o job marcando NAO_TOMADA), nenhuma linha muda e a resposta é "já registrada" (RN-008).
 */
@Service
public class ConfirmarDoseUseCase {

    private final DoseRepositoryPort doseRepository;
    private final IdosoRepositoryPort idosoRepository;
    private final PoliticaDeJanela politicaDeJanela;
    private final Clock clock;

    public ConfirmarDoseUseCase(DoseRepositoryPort doseRepository,
                                IdosoRepositoryPort idosoRepository,
                                PoliticaDeJanela politicaDeJanela,
                                Clock clock) {
        this.doseRepository = doseRepository;
        this.idosoRepository = idosoRepository;
        this.politicaDeJanela = politicaDeJanela;
        this.clock = clock;
    }

    /**
     * @param doseId  dose a confirmar
     * @param idosoId ID do idoso autenticado, extraído do JWT
     * @return a dose confirmada, avaliada no mesmo instante da confirmação
     * @throws SessaoInvalidaException       se o idoso do token não existir mais
     * @throws RecursoNaoEncontradoException se a dose não existir, for de outro idoso ou de item removido
     * @throws DoseNaoConfirmavelException   se a dose já estiver registrada, ou fora da janela de confirmação
     */
    public DoseAvaliada executar(Long doseId, Long idosoId) {
        if (idosoRepository.findById(idosoId).isEmpty()) {
            throw new SessaoInvalidaException();
        }

        // Dose de outro idoso responde igual a dose inexistente, para não revelar quais IDs existem
        DoseDetalhada detalhe = doseRepository.findDetalhadaByIdAndIdosoId(doseId, idosoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Dose não encontrada"));
        Dose dose = detalhe.dose();

        LocalDateTime agora = LocalDateTime.now(clock);
        Confirmabilidade confirmabilidade = politicaDeJanela.confirmabilidade(dose.getStatus(),
                dose.getHorarioProgramado(), agora);
        if (confirmabilidade != Confirmabilidade.OK) {
            throw new DoseNaoConfirmavelException(confirmabilidade);
        }

        // Tolerância do cuidador dono do idoso (ou a padrão), lida junto com a dose
        Duration tolerancia = politicaDeJanela.toleranciaEfetiva(detalhe.toleranciaMinutosDoCuidador());
        Dose confirmada = dose.confirmar(politicaDeJanela.statusDaConfirmacao(dose.getHorarioProgramado(), agora, tolerancia),
                agora);
        if (doseRepository.registrarSePendente(confirmada) == 0) {
            // Perdeu a corrida: a dose foi registrada entre a leitura e o UPDATE
            throw new DoseNaoConfirmavelException(Confirmabilidade.JA_REGISTRADA);
        }

        return politicaDeJanela.avaliar(new DoseDetalhada(confirmada, detalhe.idosoId(), detalhe.idosoNome(),
                detalhe.medicamentoId(), detalhe.medicamentoNome(), detalhe.dosagem(), detalhe.tipo(),
                detalhe.toleranciaMinutosDoCuidador()), agora, tolerancia);
    }
}
