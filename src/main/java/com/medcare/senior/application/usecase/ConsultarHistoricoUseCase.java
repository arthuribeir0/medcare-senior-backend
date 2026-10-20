package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.DoseAvaliada;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.exception.ParametroInvalidoException;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.historico.FiltroHistorico;
import com.medcare.senior.domain.historico.Pagina;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Histórico de doses dos idosos do cuidador (UC-011), filtrável e paginado, sempre restrito aos idosos do cuidador do
 * token. Período pela RN-011: início não pode passar do fim; sem fim, vale hoje (fuso da aplicação). As doses vêm
 * avaliadas no instante atual (janela, status de exibição e confirmabilidade), como na listagem do dia.
 */
@Service
public class ConsultarHistoricoUseCase {

    private final DoseRepositoryPort doseRepository;
    private final IdosoRepositoryPort idosoRepository;
    private final PoliticaDeJanela politicaDeJanela;
    private final Clock clock;

    public ConsultarHistoricoUseCase(DoseRepositoryPort doseRepository,
                                     IdosoRepositoryPort idosoRepository,
                                     PoliticaDeJanela politicaDeJanela,
                                     Clock clock) {
        this.doseRepository = doseRepository;
        this.idosoRepository = idosoRepository;
        this.politicaDeJanela = politicaDeJanela;
        this.clock = clock;
    }

    /**
     * @throws RecursoNaoEncontradoException se {@code idosoId} for informado e não for um idoso do cuidador
     * @throws ParametroInvalidoException    se o período ou a paginação forem inválidos
     */
    public Pagina<DoseAvaliada> executar(Long cuidadorId, Long idosoId, Long medicamentoId, LocalDate de, LocalDate ate,
                                         StatusDose status, Integer pagina, Integer tamanho) {
        LocalDateTime agora = LocalDateTime.now(clock);
        FiltroHistorico filtro = FiltroHistorico.de(idosoId, medicamentoId, de, ate, status, pagina, tamanho,
                agora.toLocalDate());
        // Idoso de outro cuidador responde igual a idoso inexistente, para não revelar quais IDs existem
        if (idosoId != null && idosoRepository.findByIdAndCuidadorId(idosoId, cuidadorId).isEmpty()) {
            throw new RecursoNaoEncontradoException("Idoso não encontrado");
        }
        return doseRepository.findHistorico(cuidadorId, filtro)
                .map(dose -> politicaDeJanela.avaliar(dose, agora,
                        politicaDeJanela.toleranciaEfetiva(dose.toleranciaMinutosDoCuidador())));
    }
}
