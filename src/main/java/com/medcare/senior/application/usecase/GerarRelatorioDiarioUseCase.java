package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.DoseAvaliada;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.relatorio.RelatorioDiario;
import com.medcare.senior.domain.relatorio.TotaisDoDia;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import com.medcare.senior.domain.relatorio.CacheDeRelatorioDiarioPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Relatório diário de um paciente do cuidador (UC-008). As doses do dia vêm em uma única consulta (com medicamento e a
 * tolerância do cuidador), avaliadas no instante atual pela {@link PoliticaDeJanela}; os totais usam o status gravado.
 *
 * <p>O relatório montado fica em cache por alguns segundos ({@link CacheDeRelatorioDiarioPort}), por cuidador, paciente e
 * dia; quem altera algo do relatório (confirmação, encerramento, tolerância, medicamento ou tratamento) descarta a
 * entrada na hora. O dono do paciente é conferido em toda chamada, antes do cache.
 */
@Service
public class GerarRelatorioDiarioUseCase {

    private final PacienteRepositoryPort pacienteRepository;
    private final DoseRepositoryPort doseRepository;
    private final PoliticaDeJanela politicaDeJanela;
    private final CacheDeRelatorioDiarioPort cache;
    private final Clock clock;

    public GerarRelatorioDiarioUseCase(PacienteRepositoryPort pacienteRepository,
                                       DoseRepositoryPort doseRepository,
                                       PoliticaDeJanela politicaDeJanela,
                                       CacheDeRelatorioDiarioPort cache,
                                       Clock clock) {
        this.pacienteRepository = pacienteRepository;
        this.doseRepository = doseRepository;
        this.politicaDeJanela = politicaDeJanela;
        this.cache = cache;
        this.clock = clock;
    }

    /**
     * @param data {@code null} = hoje, no fuso da aplicação
     * @throws RecursoNaoEncontradoException se o paciente não existir ou for de outro cuidador
     */
    public RelatorioDiario executar(Long pacienteId, Long cuidadorId, LocalDate data) {
        // Paciente de outro cuidador responde igual a paciente inexistente, para não revelar quais IDs existem
        Paciente paciente = pacienteRepository.findByIdAndCuidadorId(pacienteId, cuidadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado"));
        LocalDateTime agora = LocalDateTime.now(clock);
        LocalDate dia = data != null ? data : agora.toLocalDate();
        CacheDeRelatorioDiarioPort.Chave chave = new CacheDeRelatorioDiarioPort.Chave(cuidadorId, pacienteId, dia);
        return cache.buscar(chave).orElseGet(() -> {
            RelatorioDiario relatorio = montar(paciente, dia, agora);
            cache.guardar(chave, relatorio);
            return relatorio;
        });
    }

    private RelatorioDiario montar(Paciente paciente, LocalDate dia, LocalDateTime agora) {
        Long pacienteId = paciente.getId();
        List<DoseAvaliada> doses = doseRepository.findDetalhadasDoPacienteEntre(pacienteId, dia.atStartOfDay(),
                        dia.plusDays(1).atStartOfDay())
                .stream()
                .map(dose -> politicaDeJanela.avaliar(dose, agora,
                        politicaDeJanela.toleranciaEfetiva(dose.toleranciaMinutosDoCuidador())))
                .toList();
        TotaisDoDia totais = TotaisDoDia.de(doses.stream().map(dose -> dose.detalhe().dose().getStatus()).toList());
        return new RelatorioDiario(paciente.getId(), paciente.getNome(), dia, totais, doses);
    }
}
