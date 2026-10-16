package com.medcare.senior;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

// Jobs e FCM desligados: o teste só valida o contexto (migrations + mapeamento JPA), sem agendar nada nem
// inicializar o Firebase
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false"})
class SeniorApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
	}

	@Test
	void semFcmHabilitadoOContextoSobeSemOsBeansDoFirebase() {
		assertThat(context.getBeansOfType(FirebaseApp.class)).isEmpty();
		assertThat(context.getBeansOfType(FirebaseMessaging.class)).isEmpty();
	}

}
