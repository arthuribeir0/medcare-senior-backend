package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.dose.Confirmabilidade;
import com.medcare.senior.domain.dose.ConfirmadoPor;
import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.DoseAvaliada;
import com.medcare.senior.domain.dose.DoseDetalhada;
import com.medcare.senior.domain.dose.JanelaDose;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.dose.StatusExibicao;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DoseResponseDTOTest {

    private static final LocalDateTime HORARIO = LocalDateTime.of(2026, 10, 8, 8, 0);
    private static final JanelaDose JANELA = new JanelaDose(HORARIO.minusHours(1), HORARIO.plusMinutes(30),
            HORARIO.plusMinutes(60));

    private static DoseAvaliada avaliada(DoseDetalhada detalhe) {
        return new DoseAvaliada(detalhe, JANELA, StatusExibicao.PENDENTE, Confirmabilidade.OK, 30);
    }

    @Test
    void mapeiaACorDoMedicamentoEOsDemaisCampos() {
        Dose dose = Dose.builder().id(5000L).tratamentoId(1000L).horarioProgramado(HORARIO).status(StatusDose.PENDENTE).build();
        DoseDetalhada detalhe = new DoseDetalhada(dose, 10L, "José", 100L, "Losartana", "50mg", "COMPRIMIDO", 30, "Branco");

        DoseResponseDTO dto = DoseResponseDTO.from(avaliada(detalhe));

        assertThat(dto.cor()).isEqualTo("Branco");
        assertThat(dto.id()).isEqualTo(5000L);
        assertThat(dto.medicamentoNome()).isEqualTo("Losartana");
        assertThat(dto.tipo()).isEqualTo("COMPRIMIDO");
        assertThat(dto.tratamentoId()).isEqualTo(1000L);
        assertThat(dto.toleranciaMinutos()).isEqualTo(30);
        assertThat(dto.janela().avisoEm()).isEqualTo(HORARIO.plusMinutes(30));
        // Tolerância 30 + margem 30: pode ser confirmada até 1 hora depois do horário
        assertThat(dto.limiteConfirmacao()).isEqualTo(HORARIO.plusMinutes(60)).isEqualTo(dto.janela().encerraEm());
    }

    @Test
    void medicamentoSemCorFicaNulo() {
        Dose dose = Dose.pendente(1000L, HORARIO);

        assertThat(DoseResponseDTO.from(avaliada(new DoseDetalhada(dose, 10L, "José", 100L, "Losartana", "50mg",
                "COMPRIMIDO"))).cor()).isNull();
        assertThat(DoseResponseDTO.from(avaliada(new DoseDetalhada(dose, 10L, "José", 100L, "Losartana", "50mg",
                "COMPRIMIDO", 45))).cor()).isNull();
    }

    @Test
    void comDoseTrocaSoADoseEMantemCorETolerancia() {
        Dose pendente = Dose.builder().id(1L).tratamentoId(1000L).horarioProgramado(HORARIO).status(StatusDose.PENDENTE).build();
        DoseDetalhada detalhe = new DoseDetalhada(pendente, 10L, "José", 100L, "Losartana", "50mg", "COMPRIMIDO", 60, "Rosa");
        Dose confirmada = pendente.confirmar(StatusDose.CONFIRMADA_NO_HORARIO, HORARIO.plusMinutes(5), ConfirmadoPor.IDOSO);

        DoseDetalhada nova = detalhe.comDose(confirmada);

        assertThat(nova.dose()).isSameAs(confirmada);
        assertThat(nova).usingRecursiveComparison().ignoringFields("dose").isEqualTo(detalhe);
    }
}
