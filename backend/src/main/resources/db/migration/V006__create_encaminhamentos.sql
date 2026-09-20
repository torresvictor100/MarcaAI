CREATE TABLE encaminhamentos (
    id BIGSERIAL PRIMARY KEY,
    atendimento_id BIGINT NOT NULL REFERENCES atendimentos (id),
    tipo VARCHAR(30) NOT NULL,
    especialidade_ou_exame VARCHAR(100) NOT NULL,
    cid_id BIGINT NOT NULL REFERENCES cids (id),
    status VARCHAR(30) NOT NULL,
    urgente BOOLEAN NOT NULL DEFAULT FALSE,
    justificativa_urgencia TEXT
);
