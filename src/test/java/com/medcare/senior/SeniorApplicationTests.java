package com.medcare.senior;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Jobs desligados: o teste só valida o contexto (migrations + mapeamento JPA), sem agendar nada
@SpringBootTest(properties = "app.jobs.enabled=false")
class SeniorApplicationTests {

	@Test
	void contextLoads() {
	}

}
