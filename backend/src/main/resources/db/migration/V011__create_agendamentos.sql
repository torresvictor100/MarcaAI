CREATE TABLE agendamentos (
    id BIGSERIAL PRIMARY KEY,
    encaminhamento_id BIGINT NOT NULL REFERENCES encaminhamentos (id),
    vaga_id BIGINT NOT NULL REFERENCES vagas_horario (id),
    data_hora TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADO',
    agendado_por VARCHAR(100) NOT NULL
);
