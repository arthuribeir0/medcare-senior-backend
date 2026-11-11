package com.medcare.senior.presentation;

import com.medcare.senior.application.usecase.DesvincularPacienteUseCase;
import com.medcare.senior.application.usecase.ExcluirContaUseCase;
import com.medcare.senior.domain.desconexao.PedidoDesconexaoRepositoryPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

/**
 * Falha no meio da desvinculação (depois de apagar códigos, doses, tratamentos, remédios e mensagens) desfaz tudo.
 * Sem transação de teste em volta: a do use case é a de verdade. Os dados criados são apagados no fim pela exclusão da
 * conta.
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false"})
class DesvinculacaoAtomicaTest {

    @Autowired
    private DesvincularPacienteUseCase desvincular;

    @Autowired
    private ExcluirContaUseCase excluirConta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @MockitoSpyBean
    private PedidoDesconexaoRepositoryPort pedidos;

    private Long cuidador;

    @AfterEach
    void apagarDados() {
        if (cuidador != null) {
            excluirConta.executar(cuidador);
        }
    }

    @Test
    void falhaNoMeioNaoDeixaEstadoParcial() {
        DadosDeDesvinculacao dados = new DadosDeDesvinculacao(jdbc, LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
        cuidador = dados.cuidador("Ana Souza");
        DadosDeDesvinculacao.Paciente jose = dados.pacienteCompleto(cuidador, "José Souza");
        doThrow(new IllegalStateException("falha simulada")).when(pedidos).deleteAllByPacienteId(anyLong());

        assertThatThrownBy(() -> desvincular.executar(jose.id(), cuidador))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("falha simulada");

        dados.assertIntacto(jose);
    }
}
