package com.medcare.senior.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class ValidacaoDeProducaoTest {

    private static final String SEGREDO_BOM = "um-segredo-de-producao-com-mais-de-32-caracteres!";
    private static final String SENHA_BOA = "senha-forte-de-producao";

    private static void validar(MockEnvironment environment) {
        ValidacaoDeProducao validacao = new ValidacaoDeProducao();
        validacao.setEnvironment(environment);
        validacao.postProcessBeanFactory(new DefaultListableBeanFactory());
    }

    private static MockEnvironment ambiente(String jwt, String senha, boolean fcm) {
        MockEnvironment environment = new MockEnvironment().withProperty("app.fcm.enabled", String.valueOf(fcm));
        if (jwt != null) {
            environment.setProperty("jwt.secret", jwt);
        }
        if (senha != null) {
            environment.setProperty("spring.datasource.password", senha);
        }
        return environment;
    }

    @Test
    void regrasDoSegredoDoJwtEDaSenhaDoBanco() {
        assertThat(SegredosDeProducao.problemas(SEGREDO_BOM, SENHA_BOA)).isEmpty();
        assertThat(SegredosDeProducao.problemas("a".repeat(32), SENHA_BOA)).isEmpty();
        assertThat(SegredosDeProducao.problemas(null, SENHA_BOA)).containsExactly("JWT_SECRET ausente");
        assertThat(SegredosDeProducao.problemas("  ", SENHA_BOA)).containsExactly("JWT_SECRET ausente");
        assertThat(SegredosDeProducao.problemas(SegredosDeProducao.JWT_SECRET_PADRAO, SENHA_BOA))
                .containsExactly("JWT_SECRET igual ao valor padrão de desenvolvimento do repositório");
        assertThat(SegredosDeProducao.problemas("a".repeat(31), SENHA_BOA))
                .containsExactly("JWT_SECRET com menos de 32 caracteres");
        assertThat(SegredosDeProducao.problemas(SEGREDO_BOM, null)).containsExactly("POSTGRES_PASSWORD ausente");
        assertThat(SegredosDeProducao.problemas(SEGREDO_BOM, "")).containsExactly("POSTGRES_PASSWORD ausente");
        assertThat(SegredosDeProducao.problemas(SEGREDO_BOM, SegredosDeProducao.SENHA_BANCO_PADRAO))
                .containsExactly("POSTGRES_PASSWORD igual à senha padrão de desenvolvimento do repositório");
        assertThat(SegredosDeProducao.problemas(null, null)).hasSize(2);
    }

    @Test
    void segredosValidosSobemSemErroComFcmLigado(CapturedOutput saida) {
        validar(ambiente(SEGREDO_BOM, SENHA_BOA, true));

        assertThat(saida.getOut()).doesNotContain("FCM DESLIGADO");
    }

    @Test
    void recusaSubirComMensagemClaraSemExporOsValores(CapturedOutput saida) {
        String segredoCurto = "curto-mas-secreto";
        assertThatThrownBy(() -> validar(ambiente(segredoCurto, SegredosDeProducao.SENHA_BANCO_PADRAO, true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET com menos de 32 caracteres")
                .hasMessageContaining("POSTGRES_PASSWORD igual à senha padrão")
                .hasMessageNotContaining(segredoCurto)
                .hasMessageNotContaining(SegredosDeProducao.SENHA_BANCO_PADRAO);
        assertThatThrownBy(() -> validar(ambiente(null, null, true)))
                .hasMessageContaining("JWT_SECRET ausente")
                .hasMessageContaining("POSTGRES_PASSWORD ausente");

        assertThat(saida.getAll()).doesNotContain(segredoCurto);
    }

    @Test
    void fcmDesligadoEmProducaoSobeComErroDestacadoNoLog(CapturedOutput saida) {
        validar(ambiente(SEGREDO_BOM, SENHA_BOA, false));

        assertThat(saida.getAll()).contains("PRODUÇÃO COM FCM DESLIGADO").contains("NÃO serão entregues")
                .doesNotContain(SEGREDO_BOM).doesNotContain(SENHA_BOA);
    }

    @Test
    void soEntraNoContextoComOPerfilProd() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(ValidacaoDeProducao.class)
                .withPropertyValues("jwt.secret=" + SegredosDeProducao.JWT_SECRET_PADRAO,
                        "spring.datasource.password=" + SegredosDeProducao.SENHA_BANCO_PADRAO);

        // Desenvolvimento e testes: valores padrão continuam aceitos (o validador nem existe)
        runner.run(contexto -> assertThat(contexto).hasNotFailed().doesNotHaveBean(ValidacaoDeProducao.class));
        // Produção: o contexto não sobe
        runner.withInitializer(contexto -> contexto.getEnvironment().setActiveProfiles("prod"))
                .run(contexto -> assertThat(contexto).hasFailed()
                        .getFailure().hasStackTraceContaining("Configuração de produção inválida"));
    }
}
