package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.GeradorDeDosesService;
import com.medcare.senior.domain.configuracao.ModoDeTeste;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.tratamento.Tratamento;
import com.medcare.senior.domain.tratamento.TratamentoRepositoryPort;
import com.medcare.senior.domain.relatorio.CacheDeRelatorioDiarioPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final CacheDeRelatorioDiarioPort cacheDeRelatorio;
    private final Clock clock;
    private final ModoDeTeste modoDeTeste;

    public EditarTratamentoUseCase(TratamentoRepositoryPort tratamentoRepository,
                                   DoseRepositoryPort doseRepository,
                                   GeradorDeDosesService geradorDeDoses,
                                   CacheDeRelatorioDiarioPort cacheDeRelatorio,
                                   Clock clock) {
        this(tratamentoRepository, doseRepository, geradorDeDoses, cacheDeRelatorio, clock, ModoDeTeste.DESLIGADO);
    }

    /** Sem a configuração do modo de teste (contextos parciais de teste), valem as regras normais. */
    @Autowired
    public EditarTratamentoUseCase(TratamentoRepositoryPort tratamentoRepository,
                                   DoseRepositoryPort doseRepository,
                                   GeradorDeDosesService geradorDeDoses,
                                   CacheDeRelatorioDiarioPort cacheDeRelatorio,
                                   Clock clock,
                                   ObjectProvider<ModoDeTeste> modoDeTeste) {
        this(tratamentoRepository, doseRepository, geradorDeDoses, cacheDeRelatorio, clock,
                modoDeTeste.getIfAvailable(() -> ModoDeTeste.DESLIGADO));
    }

    public EditarTratamentoUseCase(TratamentoRepositoryPort tratamentoRepository,
                                   DoseRepositoryPort doseRepository,
                                   GeradorDeDosesService geradorDeDoses,
                                   CacheDeRelatorioDiarioPort cacheDeRelatorio,
                                   Clock clock,
                                   ModoDeTeste modoDeTeste) {
        this.modoDeTeste = modoDeTeste;
        this.tratamentoRepository = tratamentoRepository;
        this.doseRepository = doseRepository;
        this.geradorDeDoses = geradorDeDoses;
        this.cacheDeRelatorio = cacheDeRelatorio;
        this.clock = clock;
    }

    /**
     * Altera horários, dias da semana e data de término (a data de início não muda) e regenera as doses futuras.
     *
     * <p>Só as doses PENDENTES com horário depois de agora são apagadas e geradas de novo. Doses registradas
     * e doses já passadas nunca são tocadas. Tudo na mesma transação. Horários novos ou alterados seguem a
     * mesma antecedência mínima do cadastro; os que já existiam não bloqueiam a edição.
     *
     * @param dataFim    {@code null} para tratamento contínuo
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     * @return o tratamento e a primeira dose depois de agora
     */
    @Transactional
    public TratamentoSalvo executar(Long id, LocalDate dataFim, List<LocalTime> horarios, List<DayOfWeek> diasSemana,
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
        LocalDateTime agora = LocalDateTime.now(clock);
        List<LocalTime> novos = horarios.stream().filter(h -> !atual.getHorarios().contains(h)).toList();
        editado.validarAntecedencia(agora, novos, modoDeTeste.antecedenciaMinima());

        Tratamento salvo = tratamentoRepository.save(editado);
        doseRepository.deletePendentesFuturasByTratamentoId(salvo.getId(), agora);
        geradorDeDoses.gerar(salvo);
        cacheDeRelatorio.descartarDoCuidador(cuidadorId);
        return new TratamentoSalvo(salvo, salvo.primeiraDoseDepoisDe(agora).orElse(null));
    }
}
