package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.mensagem.MensagemRepositoryPort;
import com.medcare.senior.domain.notificacao.EventoNotificacaoRepositoryPort;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.tratamento.TratamentoRepositoryPort;
import com.medcare.senior.domain.usuario.CodigoVinculoRepositoryPort;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * =========================================================================================================
 *  POLÍTICA DE EXCLUSÃO DE CONTA (LGPD, art. 18, VI) — ver também docs/lgpd.md
 * =========================================================================================================
 *  - Dados pessoais são REMOVIDOS fisicamente: o cuidador e todos os pacientes, medicamentos, tratamentos e
 *    códigos de vínculo ligados a ele, INCLUSIVE medicamentos e tratamentos já removidos logicamente.
 *  - Eventos de notificação (avisos de dose) de TODAS as doses do cuidador são apagados.
 *  - Mensagens das conversas com todos os pacientes do cuidador são apagadas.
 *  - Doses PENDENTES (nunca registradas) são apagadas.
 *  - Doses já registradas (CONFIRMADA_NO_HORARIO, NAO_TOMADA, CONFIRMADA_COM_ATRASO) NÃO são apagadas: são ANONIMIZADAS.
 *    Quando os tratamentos são apagados, a FK dose.tratamento_id (ON DELETE SET NULL) zera o vínculo, e a dose
 *    passa a não identificar ninguém (só horário programado, hora de confirmação e status).
 *  - Tudo acontece em uma única transação: ou a conta inteira some, ou nada muda.
 *  - Ordem (respeita as chaves estrangeiras):
 *    códigos de vínculo -> eventos de notificação -> doses pendentes -> tratamentos -> medicamentos -> mensagens ->
 *    pacientes -> cuidador.
 * =========================================================================================================
 */
@Service
@RequiredArgsConstructor
public class ExcluirContaUseCase {

    private final CuidadorRepositoryPort cuidadorRepository;
    private final PacienteRepositoryPort pacienteRepository;
    private final MedicamentoRepositoryPort medicamentoRepository;
    private final TratamentoRepositoryPort tratamentoRepository;
    private final DoseRepositoryPort doseRepository;
    private final CodigoVinculoRepositoryPort codigoVinculoRepository;
    private final EventoNotificacaoRepositoryPort eventoNotificacaoRepository;
    private final MensagemRepositoryPort mensagemRepository;

    /**
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     * @throws SessaoInvalidaException se a conta não existir (por exemplo, já excluída com o mesmo token)
     */
    @Transactional
    public void executar(Long cuidadorId) {
        if (!cuidadorRepository.existsById(cuidadorId)) {
            throw SessaoInvalidaException.doCuidador();
        }

        codigoVinculoRepository.deleteAllByCuidadorId(cuidadorId);
        // Antes das doses e dos tratamentos: os eventos são achados pela dose -> tratamento -> ... -> cuidador
        eventoNotificacaoRepository.deleteByCuidadorId(cuidadorId);
        doseRepository.deletePendentesByCuidadorId(cuidadorId);
        // As doses registradas restantes são anonimizadas pelo banco (ON DELETE SET NULL) neste passo
        tratamentoRepository.deleteAllByCuidadorId(cuidadorId);
        medicamentoRepository.deleteAllByCuidadorId(cuidadorId);
        // A FK mensagem.paciente_id também apagaria em cascata; a remoção explícita deixa a política visível aqui
        mensagemRepository.deleteAllByCuidadorId(cuidadorId);
        pacienteRepository.deleteAllByCuidadorId(cuidadorId);
        cuidadorRepository.deleteById(cuidadorId);
    }
}
