package com.medcare.senior.infrastructure.docs;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Perfil que a operação exige, publicado no OpenAPI (descrição, cadeado do bearerAuth e respostas 401/403). Quem
 * decide o acesso de verdade é o SecurityConfig; o OpenApiContratoTest chama cada rota e falha se a anotação e a
 * regra de segurança divergirem.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PerfilExigido {

    Perfil value();

    enum Perfil {
        PUBLICO("público, sem token"),
        CUIDADOR("CUIDADOR"),
        PACIENTE("PACIENTE"),
        CUIDADOR_OU_PACIENTE("CUIDADOR ou PACIENTE");

        private final String rotulo;

        Perfil(String rotulo) {
            this.rotulo = rotulo;
        }

        public String rotulo() {
            return rotulo;
        }

        public boolean exigeToken() {
            return this != PUBLICO;
        }
    }
}
