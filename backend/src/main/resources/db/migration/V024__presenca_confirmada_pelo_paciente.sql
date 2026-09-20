-- Confirmação de presença pelo próprio paciente no agendamento em vigor. Nulo = ainda não confirmou.
-- Guarda o momento (auditoria); volta a nulo quando a data muda (remarcar/antecipar), porque a confirmação
-- valia para a data anterior.
ALTER TABLE agendamentos ADD COLUMN presenca_confirmada_em TIMESTAMP;
