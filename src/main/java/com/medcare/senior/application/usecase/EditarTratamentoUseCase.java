package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.GeradorDeDosesService;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.tratamento.Tratamento;
import com.medcare.senior.domain.tratamento.TratamentoRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class EditarTratamentoUseCase {

    private final TratamentoRepositoryPort tratamentoRepository;
    private final DoseRepositoryPort doseRepository;
    private final GeradorDeDosesService geradorDeDoses;
    private final Clock clock;

    public EditarTratamentoUseCase(TratamentoRepositoryPort tratamentoRepository,
                                   DoseRepositoryPort doseRepository,
                                   GeradorDeDosesService geradorDeDoses,
                                   Clock clock) {
        this.tratamentoRepository = tratamentoRepository;
        this.doseRepository = doseRepository;
        this.geradorDeDoses = geradorDeDoses;
        this.clock = clock;
    }

    /**
     * Altera horários, dias da semana e data de término (a data de início não muda) e regenera as doses futuras.
     *
     * <p>Só as doses PENDENTES com horário depois de agora são apagadas e geradas de novo. Doses registradas
     * (RN-008) e doses já passadas nunca são tocadas. Tudo na mesma transação.
     *
     * @param dataFim    {@code null} para tratamento contínuo
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     */
    @Transactional
    public Tratamento executar(Long id, LocalDate dataFim, List<LocalTime> horarios, List<DayOfWeek> diasSemana,
                               Long cuidadorId) {
        // Tratamento de outro cuidador, ou removido, responde igual a tratamento inexistente
        Tratamento atual = tratamentoRepository.findByIdAndCuidadorId(id, cuidadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tratamento não encontrado"));

        Tratamento editado = Tratamento.builder()
                .id(atual.getId())
                .medicamentoId(atual.getMedicamentoId())
                .dataInicio(atual.getDataInicio())
                .dataFim(dataFim)
                .horarios(horarios)
                .diasSemana(diasSemana)
                .build();

        // RN-007, as mesmas regras do cadastro
        editado.validar();

        Tratamento salvo = tratamentoRepository.save(editado);
        doseRepository.deletePendentesFuturasByTratamentoId(salvo.getId(), LocalDateTime.now(clock));
        geradorDeDoses.gerar(salvo);
        return salvo;
    }
}
