package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.notificacao.EventoNotificacaoRepositoryPort;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.tratamento.TratamentoRepositoryPort;
import com.medcare.senior.domain.usuario.CodigoVinculoRepositoryPort;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcluirContaUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;

    @Mock
    private CuidadorRepositoryPort cuidadorRepository;

    @Mock
    private IdosoRepositoryPort idosoRepository;

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

    @InjectMocks
    private ExcluirContaUseCase useCase;

    @Test
    void deveApagarDadosNaOrdemDasChavesEstrangeiras() {
        when(cuidadorRepository.existsById(CUIDADOR_ID)).thenReturn(true);

        useCase.executar(CUIDADOR_ID);

        InOrder ordem = inOrder(codigoVinculoRepository, eventoNotificacaoRepository, doseRepository,
                tratamentoRepository, medicamentoRepository, idosoRepository, cuidadorRepository);
        ordem.verify(cuidadorRepository).existsById(CUIDADOR_ID);
        ordem.verify(codigoVinculoRepository).deleteAllByCuidadorId(CUIDADOR_ID);
        // Eventos de notificação antes das doses (são achados pela dose -> tratamento -> ... -> cuidador)
        ordem.verify(eventoNotificacaoRepository).deleteByCuidadorId(CUIDADOR_ID);
        // Só as pendentes; as registradas ficam e são anonimizadas ao apagar os tratamentos
        ordem.verify(doseRepository).deletePendentesByCuidadorId(CUIDADOR_ID);
        ordem.verify(tratamentoRepository).deleteAllByCuidadorId(CUIDADOR_ID);
        ordem.verify(medicamentoRepository).deleteAllByCuidadorId(CUIDADOR_ID);
        ordem.verify(idosoRepository).deleteAllByCuidadorId(CUIDADOR_ID);
        ordem.verify(cuidadorRepository).deleteById(CUIDADOR_ID);
        ordem.verifyNoMoreInteractions();
    }

    @Test
    void deveResponderSessaoInvalidaQuandoContaJaFoiExcluida() {
        when(cuidadorRepository.existsById(CUIDADOR_ID)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(CUIDADOR_ID))
                .isInstanceOf(SessaoInvalidaException.class)
                .hasMessage("Sessão inválida. Entre na sua conta de novo.");

        verifyNoInteractions(codigoVinculoRepository, eventoNotificacaoRepository, doseRepository,
                tratamentoRepository, medicamentoRepository, idosoRepository);
    }
}
