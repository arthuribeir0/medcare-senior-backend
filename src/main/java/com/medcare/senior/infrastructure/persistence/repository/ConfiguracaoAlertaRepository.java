package com.medcare.senior.infrastructure.persistence.repository;

import com.medcare.senior.infrastructure.persistence.entity.ConfiguracaoAlertaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracaoAlertaRepository extends JpaRepository<ConfiguracaoAlertaEntity, Long> {

    // Upsert em um comando: duas gravações simultâneas do mesmo cuidador não colidem na chave primária
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO configuracao_alerta (cuidador_id, tolerancia_minutos) VALUES (:cuidadorId, :minutos) "
            + "ON CONFLICT (cuidador_id) DO UPDATE SET tolerancia_minutos = EXCLUDED.tolerancia_minutos",
            nativeQuery = true)
    int salvarToleranciaMinutos(@Param("cuidadorId") Long cuidadorId, @Param("minutos") int minutos);
}
