package com.medcare.senior.infrastructure.persistence.repository;

import com.medcare.senior.infrastructure.persistence.entity.CuidadorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CuidadorRepository extends JpaRepository<CuidadorEntity, Long> {

    Optional<CuidadorEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CuidadorEntity c SET c.fcmToken = :fcmToken WHERE c.id = :id")
    int atualizarFcmToken(@Param("id") Long id, @Param("fcmToken") String fcmToken);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CuidadorEntity c SET c.fcmToken = NULL WHERE c.fcmToken = :fcmToken AND c.id <> :id")
    int removerFcmTokenDosOutros(@Param("fcmToken") String fcmToken, @Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CuidadorEntity c SET c.fcmToken = NULL WHERE c.id = :id AND c.fcmToken = :fcmToken")
    int removerFcmTokenSeIgual(@Param("id") Long id, @Param("fcmToken") String fcmToken);
}
