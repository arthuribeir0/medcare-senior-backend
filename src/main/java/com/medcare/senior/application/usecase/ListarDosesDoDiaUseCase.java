package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.DoseAvaliada;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Doses de um dia do paciente, com janela, status de exibição e confirmabilidade calculados no servidor (Clock).
 * Só doses de tratamento e medicamento não removidos; doses anonimizadas não aparecem.
 */
@Service
public class ListarDosesDoDiaUseCase {

    private final DoseRepositoryPort doseRepository;
    private final PacienteRepositoryPort pacienteRepository;
    private final PoliticaDeJanela politicaDeJanela;
    private final Clock clock;

    public ListarDosesDoDiaUseCase(DoseRepositoryPort doseRepository,
                                   PacienteRepositoryPort pacienteRepository,
                                   PoliticaDeJanela politicaDeJanela,
                                   Clock clock) {
        this.doseRepository = doseRepository;
        this.pacienteRepository = pacienteRepository;
        this.politicaDeJanela = politicaDeJanela;
        this.clock = clock;
    }

    /**
     * Visão do próprio paciente (token PACIENTE).
     *
     * @param data {@code null} = hoje, no fuso da aplicação
     * @throws SessaoInvalidaException se o paciente do token não existir mais
     */
    public List<DoseAvaliada> executarComoPaciente(Long pacienteId, LocalDate data) {
        if (pacienteRepository.findById(pacienteId).isEmpty()) {
            throw new SessaoInvalidaException();
        }
        return listar(pacienteId, data);
    }

    /**
     * Visão do cuidador sobre um paciente dele.
     *
     * @param data {@code null} = hoje, no fuso da aplicação
     * @throws RecursoNaoEncontradoException se o paciente não existir ou for de outro cuidador
     */
    public List<DoseAvaliada> executarComoCuidador(Long pacienteId, Long cuidadorId, LocalDate data) {
        // Paciente de outro cuidador responde igual a paciente inexistente, para não revelar quais IDs existem
        if (pacienteRepository.findByIdAndCuidadorId(pacienteId, cuidadorId).isEmpty()) {
            throw new RecursoNaoEncontradoException("Paciente não encontrado");
        }
        return listar(pacienteId, data);
    }

    private List<DoseAvaliada> listar(Long pacienteId, LocalDate data) {
        LocalDateTime agora = LocalDateTime.now(clock);
        LocalDate dia = data != null ? data : agora.toLocalDate();
        return doseRepository.findDetalhadasDoPacienteEntre(pacienteId, dia.atStartOfDay(), dia.plusDays(1).atStartOfDay())
                .stream()
                // Tolerância do cuidador dono do paciente (ou a padrão), lida na mesma consulta
                .map(dose -> politicaDeJanela.avaliar(dose, agora,
                        politicaDeJanela.toleranciaEfetiva(dose.toleranciaMinutosDoCuidador())))
                .toList();
    }
}
