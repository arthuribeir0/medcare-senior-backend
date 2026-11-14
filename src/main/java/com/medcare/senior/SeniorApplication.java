/*
 * Ponto de partida do backend: a classe main do Spring Boot, que liga a configuração automática e a varredura dos
 * componentes. Não tem regra de negócio.
 */
package com.medcare.senior;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SeniorApplication {

	public static void main(String[] args) {
		SpringApplication.run(SeniorApplication.class, args);
	}

}
