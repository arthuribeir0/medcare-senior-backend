-- ShedLock: trava distribuída dos jobs agendados (@Scheduled + @SchedulerLock). Com mais de uma instância da
-- aplicação, só a que conseguir a linha do job (name) executa. Estrutura padrão do ShedLock para PostgreSQL.
-- Os horários são gravados pelo relógio do banco (usingDbTime), em UTC, e não dependem do relógio das instâncias.
CREATE TABLE shedlock (
                          name VARCHAR(64) NOT NULL,
                          lock_until TIMESTAMP NOT NULL,
                          locked_at TIMESTAMP NOT NULL,
                          locked_by VARCHAR(255) NOT NULL,
                          CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
