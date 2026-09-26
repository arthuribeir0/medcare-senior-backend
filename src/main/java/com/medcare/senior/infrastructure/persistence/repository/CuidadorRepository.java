package com.medcare.senior.infrastructure.persistence.repository;

import com.medcare.senior.infrastructure.persistence.entity.CuidadorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CuidadorRepository extends JpaRepository<CuidadorEntity, Long> {

    Optional<CuidadorEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
