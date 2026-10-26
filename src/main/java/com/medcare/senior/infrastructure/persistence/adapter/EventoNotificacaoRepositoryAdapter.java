package com.medcare.senior.infrastructure.persistence.adapter;

import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.notificacao.EventoNotificacaoRepositoryPort;
import com.medcare.senior.domain.notificacao.EventoParaRetentativa;
import com.medcare.senior.domain.notificacao.MotivoFalhaNotificacao;
import com.medcare.senior.domain.notificacao.StatusEventoNotificacao;
import com.medcare.senior.infrastructure.persistence.repository.EventoNotificacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cada método roda na própria transação: o evento é criado, reivindicado e finalizado em passos gravados
 * separadamente, para que uma queda no meio deixe o evento num estado que a retentativa sabe retomar.
 */
@Component
@RequiredArgsConstructor
public class EventoNotificacaoRepositoryAdapter implements EventoNotificacaoRepositoryPort {

    private final EventoNotificacaoRepository repository;

    @Override
    @Transactional
    public Optional<Long> criarSeAusente(Long doseId) {
        return repository.inserirSeAusente(Objects.requireNonNull(doseId)).stream().findFirst();
    }

    @Override
    @Transactional
    public boolean reivindicar(Long eventoId, int tentativasEsperadas, LocalDateTime agora) {
        return repository.reivindicar(eventoId, tentativasEsperadas, agora, StatusEventoNotificacao.PENDENTE) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventoParaRetentativa> findParaRetentativa(LocalDateTime agora, int maxTentativas, Duration intervaloBase,
                                                           int limite) {
        return repository.findParaRetentativa(agora, maxTentativas, Math.toIntExact(intervaloBase.toMinutes()), limite)
                .stream()
                .map(linha -> new EventoParaRetentativa(linha.getEventoId(), linha.getTentativas(), linha.getDoseId(),
                        StatusDose.valueOf(linha.getStatusDose()), linha.getHorarioProgramado(),
                        Boolean.TRUE.equals(linha.getSemDono()), linha.getCuidadorId(), linha.getPacienteId(),
                        linha.getNomePaciente()))
                .toList();
    }

    @Override
    @Transactional
    public void marcarEnviado(Long eventoId) {
        repository.finalizar(eventoId, StatusEventoNotificacao.ENVIADO, null, StatusEventoNotificacao.PENDENTE);
    }

    @Override
    @Transactional
    public void marcarFalha(Long eventoId, MotivoFalhaNotificacao motivo) {
        repository.finalizar(eventoId, StatusEventoNotificacao.FALHA, Objects.requireNonNull(motivo),
                StatusEventoNotificacao.PENDENTE);
    }

    @Override
    @Transactional
    public void deleteByCuidadorId(Long cuidadorId) {
        repository.deleteByCuidadorId(cuidadorId);
    }
}
