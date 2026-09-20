-- A fila vale até o paciente ser atendido: encaminhamento REALIZADO sai da fila. A linha não é apagada,
-- porque guarda a justificativa de ajustes manuais (auditoria, LGPD) — só ganha a data de saída, e as
-- consultas de fila passam a ignorar quem já saiu.
ALTER TABLE itens_fila ADD COLUMN saiu_da_fila_em TIMESTAMP;

-- Encaminhamentos já realizados antes desta regra saem da fila agora.
UPDATE itens_fila i
SET saiu_da_fila_em = CURRENT_TIMESTAMP
FROM encaminhamentos e
WHERE e.id = i.encaminhamento_id
  AND e.status = 'REALIZADO'
  AND i.saiu_da_fila_em IS NULL;
