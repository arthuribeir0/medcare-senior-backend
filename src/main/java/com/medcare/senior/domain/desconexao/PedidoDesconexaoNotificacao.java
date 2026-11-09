package com.medcare.senior.domain.desconexao;

/**
 * Aviso ao cuidador de que um paciente pediu para sair da conta. LGPD: só IDs e o nome do paciente.
 */
public record PedidoDesconexaoNotificacao(Long pedidoId, Long pacienteId, Long cuidadorId, String nomePaciente) {
}
