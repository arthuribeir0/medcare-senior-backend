package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.DoseAvaliada;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.relatorio.RelatorioDiario;
import com.medcare.senior.domain.relatorio.TotaisDoDia;
import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import com.medcare.senior.domain.relatorio.CacheDeRelatorioDiarioPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Relatório diário de um idoso do cuidador (UC-008). As doses do dia vêm em uma única consulta (com medicamento e a
 * tolerância do cuidador), avaliadas no instante atual pela {@link PoliticaDeJanela}; os totais usam o status gravado.
 *
 * <p>O relatório montado fica em cache por alguns segundos ({@link CacheDeRelatorioDiarioPort}), por cuidador, idoso e
 * dia; quem altera algo do relatório (confirmação, encerramento, tolerância, medicamento ou tratamento) descarta a
 * entrada na hora. O dono do idoso é conferido em toda chamada, antes do cache.
 */
@Service
public class GerarRelatorioDiarioUseCase {

    private final IdosoRepositoryPort idosoRepository;
    private final DoseRepositoryPort doseRepository;
    private final PoliticaDeJanela politicaDeJanela;
    private final CacheDeRelatorioDiarioPort cache;
    private final Clock clock;

    public GerarRelatorioDiarioUseCase(IdosoRepositoryPort idosoRepository,
                                       DoseRepositoryPort doseRepository,
                                       PoliticaDeJanela politicaDeJanela,
                                       CacheDeRelatorioDiarioPort cache,
                                       Clock clock) {
        this.idosoRepository = idosoRepository;
        this.doseRepository = doseRepository;
        this.politicaDeJanela = politicaDeJanela;
        this.cache = cache;
        this.clock = clock;
    }

    /**
     * @param data {@code null} = hoje, no fuso da aplicação
     * @throws RecursoNaoEncontradoException se o idoso não existir ou for de outro cuidador
     */
    public RelatorioDiario executar(Long idosoId, Long cuidadorId, LocalDate data) {
        // Idoso de outro cuidador responde igual a idoso inexistente, para não revelar quais IDs existem
        Idoso idoso = idosoRepository.findByIdAndCuidadorId(idosoId, cuidadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Idoso não encontrado"));
        LocalDateTime agora = LocalDateTime.now(clock);
        LocalDate dia = data != null ? data : agora.toLocalDate();
        CacheDeRelatorioDiarioPort.Chave chave = new CacheDeRelatorioDiarioPort.Chave(cuidadorId, idosoId, dia);
        return cache.buscar(chave).orElseGet(() -> {
            RelatorioDiario relatorio = montar(idoso, dia, agora);
            cache.guardar(chave, relatorio);
            return relatorio;
        });
    }

    private RelatorioDiario montar(Idoso idoso, LocalDate dia, LocalDateTime agora) {
        Long idosoId = idoso.getId();
        List<DoseAvaliada> doses = doseRepository.findDetalhadasDoIdosoEntre(idosoId, dia.atStartOfDay(),
                        dia.plusDays(1).atStartOfDay())
                .stream()
                .map(dose -> politicaDeJanela.avaliar(dose, agora,
                        politicaDeJanela.toleranciaEfetiva(dose.toleranciaMinutosDoCuidador())))
                .toList();
        TotaisDoDia totais = TotaisDoDia.de(doses.stream().map(dose -> dose.detalhe().dose().getStatus()).toList());
        return new RelatorioDiario(idoso.getId(), idoso.getNome(), dia, totais, doses);
    }
}
