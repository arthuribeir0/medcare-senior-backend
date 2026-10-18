package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.DoseAvaliada;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Doses de um dia do idoso, com janela, status de exibição e confirmabilidade calculados no servidor (Clock).
 * Só doses de tratamento e medicamento não removidos; doses anonimizadas não aparecem.
 */
@Service
public class ListarDosesDoDiaUseCase {

    private final DoseRepositoryPort doseRepository;
    private final IdosoRepositoryPort idosoRepository;
    private final PoliticaDeJanela politicaDeJanela;
    private final Clock clock;

    public ListarDosesDoDiaUseCase(DoseRepositoryPort doseRepository,
                                   IdosoRepositoryPort idosoRepository,
                                   PoliticaDeJanela politicaDeJanela,
                                   Clock clock) {
        this.doseRepository = doseRepository;
        this.idosoRepository = idosoRepository;
        this.politicaDeJanela = politicaDeJanela;
        this.clock = clock;
    }

    /**
     * Visão do próprio idoso (token IDOSO).
     *
     * @param data {@code null} = hoje, no fuso da aplicação
     * @throws SessaoInvalidaException se o idoso do token não existir mais
     */
    public List<DoseAvaliada> executarComoIdoso(Long idosoId, LocalDate data) {
        if (idosoRepository.findById(idosoId).isEmpty()) {
            throw new SessaoInvalidaException();
        }
        return listar(idosoId, data);
    }

    /**
     * Visão do cuidador sobre um idoso dele.
     *
     * @param data {@code null} = hoje, no fuso da aplicação
     * @throws RecursoNaoEncontradoException se o idoso não existir ou for de outro cuidador
     */
    public List<DoseAvaliada> executarComoCuidador(Long idosoId, Long cuidadorId, LocalDate data) {
        // Idoso de outro cuidador responde igual a idoso inexistente, para não revelar quais IDs existem
        if (idosoRepository.findByIdAndCuidadorId(idosoId, cuidadorId).isEmpty()) {
            throw new RecursoNaoEncontradoException("Idoso não encontrado");
        }
        return listar(idosoId, data);
    }

    private List<DoseAvaliada> listar(Long idosoId, LocalDate data) {
        LocalDateTime agora = LocalDateTime.now(clock);
        LocalDate dia = data != null ? data : agora.toLocalDate();
        return doseRepository.findDetalhadasDoIdosoEntre(idosoId, dia.atStartOfDay(), dia.plusDays(1).atStartOfDay())
                .stream()
                // Tolerância global por enquanto (a do cuidador dono do idoso entra aqui)
                .map(dose -> politicaDeJanela.avaliar(dose, agora, politicaDeJanela.toleranciaPadrao()))
                .toList();
    }
}
