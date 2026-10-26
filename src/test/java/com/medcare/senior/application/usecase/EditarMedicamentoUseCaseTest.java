package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.DadosObrigatoriosMedicamentoException;
import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import com.medcare.senior.domain.relatorio.CacheDeRelatorioDiarioPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EditarMedicamentoUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long PACIENTE_ID = 10L;
    private static final Long MEDICAMENTO_ID = 100L;

    @Mock
    private CacheDeRelatorioDiarioPort cacheDeRelatorio;

    @Mock
    private MedicamentoRepositoryPort medicamentoRepository;

    @Mock
    private PacienteRepositoryPort pacienteRepository;

    @InjectMocks
    private EditarMedicamentoUseCase useCase;

    private static Medicamento dadosValidos() {
        return Medicamento.builder()
                .nome("Losartana")
                .dosagem("100mg")
                .tipo("COMPRIMIDO")
                .pacienteId(PACIENTE_ID)
                .build();
    }

    private void medicamentoExistenteDoCuidador() {
        Medicamento atual = dadosValidos();
        atual.setId(MEDICAMENTO_ID);
        atual.setDosagem("50mg");
        when(medicamentoRepository.findById(MEDICAMENTO_ID)).thenReturn(Optional.of(atual));
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID))
                .thenReturn(Optional.of(Paciente.builder().id(PACIENTE_ID).cuidadorId(CUIDADOR_ID).build()));
    }

    @Test
    void deveEditarMedicamento() {
        medicamentoExistenteDoCuidador();
        when(medicamentoRepository.save(any(Medicamento.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Medicamento resultado = useCase.executar(MEDICAMENTO_ID, dadosValidos(), CUIDADOR_ID);

        assertThat(resultado.getId()).isEqualTo(MEDICAMENTO_ID);
        assertThat(resultado.getDosagem()).isEqualTo("100mg");
        verify(cacheDeRelatorio).descartarDoCuidador(CUIDADOR_ID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void deveRejeitarNomeAusente(String nome) {
        medicamentoExistenteDoCuidador();
        Medicamento dados = dadosValidos();
        dados.setNome(nome);

        assertRejeitado(dados);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void deveRejeitarDosagemAusente(String dosagem) {
        medicamentoExistenteDoCuidador();
        Medicamento dados = dadosValidos();
        dados.setDosagem(dosagem);

        assertRejeitado(dados);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void deveRejeitarTipoAusente(String tipo) {
        medicamentoExistenteDoCuidador();
        Medicamento dados = dadosValidos();
        dados.setTipo(tipo);

        assertRejeitado(dados);
    }

    @Test
    void deveRejeitarMedicamentoDeOutroCuidador() {
        Medicamento deOutro = dadosValidos();
        deOutro.setId(MEDICAMENTO_ID);
        when(medicamentoRepository.findById(MEDICAMENTO_ID)).thenReturn(Optional.of(deOutro));
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(MEDICAMENTO_ID, dadosValidos(), CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Medicamento não encontrado");

        verify(medicamentoRepository, never()).save(any());
    }

    @Test
    void deveRejeitarTransferenciaParaPacienteDeOutroCuidador() {
        medicamentoExistenteDoCuidador();
        Long pacienteDeOutroCuidador = 20L;
        when(pacienteRepository.findByIdAndCuidadorId(pacienteDeOutroCuidador, CUIDADOR_ID)).thenReturn(Optional.empty());
        Medicamento dados = dadosValidos();
        dados.setPacienteId(pacienteDeOutroCuidador);

        assertThatThrownBy(() -> useCase.executar(MEDICAMENTO_ID, dados, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Paciente não encontrado");

        verify(medicamentoRepository, never()).save(any());
    }

    private void assertRejeitado(Medicamento dados) {
        assertThatThrownBy(() -> useCase.executar(MEDICAMENTO_ID, dados, CUIDADOR_ID))
                .isInstanceOf(DadosObrigatoriosMedicamentoException.class);

        verify(medicamentoRepository, never()).save(any());
    }
}
