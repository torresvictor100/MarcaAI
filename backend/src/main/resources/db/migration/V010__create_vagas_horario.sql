CREATE TABLE vagas_horario (
    id BIGSERIAL PRIMARY KEY,
    unidade_id BIGINT NOT NULL REFERENCES unidades_saude (id),
    profissional_id BIGINT NOT NULL REFERENCES profissionais (id),
    especialidade_ou_exame VARCHAR(100) NOT NULL,
    data_hora TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DISPONIVEL'
);
