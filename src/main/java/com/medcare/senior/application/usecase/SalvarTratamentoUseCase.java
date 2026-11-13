package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.GeradorDeDosesService;
import com.medcare.senior.domain.configuracao.ModoDeTeste;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.tratamento.Tratamento;
import com.medcare.senior.domain.tratamento.TratamentoRepositoryPort;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
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
public class SalvarTratamentoUseCase {

    private final TratamentoRepositoryPort tratamentoRepository;
    private final MedicamentoRepositoryPort medicamentoRepository;
    private final PacienteRepositoryPort pacienteRepository;
    private final GeradorDeDosesService geradorDeDoses;
    private final CacheDeRelatorioDiarioPort cacheDeRelatorio;
    private final Clock clock;
    private final ModoDeTeste modoDeTeste;

    public SalvarTratamentoUseCase(TratamentoRepositoryPort tratamentoRepository,
                                   MedicamentoRepositoryPort medicamentoRepository,
                                   PacienteRepositoryPort pacienteRepository,
                                   GeradorDeDosesService geradorDeDoses,
                                   CacheDeRelatorioDiarioPort cacheDeRelatorio,
                                   Clock clock) {
        this(tratamentoRepository, medicamentoRepository, pacienteRepository, geradorDeDoses, cacheDeRelatorio, clock,
                ModoDeTeste.DESLIGADO);
    }

    /** Sem a configuração do modo de teste (contextos parciais de teste), valem as regras normais. */
    @Autowired
    public SalvarTratamentoUseCase(TratamentoRepositoryPort tratamentoRepository,
                                   MedicamentoRepositoryPort medicamentoRepository,
                                   PacienteRepositoryPort pacienteRepository,
                                   GeradorDeDosesService geradorDeDoses,
                                   CacheDeRelatorioDiarioPort cacheDeRelatorio,
                                   Clock clock,
                                   ObjectProvider<ModoDeTeste> modoDeTeste) {
        this(tratamentoRepository, medicamentoRepository, pacienteRepository, geradorDeDoses, cacheDeRelatorio, clock,
                modoDeTeste.getIfAvailable(() -> ModoDeTeste.DESLIGADO));
    }

    public SalvarTratamentoUseCase(TratamentoRepositoryPort tratamentoRepository,
                                   MedicamentoRepositoryPort medicamentoRepository,
                                   PacienteRepositoryPort pacienteRepository,
                                   GeradorDeDosesService geradorDeDoses,
                                   CacheDeRelatorioDiarioPort cacheDeRelatorio,
                                   Clock clock,
                                   ModoDeTeste modoDeTeste) {
        this.tratamentoRepository = tratamentoRepository;
        this.medicamentoRepository = medicamentoRepository;
        this.pacienteRepository = pacienteRepository;
        this.geradorDeDoses = geradorDeDoses;
        this.cacheDeRelatorio = cacheDeRelatorio;
        this.clock = clock;
        this.modoDeTeste = modoDeTeste;
    }

    /**
     * Cria um tratamento para o medicamento e gera as doses da janela inicial, na mesma transação. Se hoje tem dose do
     * tratamento, nenhum horário pode cair de agora até daqui a menos de 5 minutos (no modo de teste, qualquer um).
     *
     * @param medicamentoId medicamento ao qual o tratamento pertence
     * @param dataFim       {@code null} para tratamento contínuo
     * @param cuidadorId    ID do cuidador autenticado, extraído do JWT
     * @return o tratamento e a primeira dose depois de agora
     * @throws com.medcare.senior.domain.tratamento.HorarioMuitoProximoException horário de hoje a menos de 5 minutos
     */
    @Transactional
    public TratamentoSalvo executar(Long medicamentoId, LocalDate dataInicio, LocalDate dataFim,
                               List<LocalTime> horarios, List<DayOfWeek> diasSemana, Long cuidadorId) {
        // Medicamento de outro cuidador, ou removido, responde igual a medicamento inexistente
        medicamentoRepository.findById(medicamentoId)
                .filter(medicamento -> pacienteRepository
                        .findByIdAndCuidadorId(medicamento.getPacienteId(), cuidadorId).isPresent())
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
        LocalDateTime agora = LocalDateTime.now(clock);
        tratamento.validarAntecedencia(agora, horarios, modoDeTeste.antecedenciaMinima());

        Tratamento salvo = tratamentoRepository.save(tratamento);
        geradorDeDoses.gerar(salvo);
        cacheDeRelatorio.descartarDoCuidador(cuidadorId);
        return new TratamentoSalvo(salvo, salvo.primeiraDoseDepoisDe(agora).orElse(null));
    }
}
