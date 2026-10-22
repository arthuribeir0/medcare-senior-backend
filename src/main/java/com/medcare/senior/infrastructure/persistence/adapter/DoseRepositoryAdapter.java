package com.medcare.senior.infrastructure.persistence.adapter;

import com.medcare.senior.domain.configuracao.Tolerancia;
import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.DoseDetalhada;
import com.medcare.senior.domain.dose.DoseEncerrada;
import com.medcare.senior.domain.dose.DoseParaAviso;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.historico.FiltroHistorico;
import com.medcare.senior.domain.historico.Pagina;
import com.medcare.senior.domain.relatorio.ContagemDeDoses;
import com.medcare.senior.infrastructure.persistence.mapper.DoseMapper;
import com.medcare.senior.infrastructure.persistence.projection.DoseDetalhadaProjection;
import com.medcare.senior.infrastructure.persistence.repository.DoseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class DoseRepositoryAdapter implements DoseRepositoryPort {

    private static final LocalDateTime SEM_INICIO = LocalDateTime.of(1900, 1, 1, 0, 0);

    private final DoseRepository doseRepository;
    private final DoseMapper doseMapper;

    @Override
    @Transactional
    public List<Dose> saveAll(List<Dose> doses) {
        return doseRepository.saveAll(doses.stream().map(doseMapper::toEntity).toList()).stream()
                .map(doseMapper::toDomain)
                .toList();
    }

    @Override
    public Set<LocalDateTime> findHorariosProgramadosByTratamentoIdDesde(Long tratamentoId, LocalDateTime desde) {
        return new HashSet<>(doseRepository.findHorariosProgramadosDesde(tratamentoId, desde));
    }

    @Override
    public List<DoseParaAviso> findPendentesParaAviso(LocalDateTime agora, Duration toleranciaPadrao,
                                                      Duration margemPosAviso, int limite) {
        // Nenhuma dose com horário antes disto ainda está aberta, qualquer que seja a tolerância do cuidador
        LocalDateTime horarioMinimo = agora.minus(margemPosAviso).minus(maiorTolerancia(toleranciaPadrao));
        return doseRepository.findParaAviso(agora, minutos(toleranciaPadrao), minutos(margemPosAviso), horarioMinimo,
                        limite)
                .stream()
                .map(linha -> new DoseParaAviso(linha.getDoseId(), linha.getHorarioProgramado(), linha.getCuidadorId(),
                        linha.getIdosoId(), linha.getNomeIdoso()))
                .toList();
    }

    @Override
    @Transactional
    public List<DoseEncerrada> encerrarVencidas(LocalDateTime agora, Duration toleranciaPadrao, Duration margemPosAviso,
                                                int limite) {
        // Nenhuma dose com horário depois disto já venceu, qualquer que seja a tolerância do cuidador
        LocalDateTime horarioMaximo = agora.minus(margemPosAviso).minus(menorTolerancia(toleranciaPadrao));
        return doseRepository.encerrarVencidas(agora, minutos(toleranciaPadrao), minutos(margemPosAviso), horarioMaximo,
                        limite)
                .stream()
                .map(linha -> new DoseEncerrada(linha.getDoseId(), linha.getIdosoId()))
                .toList();
    }

    private static int minutos(Duration duracao) {
        return Math.toIntExact(duracao.toMinutes());
    }

    // Tolerâncias possíveis: a de cada cuidador (5 a 120 min, garantido pelo CHECK da configuracao_alerta) ou a padrão
    private static Duration maiorTolerancia(Duration padrao) {
        Duration maxima = Duration.ofMinutes(Tolerancia.MAXIMO_MINUTOS);
        return padrao.compareTo(maxima) > 0 ? padrao : maxima;
    }

    private static Duration menorTolerancia(Duration padrao) {
        Duration minima = Duration.ofMinutes(Tolerancia.MINIMO_MINUTOS);
        return padrao.compareTo(minima) < 0 ? padrao : minima;
    }

    @Override
    public List<DoseDetalhada> findDetalhadasDoIdosoEntre(Long idosoId, LocalDateTime inicio, LocalDateTime fim) {
        return doseRepository.findDetalhadasDoIdosoEntre(idosoId, inicio, fim).stream()
                .map(DoseRepositoryAdapter::toDetalhada)
                .toList();
    }

    @Override
    public Pagina<DoseDetalhada> findHistorico(Long cuidadorId, FiltroHistorico filtro) {
        // Sem "de": desde o início (data anterior a qualquer dose do sistema). "ate" é inclusivo: até o dia seguinte
        LocalDateTime inicio = filtro.de() != null ? filtro.de().atStartOfDay() : SEM_INICIO;
        LocalDateTime fim = filtro.ate().plusDays(1).atStartOfDay();
        Page<DoseDetalhadaProjection> pagina = doseRepository.findHistorico(cuidadorId, filtro.idosoId(),
                filtro.medicamentoId(), filtro.status(), inicio, fim, PageRequest.of(filtro.pagina(), filtro.tamanho()));
        return new Pagina<>(pagina.getContent().stream().map(DoseRepositoryAdapter::toDetalhada).toList(),
                filtro.pagina(), filtro.tamanho(), pagina.getTotalElements());
    }

    @Override
    public List<ContagemDeDoses> contarPorMedicamentoEStatus(Long idosoId, LocalDateTime inicio, LocalDateTime fim) {
        return doseRepository.contarPorMedicamentoEStatus(idosoId, inicio, fim);
    }

    @Override
    @Transactional(readOnly = true)
    public void percorrerDoIdoso(Long idosoId, LocalDateTime inicio, LocalDateTime fim, int tamanhoDoLote,
                                 Consumer<List<DoseDetalhada>> lote) {
        // Uma transação só leitura para todo o percurso: os lotes enxergam o mesmo estado do banco
        int pagina = 0;
        List<DoseDetalhadaProjection> linhas;
        do {
            linhas = doseRepository.findDoIdosoParaRelatorio(idosoId, inicio, fim, PageRequest.of(pagina++, tamanhoDoLote));
            if (!linhas.isEmpty()) {
                lote.accept(linhas.stream().map(DoseRepositoryAdapter::toDetalhada).toList());
            }
        } while (linhas.size() == tamanhoDoLote);
    }

    @Override
    public Optional<DoseDetalhada> findDetalhadaByIdAndIdosoId(Long doseId, Long idosoId) {
        return doseRepository.findDetalhadaByIdAndIdosoId(doseId, idosoId).map(DoseRepositoryAdapter::toDetalhada);
    }

    @Override
    public Optional<DoseDetalhada> findDetalhadaByIdAndCuidadorId(Long doseId, Long cuidadorId) {
        return doseRepository.findDetalhadaByIdAndCuidadorId(doseId, cuidadorId).map(DoseRepositoryAdapter::toDetalhada);
    }

    private static DoseDetalhada toDetalhada(DoseDetalhadaProjection linha) {
        Dose dose = Dose.builder()
                .id(linha.doseId())
                .tratamentoId(linha.tratamentoId())
                .horarioProgramado(linha.horarioProgramado())
                .horaConfirmacao(linha.horaConfirmacao())
                .status(linha.status())
                .confirmadoPor(linha.confirmadoPor())
                .build();
        return new DoseDetalhada(dose, linha.idosoId(), linha.idosoNome(), linha.medicamentoId(), linha.medicamentoNome(),
                linha.dosagem(), linha.tipo(), linha.toleranciaMinutosDoCuidador(), linha.cor());
    }

    @Override
    public Optional<StatusDose> findStatusById(Long doseId) {
        return doseRepository.findStatusById(doseId);
    }

    @Override
    @Transactional
    public int registrarSePendente(Dose doseRegistrada) {
        if (doseRegistrada.getId() == null || !doseRegistrada.isRegistrada()) {
            throw new IllegalArgumentException("Só uma dose existente e já registrada pode substituir uma PENDENTE");
        }
        return doseRepository.atualizarStatusSeAtual(doseRegistrada.getId(), StatusDose.PENDENTE,
                doseRegistrada.getStatus(), doseRegistrada.getHoraConfirmacao(), doseRegistrada.getConfirmadoPor());
    }

    @Override
    @Transactional
    public void deletePendentesFuturasByTratamentoId(Long tratamentoId, LocalDateTime agora) {
        doseRepository.deleteFuturasByTratamentoIdAndStatus(tratamentoId, StatusDose.PENDENTE, agora);
    }

    @Override
    @Transactional
    public void deletePendentesFuturasByMedicamentoId(Long medicamentoId, LocalDateTime agora) {
        doseRepository.deleteFuturasByMedicamentoIdAndStatus(medicamentoId, StatusDose.PENDENTE, agora);
    }

    @Override
    @Transactional
    public void deletePendentesByCuidadorId(Long cuidadorId) {
        doseRepository.deleteByCuidadorIdAndStatus(cuidadorId, StatusDose.PENDENTE);
    }
}
