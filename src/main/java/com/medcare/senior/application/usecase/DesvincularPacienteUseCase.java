package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.desconexao.PedidoDesconexaoRepositoryPort;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.mensagem.MensagemRepositoryPort;
import com.medcare.senior.domain.notificacao.EventoNotificacaoRepositoryPort;
import com.medcare.senior.domain.relatorio.CacheDeRelatorioDiarioPort;
import com.medcare.senior.domain.tratamento.TratamentoRepositoryPort;
import com.medcare.senior.domain.usuario.CodigoVinculoRepositoryPort;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * =========================================================================================================
 *  DESVINCULAÇÃO DE PACIENTE (LGPD, art. 18, VI) — mesma política da exclusão de conta, para UM paciente.
 *  Ver ExcluirContaUseCase e docs/lgpd.md.
 * =========================================================================================================
 *  - O cuidador remove um paciente da própria conta. O cuidador e os outros pacientes dele não mudam.
 *  - Dados pessoais do paciente são REMOVIDOS fisicamente: o paciente, os medicamentos e tratamentos dele (INCLUSIVE
 *    os já removidos logicamente), os códigos de vínculo, a conversa com o cuidador e os pedidos de desconexão.
 *  - Eventos de notificação (avisos de dose) de TODAS as doses do paciente são apagados.
 *  - Doses PENDENTES (nunca registradas) são apagadas.
 *  - Doses já registradas NÃO são apagadas: são ANONIMIZADAS pela FK dose.tratamento_id (ON DELETE SET NULL) quando
 *    os tratamentos são apagados.
 *  - O aparelho do paciente perde a sessão: o paciente deixa de existir e o SessaoDoPacienteInterceptor (o mesmo
 *    controle da desconexão do aparelho) responde 401 SESSAO_INVALIDA ao próximo pedido com o token antigo. O
 *    paciente não tem token de push no servidor (o push vai só para o cuidador): não há token a apagar.
 *  - O relatório diário do paciente em cache é descartado.
 *  - Tudo acontece em uma única transação: ou o paciente inteiro some, ou nada muda.
 *  - Ordem (respeita as chaves estrangeiras):
 *    códigos de vínculo -> eventos de notificação -> doses pendentes -> tratamentos -> medicamentos -> mensagens ->
 *    pedidos de desconexão -> paciente.
 * =========================================================================================================
 */
@Service
@RequiredArgsConstructor
public class DesvincularPacienteUseCase {

    private final PacienteRepositoryPort pacienteRepository;
    private final MedicamentoRepositoryPort medicamentoRepository;
    private final TratamentoRepositoryPort tratamentoRepository;
    private final DoseRepositoryPort doseRepository;
    private final CodigoVinculoRepositoryPort codigoVinculoRepository;
    private final EventoNotificacaoRepositoryPort eventoNotificacaoRepository;
    private final MensagemRepositoryPort mensagemRepository;
    private final PedidoDesconexaoRepositoryPort pedidoDesconexaoRepository;
    private final CacheDeRelatorioDiarioPort cacheDeRelatorio;

    /**
     * @param pacienteId paciente a desvincular
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     * @throws RecursoNaoEncontradoException se o paciente não existir (inclusive já desvinculado) ou for de outro
     *                                       cuidador
     */
    @Transactional
    public void executar(Long pacienteId, Long cuidadorId) {
        // Paciente de outro cuidador responde igual a paciente inexistente, para não revelar quais IDs existem
        pacienteRepository.findByIdAndCuidadorId(pacienteId, cuidadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado"));

        codigoVinculoRepository.deleteAllByPacienteId(pacienteId);
        // Antes das doses e dos tratamentos: os eventos são achados pela dose -> tratamento -> medicamento -> paciente
        eventoNotificacaoRepository.deleteByPacienteId(pacienteId);
        doseRepository.deletePendentesByPacienteId(pacienteId);
        // As doses registradas restantes são anonimizadas pelo banco (ON DELETE SET NULL) neste passo
        tratamentoRepository.deleteAllByPacienteId(pacienteId);
        medicamentoRepository.deleteAllByPacienteId(pacienteId);
        // As FKs de mensagem e pedido também apagariam em cascata; a remoção explícita deixa a política visível aqui
        mensagemRepository.deleteAllByPacienteId(pacienteId);
        pedidoDesconexaoRepository.deleteAllByPacienteId(pacienteId);
        if (!pacienteRepository.deleteByIdAndCuidadorId(pacienteId, cuidadorId)) {
            // Outra desvinculação do mesmo paciente terminou primeiro
            throw new RecursoNaoEncontradoException("Paciente não encontrado");
        }
        cacheDeRelatorio.descartarDoPaciente(pacienteId);
    }
}
