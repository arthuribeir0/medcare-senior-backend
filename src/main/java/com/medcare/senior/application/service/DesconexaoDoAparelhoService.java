package com.medcare.senior.application.service;

import com.medcare.senior.domain.usuario.CodigoVinculoRepositoryPort;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

/**
 * Desconecta o aparelho do paciente: os tokens já emitidos deixam de valer (o próximo pedido do app recebe 401
 * SESSAO_INVALIDA e ele volta à tela inicial), os códigos de vínculo ainda não usados são inutilizados e o token de push
 * do aparelho é apagado (o lembrete do servidor deixa de ir para ele). O vínculo com o cuidador, os remédios e o
 * histórico continuam: para voltar, o cuidador gera um código novo. Os lembretes agendados no aparelho são apagados
 * pelo próprio app ao receber o 401.
 *
 * <p>Marcador proporcional ao JWT sem estado: uma coluna "sessão válida desde" no paciente, conferida a cada
 * requisição do paciente (SessaoDoPacienteInterceptor), em vez de uma lista de tokens revogados. O "emitido em" do
 * JWT tem precisão de segundo, então o marcador vai para o início do segundo seguinte: um token emitido no mesmo
 * segundo, antes da desconexão, também deixa de valer.
 */
@Service
@RequiredArgsConstructor
public class DesconexaoDoAparelhoService {

    private final PacienteRepositoryPort pacienteRepository;
    private final CodigoVinculoRepositoryPort codigoVinculoRepository;
    private final Clock clock;

    @Transactional
    public void desconectar(Long pacienteId) {
        pacienteRepository.invalidarSessoes(pacienteId, clock.instant().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1));
        codigoVinculoRepository.inutilizarCodigosDoPaciente(pacienteId);
        pacienteRepository.removerTokenFcm(pacienteId);
    }
}
