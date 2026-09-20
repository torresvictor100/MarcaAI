CREATE TABLE pacientes (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(200) NOT NULL,
    cpf VARCHAR(11) NOT NULL UNIQUE,
    data_nascimento DATE NOT NULL,
    contato VARCHAR(200),
    usuario_id BIGINT REFERENCES usuarios (id)
);

CREATE TABLE profissionais (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(200) NOT NULL,
    registro_conselho VARCHAR(50) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    especialidade VARCHAR(100) NOT NULL,
    usuario_id BIGINT REFERENCES usuarios (id)
);

CREATE TABLE unidades_saude (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(200) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    endereco VARCHAR(300)
);
