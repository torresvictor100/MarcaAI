-- O papel UNIDADE_EXAME deixou de existir: o laboratório trabalha igual ao especialista (tem vagas, agenda
-- e registra o atendimento realizado), então passa a usar o papel ESPECIALISTA. A "especialidade" dele é o
-- exame que realiza, vinda do cadastro de profissional já vinculado em V018.
UPDATE usuarios
SET papel = 'ESPECIALISTA'
WHERE papel = 'UNIDADE_EXAME';
