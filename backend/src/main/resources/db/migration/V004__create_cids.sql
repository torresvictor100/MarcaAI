CREATE TABLE cids (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(10) NOT NULL UNIQUE,
    descricao VARCHAR(300) NOT NULL
);

CREATE TABLE cid_especialidades (
    cid_id BIGINT NOT NULL REFERENCES cids (id),
    especialidade VARCHAR(100) NOT NULL
);
