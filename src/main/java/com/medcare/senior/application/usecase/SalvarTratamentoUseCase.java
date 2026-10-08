package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.GeradorDeDosesService;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.tratamento.Tratamento;
import com.medcare.senior.domain.tratamento.TratamentoRepositoryPort;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalvarTratamentoUseCase {

    private final TratamentoRepositoryPort tratamentoRepository;
    private final MedicamentoRepositoryPort medicamentoRepository;
    private final IdosoRepositoryPort idosoRepository;
    private final GeradorDeDosesService geradorDeDoses;

    /**
     * Cria um tratamento para o medicamento e gera as doses da janela inicial, na mesma transação.
     *
     * @param medicamentoId medicamento ao qual o tratamento pertence
     * @param dataFim       {@code null} para tratamento contínuo
     * @param cuidadorId    ID do cuidador autenticado, extraído do JWT
     */
    @Transactional
    public Tratamento executar(Long medicamentoId, LocalDate dataInicio, LocalDate dataFim,
                               List<LocalTime> horarios, List<DayOfWeek> diasSemana, Long cuidadorId) {
        // Medicamento de outro cuidador, ou removido, responde igual a medicamento inexistente
        medicamentoRepository.findById(medicamentoId)
                .filter(medicamento -> idosoRepository
                        .findByIdAndCuidadorId(medicamento.getIdosoId(), cuidadorId).isPresent())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Medicamento não encontrado"));

        Tratamento tratamento = Tratamento.builder()
                .medicamentoId(medicamentoId)
                .dataInicio(dataInicio)
                .dataFim(dataFim)
                .horarios(horarios)
                .diasSemana(diasSemana)
                .build();

        // RN-007
        tratamento.validar();

        Tratamento salvo = tratamentoRepository.save(tratamento);
        geradorDeDoses.gerar(salvo);
        return salvo;
    }
}
