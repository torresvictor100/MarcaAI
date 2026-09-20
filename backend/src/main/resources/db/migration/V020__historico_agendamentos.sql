-- Trilha de auditoria (LGPD) das mudanças num agendamento já confirmado: cancelamento, remarcação e
-- antecipação. Toda mudança exige motivo e registra quem fez e quando.
CREATE TABLE historico_agendamentos (
    id BIGSERIAL PRIMARY KEY,
    agendamento_id BIGINT NOT NULL REFERENCES agendamentos (id),
    acao VARCHAR(20) NOT NULL,
    vaga_anterior_id BIGINT NOT NULL REFERENCES vagas_horario (id),
    data_hora_anterior TIMESTAMP NOT NULL,
    vaga_nova_id BIGINT REFERENCES vagas_horario (id),
    data_hora_nova TIMESTAMP,
    motivo TEXT NOT NULL,
    feito_por VARCHAR(100) NOT NULL,
    feito_em TIMESTAMP NOT NULL
);

CREATE INDEX idx_historico_agendamentos_agendamento ON historico_agendamentos (agendamento_id);
