package com.medcare.senior.infrastructure.persistence.adapter;

import com.medcare.senior.domain.email.CodigoPorEmail;
import com.medcare.senior.domain.email.CodigoPorEmailRepositoryPort;
import com.medcare.senior.domain.email.FinalidadeDoCodigo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Optional;

/** Tabela codigo_email (V20) por SQL direto: uma linha por cuidador e finalidade, substituída a cada código novo. */
@Component
public class CodigoPorEmailRepositoryAdapter implements CodigoPorEmailRepositoryPort {

    private final JdbcTemplate jdbc;

    public CodigoPorEmailRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CodigoPorEmail> buscar(Long cuidadorId, FinalidadeDoCodigo finalidade) {
        return jdbc.query("SELECT cuidador_id, finalidade, codigo_hash, criado_em, expira_em, tentativas FROM codigo_email "
                        + "WHERE cuidador_id = ? AND finalidade = ?",
                (rs, linha) -> new CodigoPorEmail(rs.getLong("cuidador_id"),
                        FinalidadeDoCodigo.valueOf(rs.getString("finalidade")), rs.getString("codigo_hash"),
                        rs.getTimestamp("criado_em").toLocalDateTime(), rs.getTimestamp("expira_em").toLocalDateTime(),
                        rs.getInt("tentativas")),
                cuidadorId, finalidade.name()).stream().findFirst();
    }

    @Override
    public void salvar(CodigoPorEmail codigo) {
        jdbc.update("INSERT INTO codigo_email (cuidador_id, finalidade, codigo_hash, criado_em, expira_em, tentativas) "
                        + "VALUES (?, ?, ?, ?, ?, 0) ON CONFLICT (cuidador_id, finalidade) DO UPDATE SET "
                        + "codigo_hash = EXCLUDED.codigo_hash, criado_em = EXCLUDED.criado_em, "
                        + "expira_em = EXCLUDED.expira_em, tentativas = 0",
                codigo.cuidadorId(), codigo.finalidade().name(), codigo.codigoHash(),
                Timestamp.valueOf(codigo.criadoEm()), Timestamp.valueOf(codigo.expiraEm()));
    }

    @Override
    public void registrarTentativa(Long cuidadorId, FinalidadeDoCodigo finalidade) {
        jdbc.update("UPDATE codigo_email SET tentativas = tentativas + 1 WHERE cuidador_id = ? AND finalidade = ?",
                cuidadorId, finalidade.name());
    }

    @Override
    public void apagar(Long cuidadorId, FinalidadeDoCodigo finalidade) {
        jdbc.update("DELETE FROM codigo_email WHERE cuidador_id = ? AND finalidade = ?", cuidadorId, finalidade.name());
    }
}
