package com.marcaai.shared;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Migração Flyway em Java (o Spring Boot entrega os beans {@code JavaMigration} ao Flyway): cifra os CPFs que
 * ainda estão em claro e preenche o índice cego (ADR-010). Precisa ser Java porque a chave vem do ambiente e
 * nunca pode aparecer num script SQL. O nome da classe é a versão (V026), entre a V025 e a V027.
 */
@Component
public class V026__Cifrar_cpf_dos_pacientes extends BaseJavaMigration {

    private final CriptografiaDados criptografia;

    public V026__Cifrar_cpf_dos_pacientes(CriptografiaDados criptografia) {
        this.criptografia = criptografia;
    }

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement consulta = context.getConnection().createStatement();
             ResultSet linhas = consulta.executeQuery("SELECT id, cpf FROM pacientes");
             PreparedStatement atualizacao = context.getConnection()
                     .prepareStatement("UPDATE pacientes SET cpf = ?, cpf_hash = ? WHERE id = ?")) {
            while (linhas.next()) {
                String cpfEmClaro = criptografia.decifrar(linhas.getString("cpf"));
                atualizacao.setString(1, criptografia.cifrar(cpfEmClaro));
                atualizacao.setString(2, criptografia.indiceCpf(cpfEmClaro));
                atualizacao.setLong(3, linhas.getLong("id"));
                atualizacao.addBatch();
            }
            atualizacao.executeBatch();
        }
    }
}
