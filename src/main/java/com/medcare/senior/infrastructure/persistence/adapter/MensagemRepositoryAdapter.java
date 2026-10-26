package com.medcare.senior.infrastructure.persistence.adapter;

import com.medcare.senior.domain.historico.Pagina;
import com.medcare.senior.domain.mensagem.Mensagem;
import com.medcare.senior.domain.mensagem.MensagemRepositoryPort;
import com.medcare.senior.domain.mensagem.Remetente;
import com.medcare.senior.domain.mensagem.ResumoConversa;
import com.medcare.senior.infrastructure.persistence.entity.MensagemEntity;
import com.medcare.senior.infrastructure.persistence.projection.ConversaView;
import com.medcare.senior.infrastructure.persistence.repository.MensagemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MensagemRepositoryAdapter implements MensagemRepositoryPort {

    private final MensagemRepository repository;

    @Override
    @Transactional
    public Mensagem salvar(Mensagem mensagem) {
        return toDomain(repository.save(MensagemEntity.builder()
                .id(mensagem.id())
                .pacienteId(mensagem.pacienteId())
                .remetente(mensagem.remetente())
                .conteudo(mensagem.conteudo())
                .criadaEm(mensagem.criadaEm())
                .lidaEm(mensagem.lidaEm())
                .build()));
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<Mensagem> findConversa(Long pacienteId, int pagina, int tamanho) {
        Page<MensagemEntity> resultado = repository.findConversa(pacienteId, PageRequest.of(pagina, tamanho));
        return new Pagina<>(resultado.getContent().stream().map(MensagemRepositoryAdapter::toDomain).toList(), pagina,
                tamanho, resultado.getTotalElements());
    }

    @Override
    @Transactional
    public int marcarComoLidas(Long pacienteId, Remetente remetente, LocalDateTime agora) {
        return repository.marcarComoLidas(pacienteId, remetente, agora);
    }

    @Override
    @Transactional(readOnly = true)
    public long contarNaoLidas(Long pacienteId, Remetente remetente) {
        return repository.countByPacienteIdAndRemetenteAndLidaEmIsNull(pacienteId, remetente);
    }

    @Override
    @Transactional(readOnly = true)
    public long contarNaoLidasDoCuidador(Long cuidadorId) {
        return repository.countNaoLidasDoCuidador(cuidadorId, Remetente.PACIENTE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResumoConversa> findConversasDoCuidador(Long cuidadorId) {
        // O cuidador lê o que o paciente escreveu
        return repository.findConversasDoCuidador(cuidadorId, Remetente.PACIENTE.name()).stream()
                .map(MensagemRepositoryAdapter::toResumo)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ResumoConversa findConversaDoPaciente(Long pacienteId) {
        // O paciente lê o que o cuidador escreveu. O paciente já foi conferido pelo use case
        return repository.findConversaDoPaciente(pacienteId, Remetente.CUIDADOR.name())
                .map(MensagemRepositoryAdapter::toResumo)
                .orElseThrow(() -> new IllegalStateException("Paciente da conversa não encontrado"));
    }

    @Override
    @Transactional
    public void deleteAllByCuidadorId(Long cuidadorId) {
        repository.deleteAllByCuidadorId(cuidadorId);
    }

    private static Mensagem toDomain(MensagemEntity entity) {
        return new Mensagem(entity.getId(), entity.getPacienteId(), entity.getRemetente(), entity.getConteudo(),
                entity.getCriadaEm(), entity.getLidaEm());
    }

    private static ResumoConversa toResumo(ConversaView linha) {
        ResumoConversa.UltimaMensagem ultima = linha.getCriadaEm() == null ? null
                : new ResumoConversa.UltimaMensagem(linha.getConteudo(), Remetente.valueOf(linha.getRemetente()),
                linha.getCriadaEm());
        return new ResumoConversa(linha.getPacienteId(), linha.getPacienteNome(), ultima, linha.getNaoLidas());
    }
}
