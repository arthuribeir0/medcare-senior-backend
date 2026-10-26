package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.exception.ParametroInvalidoException;
import com.medcare.senior.domain.exception.PeriodoSemRegistrosException;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.relatorio.ContagemDeDoses;
import com.medcare.senior.domain.relatorio.GeradorDePdfPort;
import com.medcare.senior.domain.relatorio.RelatorioDeAdesao;
import com.medcare.senior.domain.relatorio.TotaisDoDia;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exporta o histórico de adesão de um paciente do cuidador em PDF (UC-012, HU10).
 *
 * <ul>
 *   <li>RN-011: a data de início é obrigatória e não pode ser depois da de fim; sem data de fim, vale hoje (fuso da
 *       aplicação). Período de no máximo {@value #PERIODO_MAXIMO_MESES} meses (de "de" até "ate", inclusive: de
 *       10/01/2025 a 10/01/2026 é aceito; até 11/01/2026, não). As doses são lidas em lotes, sem carregar o período
 *       inteiro.</li>
 *   <li>RN-012: o arquivo traz o nome do paciente, o período, os medicamentos avaliados, cada dose (data, hora programada,
 *       status e hora da confirmação) e a taxa de adesão por medicamento e do período (mesma fórmula do relatório
 *       diário).</li>
 *   <li>UC-012, fluxo 4a: período sem nenhuma dose não gera arquivo ({@link PeriodoSemRegistrosException}).</li>
 * </ul>
 */
@Service
public class GerarRelatorioPdfUseCase {

    /** Doses lidas do banco por vez ao montar a tabela. */
    static final int TAMANHO_DO_LOTE = 500;

    /** Maior período aceito, em meses. */
    public static final int PERIODO_MAXIMO_MESES = 12;

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PacienteRepositoryPort pacienteRepository;
    private final DoseRepositoryPort doseRepository;
    private final GeradorDePdfPort geradorDePdf;
    private final Clock clock;

    public GerarRelatorioPdfUseCase(PacienteRepositoryPort pacienteRepository, DoseRepositoryPort doseRepository,
                                    GeradorDePdfPort geradorDePdf, Clock clock) {
        this.pacienteRepository = pacienteRepository;
        this.doseRepository = doseRepository;
        this.geradorDePdf = geradorDePdf;
        this.clock = clock;
    }

    /**
     * @throws ParametroInvalidoException    sem data de início, início depois do fim ou período maior que 12 meses
     * @throws RecursoNaoEncontradoException paciente inexistente ou de outro cuidador
     * @throws PeriodoSemRegistrosException  nenhuma dose do paciente no período
     */
    public Resultado executar(Long cuidadorId, Long pacienteId, LocalDate de, LocalDate ate) {
        LocalDateTime agora = LocalDateTime.now(clock);
        if (de == null) {
            throw new ParametroInvalidoException("de", "Informe a data de início do período");
        }
        LocalDate fim = ate != null ? ate : agora.toLocalDate();
        if (de.isAfter(fim)) {
            throw new ParametroInvalidoException("de", "A data de início não pode ser posterior à data de fim");
        }
        if (fim.isAfter(de.plusMonths(PERIODO_MAXIMO_MESES))) {
            throw new ParametroInvalidoException("ate", "O período do relatório pode ter no máximo 12 meses. "
                    + "Escolha uma data de fim até " + de.plusMonths(PERIODO_MAXIMO_MESES).format(DATA) + ".");
        }
        Paciente paciente = pacienteRepository.findByIdAndCuidadorId(pacienteId, cuidadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado"));

        LocalDateTime inicio = de.atStartOfDay();
        LocalDateTime fimExclusivo = fim.plusDays(1).atStartOfDay();
        List<ContagemDeDoses> contagens = doseRepository.contarPorMedicamentoEStatus(pacienteId, inicio, fimExclusivo);
        if (contagens.stream().mapToLong(ContagemDeDoses::quantidade).sum() == 0) {
            throw new PeriodoSemRegistrosException();
        }

        RelatorioDeAdesao relatorio = new RelatorioDeAdesao(paciente.getId(), paciente.getNome(), de, fim, agora,
                totais(contagens), porMedicamento(contagens));
        byte[] pdf = geradorDePdf.gerar(relatorio, lote -> doseRepository.percorrerDoPaciente(pacienteId, inicio, fimExclusivo,
                TAMANHO_DO_LOTE, lote));
        return new Resultado(relatorio, pdf);
    }

    private static TotaisDoDia totais(List<ContagemDeDoses> contagens) {
        Map<StatusDose, Long> porStatus = new EnumMap<>(StatusDose.class);
        contagens.forEach(c -> porStatus.merge(c.status(), c.quantidade(), Long::sum));
        return totais(porStatus);
    }

    private static List<RelatorioDeAdesao.AdesaoDoMedicamento> porMedicamento(List<ContagemDeDoses> contagens) {
        Map<Long, List<ContagemDeDoses>> porId = new LinkedHashMap<>();
        contagens.forEach(c -> porId.computeIfAbsent(c.medicamentoId(), id -> new java.util.ArrayList<>()).add(c));
        return porId.values().stream()
                .map(linhas -> {
                    Map<StatusDose, Long> porStatus = new EnumMap<>(StatusDose.class);
                    linhas.forEach(c -> porStatus.merge(c.status(), c.quantidade(), Long::sum));
                    ContagemDeDoses primeira = linhas.get(0);
                    return new RelatorioDeAdesao.AdesaoDoMedicamento(primeira.medicamentoId(), primeira.nome(),
                            primeira.dosagem(), totais(porStatus));
                })
                .sorted(Comparator.comparing((RelatorioDeAdesao.AdesaoDoMedicamento a) -> a.nome().toLowerCase())
                        .thenComparing(RelatorioDeAdesao.AdesaoDoMedicamento::medicamentoId))
                .toList();
    }

    private static TotaisDoDia totais(Map<StatusDose, Long> porStatus) {
        return TotaisDoDia.deContagens(Math.toIntExact(porStatus.getOrDefault(StatusDose.CONFIRMADA_NO_HORARIO, 0L)),
                Math.toIntExact(porStatus.getOrDefault(StatusDose.CONFIRMADA_COM_ATRASO, 0L)),
                Math.toIntExact(porStatus.getOrDefault(StatusDose.NAO_TOMADA, 0L)),
                Math.toIntExact(porStatus.getOrDefault(StatusDose.PENDENTE, 0L)));
    }

    /**
     * @param pdf o arquivo gerado
     */
    public record Resultado(RelatorioDeAdesao relatorio, byte[] pdf) {
    }
}
