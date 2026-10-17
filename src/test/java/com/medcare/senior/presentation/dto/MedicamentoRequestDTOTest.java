package com.medcare.senior.presentation.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tipo de medicamento: lista fixa (COMPRIMIDO, CAPSULA, GOTAS, INJECAO, XAROPE, ADESIVO), validada na entrada.
 */
class MedicamentoRequestDTOTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void criarValidador() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void fecharValidador() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPRIMIDO", "CAPSULA", "GOTAS", "INJECAO", "XAROPE", "ADESIVO"})
    void aceitaOsTiposDaLista(String tipo) {
        assertThat(validator.validate(comTipo(tipo))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Comprimido", "comprimido", "CÁPSULA", "POMADA", " COMPRIMIDO", "COMPRIMIDO|GOTAS"})
    void recusaTipoForaDaListaComMensagemEmPortugues(String tipo) {
        Set<ConstraintViolation<MedicamentoRequestDTO>> violacoes = validator.validate(comTipo(tipo));

        assertThat(violacoes).singleElement().satisfies(violacao -> {
            assertThat(violacao.getPropertyPath()).hasToString("tipo");
            assertThat(violacao.getMessage())
                    .isEqualTo("O tipo deve ser um destes: COMPRIMIDO, CAPSULA, GOTAS, INJECAO, XAROPE ou ADESIVO");
        });
    }

    private static MedicamentoRequestDTO comTipo(String tipo) {
        return new MedicamentoRequestDTO("Losartana", "50mg", tipo, null, null, null, null, 10L);
    }
}
