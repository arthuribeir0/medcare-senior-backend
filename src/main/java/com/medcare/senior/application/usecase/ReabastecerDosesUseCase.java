package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.GeradorDeDosesService;
import com.medcare.senior.domain.tratamento.Tratamento;
import com.medcare.senior.domain.tratamento.TratamentoRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Empurra a janela rolante de doses: para cada tratamento ativo, garante doses até hoje + 30 dias, sem duplicar.
 *
 * <p>NÃO é @Transactional de propósito: cada tratamento roda na transação do próprio GeradorDeDosesService.gerar,
 * para que um erro em um tratamento desfaça só as doses dele e não impeça os demais.
 */
@Service
public class ReabastecerDosesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReabastecerDosesUseCase.class);

    private final TratamentoRepositoryPort tratamentoRepository;
    private final GeradorDeDosesService geradorDeDoses;
    private final Clock clock;

    public ReabastecerDosesUseCase(TratamentoRepositoryPort tratamentoRepository,
                                   GeradorDeDosesService geradorDeDoses,
                                   Clock clock) {
        this.tratamentoRepository = tratamentoRepository;
        this.geradorDeDoses = geradorDeDoses;
        this.clock = clock;
    }

    public Resultado executar() {
        List<Tratamento> ativos = tratamentoRepository.findAllAtivos(LocalDate.now(clock));

        int dosesGeradas = 0;
        int falhas = 0;
        for (Tratamento tratamento : ativos) {
            try {
                dosesGeradas += geradorDeDoses.gerar(tratamento);
            } catch (RuntimeException e) {
                falhas++;
                log.error("Falha ao reabastecer doses do tratamento {}; seguindo para os demais", tratamento.getId(), e);
            }
        }

        log.info("Reabastecimento de doses: {} tratamento(s) ativo(s), {} dose(s) gerada(s), {} falha(s)",
                ativos.size(), dosesGeradas, falhas);
        return new Resultado(ativos.size(), dosesGeradas, falhas);
    }

    public record Resultado(int tratamentosAtivos, int dosesGeradas, int falhas) {
    }
}
