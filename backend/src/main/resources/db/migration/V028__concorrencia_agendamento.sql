-- ADR-011: duas pessoas agendando a mesma vaga ao mesmo tempo liam "DISPONIVEL" e as duas gravavam
-- (reproduzido no ConcorrenciaAgendamentoTest: 5 agendamentos ativos numa vaga só). Duas travas:
--  1. versão (lock otimista) na vaga: a segunda gravação da mesma vaga falha e vira 409;
--  2. o banco garante no máximo um agendamento ativo por vaga e por encaminhamento, mesmo que a
--     aplicação erre (cancelado não conta: a vaga pode ser reagendada depois de um cancelamento).
ALTER TABLE vagas_horario ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX ux_agendamentos_vaga_ativa ON agendamentos (vaga_id) WHERE status <> 'CANCELADO';
CREATE UNIQUE INDEX ux_agendamentos_encaminhamento_ativo ON agendamentos (encaminhamento_id) WHERE status <> 'CANCELADO';
