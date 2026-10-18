package com.medcare.senior.infrastructure.persistence.adapter;

import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.DoseDetalhada;
import com.medcare.senior.domain.dose.DoseParaAviso;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.infrastructure.persistence.mapper.DoseMapper;
import com.medcare.senior.infrastructure.persistence.projection.DoseDetalhadaProjection;
import com.medcare.senior.infrastructure.persistence.repository.DoseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DoseRepositoryAdapter implements DoseRepositoryPort {

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
                                                      LocalDateTime encerradasAte, int limite) {
        return doseRepository.findParaAviso(agora, Math.toIntExact(toleranciaPadrao.toMinutes()), encerradasAte, limite)
                .stream()
                .map(linha -> new DoseParaAviso(linha.getDoseId(), linha.getHorarioProgramado(), linha.getCuidadorId(),
                        linha.getIdosoId(), linha.getNomeIdoso()))
                .toList();
    }

    @Override
    public List<Dose> findPendentesEncerradas(LocalDateTime encerradasAte, int limite) {
        return doseRepository.findEncerradasByStatus(StatusDose.PENDENTE, encerradasAte, PageRequest.of(0, limite))
                .stream()
                .map(doseMapper::toDomain)
                .toList();
    }

    @Override
    public List<DoseDetalhada> findDetalhadasDoIdosoEntre(Long idosoId, LocalDateTime inicio, LocalDateTime fim) {
        return doseRepository.findDetalhadasDoIdosoEntre(idosoId, inicio, fim).stream()
                .map(DoseRepositoryAdapter::toDetalhada)
                .toList();
    }

    @Override
    public Optional<DoseDetalhada> findDetalhadaByIdAndIdosoId(Long doseId, Long idosoId) {
        return doseRepository.findDetalhadaByIdAndIdosoId(doseId, idosoId).map(DoseRepositoryAdapter::toDetalhada);
    }

    private static DoseDetalhada toDetalhada(DoseDetalhadaProjection linha) {
        Dose dose = Dose.builder()
                .id(linha.doseId())
                .tratamentoId(linha.tratamentoId())
                .horarioProgramado(linha.horarioProgramado())
                .horaConfirmacao(linha.horaConfirmacao())
                .status(linha.status())
                .build();
        return new DoseDetalhada(dose, linha.idosoId(), linha.idosoNome(), linha.medicamentoId(), linha.medicamentoNome(),
                linha.dosagem(), linha.tipo(), linha.toleranciaMinutosDoCuidador());
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
                doseRegistrada.getStatus(), doseRegistrada.getHoraConfirmacao());
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
