package com.medcare.senior.infrastructure.jobs;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Trava distribuída dos jobs (ShedLock, tabela shedlock da V9): com várias instâncias da aplicação, cada execução
 * agendada roda em uma só. Cada job declara o próprio @SchedulerLock com nome único.
 *
 * <p>defaultLockAtMostFor vale para job sem lockAtMostFor próprio: é o tempo máximo que a trava fica presa se a
 * instância que a pegou morrer no meio da execução.
 *
 * <p>usingDbTime: lock_until e locked_at são calculados pelo relógio do PostgreSQL, e não pelo de cada instância,
 * para que relógios de servidores dessincronizados não liberem a trava antes da hora.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
