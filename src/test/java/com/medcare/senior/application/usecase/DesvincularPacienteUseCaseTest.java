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
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesvincularPacienteUseCaseTest {

    private static final Long PACIENTE_ID = 7L;
    private static final Long CUIDADOR_ID = 1L;

    @Mock
    private PacienteRepositoryPort pacienteRepository;

    @Mock
    private MedicamentoRepositoryPort medicamentoRepository;

    @Mock
    private TratamentoRepositoryPort tratamentoRepository;

    @Mock
    private DoseRepositoryPort doseRepository;

    @Mock
    private CodigoVinculoRepositoryPort codigoVinculoRepository;

    @Mock
    private EventoNotificacaoRepositoryPort eventoNotificacaoRepository;

    @Mock
    private MensagemRepositoryPort mensagemRepository;

    @Mock
    private PedidoDesconexaoRepositoryPort pedidoDesconexaoRepository;

    @Mock
    private CacheDeRelatorioDiarioPort cacheDeRelatorio;

    @InjectMocks
    private DesvincularPacienteUseCase useCase;

    @Test
    void deveApagarDadosDoPacienteNaOrdemDasChavesEstrangeiras() {
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID))
                .thenReturn(Optional.of(Paciente.builder().id(PACIENTE_ID).build()));
        when(pacienteRepository.deleteByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID)).thenReturn(true);

        useCase.executar(PACIENTE_ID, CUIDADOR_ID);

        InOrder ordem = inOrder(pacienteRepository, codigoVinculoRepository, eventoNotificacaoRepository, doseRepository,
                tratamentoRepository, medicamentoRepository, mensagemRepository, pedidoDesconexaoRepository,
                cacheDeRelatorio);
        ordem.verify(pacienteRepository).findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID);
        ordem.verify(codigoVinculoRepository).deleteAllByPacienteId(PACIENTE_ID);
        // Eventos de notificação antes das doses (são achados pela dose -> tratamento -> medicamento -> paciente)
        ordem.verify(eventoNotificacaoRepository).deleteByPacienteId(PACIENTE_ID);
        // Só as pendentes; as registradas ficam e são anonimizadas ao apagar os tratamentos
        ordem.verify(doseRepository).deletePendentesByPacienteId(PACIENTE_ID);
        ordem.verify(tratamentoRepository).deleteAllByPacienteId(PACIENTE_ID);
        ordem.verify(medicamentoRepository).deleteAllByPacienteId(PACIENTE_ID);
        ordem.verify(mensagemRepository).deleteAllByPacienteId(PACIENTE_ID);
        ordem.verify(pedidoDesconexaoRepository).deleteAllByPacienteId(PACIENTE_ID);
        ordem.verify(pacienteRepository).deleteByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID);
        ordem.verify(cacheDeRelatorio).descartarDoPaciente(PACIENTE_ID);
        ordem.verifyNoMoreInteractions();
    }

    @Test
    void pacienteInexistenteOuDeOutroCuidadorNaoApagaNada() {
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(PACIENTE_ID, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Paciente não encontrado");

        verify(pacienteRepository, never()).deleteByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID);
        verifyNoInteractions(codigoVinculoRepository, eventoNotificacaoRepository, doseRepository, tratamentoRepository,
                medicamentoRepository, mensagemRepository, pedidoDesconexaoRepository, cacheDeRelatorio);
    }

    @Test
    void desvinculacaoSimultaneaQuePerdeACorridaResponde404() {
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID))
                .thenReturn(Optional.of(Paciente.builder().id(PACIENTE_ID).build()));
        when(pacienteRepository.deleteByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID)).thenReturn(false);

        // A exceção desfaz a transação inteira: nenhuma remoção anterior fica valendo
        assertThatThrownBy(() -> useCase.executar(PACIENTE_ID, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(cacheDeRelatorio);
    }
}
