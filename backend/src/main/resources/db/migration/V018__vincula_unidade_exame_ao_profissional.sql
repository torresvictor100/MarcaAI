-- O usuário da unidade de exame (lab.central) nasceu sem vínculo com o cadastro de profissional, e o
-- profissional "Equipe Laboratório Central (Hemograma)" sem usuário. A regra de acesso (ADR-007) usa esse
-- vínculo para saber qual exame a unidade realiza — sem ele, a unidade não enxergaria nenhum encaminhamento.
UPDATE profissionais
SET usuario_id = (SELECT id FROM usuarios WHERE login = 'lab.central')
WHERE registro_conselho = 'CRBM-SP 555555'
  AND usuario_id IS NULL;
