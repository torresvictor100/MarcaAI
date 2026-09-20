-- Melhora a massa de demonstração (sem recriar o banco):
--
-- 1) Nomes únicos para os 60 pacientes da V015. A V016 combinava só 10 primeiros nomes com 6 sobrenomes,
--    então "Fábio", "João"... se repetiam (e caíam juntos na mesma fila). Aqui cada paciente ganha um
--    primeiro nome diferente. Só muda o nome de exibição: login, CPF e todo o resto ficam iguais.
--
-- 2) 24 pacientes de "cenário de risco" (6 por especialidade), para a Análise IA da fila (ADR-008) sempre
--    ter o que apontar. Por especialidade:
--      - 3 VERMELHO atendidos há 0-2 dias (score ~100): ficam atrás dos dois urgentes abaixo -> "subir na fila";
--      - 1 AMARELO urgente atendido há 60 dias (score ~110): "urgente sem vaga" + "espera longa";
--      - 1 LARANJA urgente atendido há 2 dias (score ~106): "urgente sem vaga";
--      - 1 VERDE atendido há 45 dias (score ~47): "espera longa".
--    Nascem EM_ANALISE com todos os documentos válidos; quem calcula score e fila é a triagem de verdade,
--    disparada pelo DemoDataSeeder na subida (nunca SQL direto em analises_ia/itens_fila — ADR-004).
--    O seeder só agenda automaticamente a massa da V015, então estes pacientes ficam sempre aguardando.

-- 1) Nomes únicos da massa V015 (ordem pelo login paciente.demo001..060).
WITH novos (login, nome) AS (
    VALUES
        ('paciente.demo001', 'Beatriz Nogueira Barbosa'),
        ('paciente.demo002', 'Rafael Cunha Cardoso'),
        ('paciente.demo003', 'Letícia Moraes Duarte'),
        ('paciente.demo004', 'Gustavo Barbosa Siqueira'),
        ('paciente.demo005', 'Camila Teixeira Medeiros'),
        ('paciente.demo006', 'Thiago Ribeiro Figueiredo'),
        ('paciente.demo007', 'Juliana Carvalho Salgado'),
        ('paciente.demo008', 'Leonardo Rocha Teixeira'),
        ('paciente.demo009', 'Mariana Mendes Lima'),
        ('paciente.demo010', 'Felipe Freitas Farias'),
        ('paciente.demo011', 'Larissa Cardoso Tavares'),
        ('paciente.demo012', 'Vinícius Lima Fonseca'),
        ('paciente.demo013', 'Patrícia Araújo Coelho'),
        ('paciente.demo014', 'Rodrigo Gomes Viana'),
        ('paciente.demo015', 'Aline Martins Ribeiro'),
        ('paciente.demo016', 'Matheus Rezende Araújo'),
        ('paciente.demo017', 'Vanessa Pinheiro Castro'),
        ('paciente.demo018', 'Eduardo Duarte Campos'),
        ('paciente.demo019', 'Priscila Farias Macedo'),
        ('paciente.demo020', 'Lucas Castro Peixoto'),
        ('paciente.demo021', 'Renata Vieira Borges'),
        ('paciente.demo022', 'André Monteiro Carvalho'),
        ('paciente.demo023', 'Tatiane Correia Gomes'),
        ('paciente.demo024', 'Daniel Batista Vieira'),
        ('paciente.demo025', 'Natália Siqueira Andrade'),
        ('paciente.demo026', 'Marcelo Tavares Bezerra'),
        ('paciente.demo027', 'Bianca Campos Azevedo'),
        ('paciente.demo028', 'Gabriel Andrade Nogueira'),
        ('paciente.demo029', 'Débora Prado Rocha'),
        ('paciente.demo030', 'Henrique Queiroz Martins'),
        ('paciente.demo031', 'Isadora Dias Monteiro'),
        ('paciente.demo032', 'Leandro Medeiros Prado'),
        ('paciente.demo033', 'Jéssica Fonseca Lacerda'),
        ('paciente.demo034', 'Otávio Macedo Magalhães'),
        ('paciente.demo035', 'Luana Bezerra Cunha'),
        ('paciente.demo036', 'Sérgio Lacerda Mendes'),
        ('paciente.demo037', 'Carolina Brandão Rezende'),
        ('paciente.demo038', 'Fabrício Sampaio Correia'),
        ('paciente.demo039', 'Rebeca Figueiredo Queiroz'),
        ('paciente.demo040', 'Igor Coelho Brandão'),
        ('paciente.demo041', 'Sabrina Peixoto Xavier'),
        ('paciente.demo042', 'Paulo Azevedo Moraes'),
        ('paciente.demo043', 'Viviane Magalhães Freitas'),
        ('paciente.demo044', 'Caio Xavier Pinheiro'),
        ('paciente.demo045', 'Helena Guimarães Batista'),
        ('paciente.demo046', 'Renan Salgado Dias'),
        ('paciente.demo047', 'Yasmin Viana Sampaio'),
        ('paciente.demo048', 'Augusto Borges Guimarães'),
        ('paciente.demo049', 'Lorena Nogueira Barbosa'),
        ('paciente.demo050', 'Samuel Cunha Cardoso'),
        ('paciente.demo051', 'Cecília Moraes Duarte'),
        ('paciente.demo052', 'Wagner Barbosa Siqueira'),
        ('paciente.demo053', 'Adriana Teixeira Medeiros'),
        ('paciente.demo054', 'Márcio Ribeiro Figueiredo'),
        ('paciente.demo055', 'Talita Carvalho Salgado'),
        ('paciente.demo056', 'Hugo Rocha Teixeira'),
        ('paciente.demo057', 'Raquel Mendes Lima'),
        ('paciente.demo058', 'Diogo Freitas Farias'),
        ('paciente.demo059', 'Simone Cardoso Tavares'),
        ('paciente.demo060', 'Alexandre Lima Fonseca')
)
UPDATE usuarios u SET nome = n.nome FROM novos n WHERE u.login = n.login;

UPDATE pacientes p SET nome = u.nome
FROM usuarios u
WHERE p.usuario_id = u.id AND u.login LIKE 'paciente.demo%';

-- 2) Cenários de risco: usuário, paciente, atendimento, encaminhamento e documentos.
CREATE TEMP TABLE cenarios_risco (
    numero INTEGER, nome VARCHAR(200), especialidade VARCHAR(100), tipo VARCHAR(30), cid VARCHAR(10),
    risco VARCHAR(20), dias INTEGER, urgente BOOLEAN
) ON COMMIT DROP;

INSERT INTO cenarios_risco VALUES
    (1, 'Antônio Araújo Coelho', 'Cardiologia', 'CONSULTA_ESPECIALISTA', 'I20', 'VERMELHO', 0, FALSE),
    (2, 'Benedita Gomes Viana', 'Cardiologia', 'CONSULTA_ESPECIALISTA', 'I20', 'VERMELHO', 1, FALSE),
    (3, 'Cláudio Martins Ribeiro', 'Cardiologia', 'CONSULTA_ESPECIALISTA', 'I20', 'VERMELHO', 2, FALSE),
    (4, 'Dalva Rezende Araújo', 'Cardiologia', 'CONSULTA_ESPECIALISTA', 'I20', 'AMARELO', 60, TRUE),
    (5, 'Edson Pinheiro Castro', 'Cardiologia', 'CONSULTA_ESPECIALISTA', 'I20', 'LARANJA', 2, TRUE),
    (6, 'Francisca Duarte Campos', 'Cardiologia', 'CONSULTA_ESPECIALISTA', 'I20', 'VERDE', 45, FALSE),
    (7, 'Geraldo Farias Macedo', 'Ortopedia', 'CONSULTA_ESPECIALISTA', 'M54', 'VERMELHO', 0, FALSE),
    (8, 'Heloísa Castro Peixoto', 'Ortopedia', 'CONSULTA_ESPECIALISTA', 'M54', 'VERMELHO', 1, FALSE),
    (9, 'Ivone Vieira Borges', 'Ortopedia', 'CONSULTA_ESPECIALISTA', 'M54', 'VERMELHO', 2, FALSE),
    (10, 'Jorge Monteiro Carvalho', 'Ortopedia', 'CONSULTA_ESPECIALISTA', 'M54', 'AMARELO', 60, TRUE),
    (11, 'Lúcia Correia Gomes', 'Ortopedia', 'CONSULTA_ESPECIALISTA', 'M54', 'LARANJA', 2, TRUE),
    (12, 'Manoel Batista Vieira', 'Ortopedia', 'CONSULTA_ESPECIALISTA', 'M54', 'VERDE', 45, FALSE),
    (13, 'Neusa Siqueira Andrade', 'Dermatologia', 'CONSULTA_ESPECIALISTA', 'L40', 'VERMELHO', 0, FALSE),
    (14, 'Osvaldo Tavares Bezerra', 'Dermatologia', 'CONSULTA_ESPECIALISTA', 'L40', 'VERMELHO', 1, FALSE),
    (15, 'Rosa Campos Azevedo', 'Dermatologia', 'CONSULTA_ESPECIALISTA', 'L40', 'VERMELHO', 2, FALSE),
    (16, 'Sebastião Andrade Nogueira', 'Dermatologia', 'CONSULTA_ESPECIALISTA', 'L40', 'AMARELO', 60, TRUE),
    (17, 'Terezinha Prado Rocha', 'Dermatologia', 'CONSULTA_ESPECIALISTA', 'L40', 'LARANJA', 2, TRUE),
    (18, 'Valdir Queiroz Martins', 'Dermatologia', 'CONSULTA_ESPECIALISTA', 'L40', 'VERDE', 45, FALSE),
    (19, 'Zilda Dias Monteiro', 'Hemograma Completo', 'EXAME', 'D64', 'VERMELHO', 0, FALSE),
    (20, 'Nelson Medeiros Prado', 'Hemograma Completo', 'EXAME', 'D64', 'VERMELHO', 1, FALSE),
    (21, 'Irene Fonseca Lacerda', 'Hemograma Completo', 'EXAME', 'D64', 'VERMELHO', 2, FALSE),
    (22, 'Raimundo Macedo Magalhães', 'Hemograma Completo', 'EXAME', 'D64', 'AMARELO', 60, TRUE),
    (23, 'Conceição Bezerra Cunha', 'Hemograma Completo', 'EXAME', 'D64', 'LARANJA', 2, TRUE),
    (24, 'Waldemar Lacerda Mendes', 'Hemograma Completo', 'EXAME', 'D64', 'VERDE', 45, FALSE);

INSERT INTO usuarios (nome, papel, login, senha_hash)
SELECT c.nome, 'PACIENTE', 'paciente.risco' || lpad(c.numero::text, 2, '0'), crypt('Senha123!', gen_salt('bf', 10))
FROM cenarios_risco c;

INSERT INTO pacientes (nome, cpf, data_nascimento, contato, usuario_id)
SELECT c.nome,
       (91000000000 + c.numero)::text,
       DATE '1945-01-01' + ((c.numero * 211) % 25000),
       '(11) 97' || lpad(c.numero::text, 6, '0'),
       u.id
FROM cenarios_risco c
JOIN usuarios u ON u.login = 'paciente.risco' || lpad(c.numero::text, 2, '0');

INSERT INTO atendimentos (paciente_id, profissional_id, unidade_id, data, notas, classificacao_risco)
SELECT p.id,
       (SELECT id FROM profissionais WHERE registro_conselho = 'CRM-SP 111111'),
       (SELECT id FROM unidades_saude WHERE nome = 'UBS Jardim das Flores'),
       CURRENT_TIMESTAMP - (c.dias || ' days')::interval - INTERVAL '2 hours',
       'Cenário de risco (massa de dados) #' || c.numero,
       c.risco
FROM cenarios_risco c
JOIN usuarios u ON u.login = 'paciente.risco' || lpad(c.numero::text, 2, '0')
JOIN pacientes p ON p.usuario_id = u.id;

INSERT INTO encaminhamentos (atendimento_id, tipo, especialidade_ou_exame, cid_id, status, urgente, justificativa_urgencia)
SELECT a.id, c.tipo, c.especialidade, (SELECT id FROM cids WHERE codigo = c.cid), 'EM_ANALISE', c.urgente,
       CASE WHEN c.urgente THEN 'Piora do quadro relatada na UBS; médico pediu prioridade' END
FROM cenarios_risco c
JOIN atendimentos a ON a.notas = 'Cenário de risco (massa de dados) #' || c.numero;

INSERT INTO documentos (encaminhamento_id, tipo, referencia_arquivo, data_emissao, validade)
SELECT e.id, tde.tipo_documento,
       'demo/encaminhamento-' || e.id || '/' || lower(tde.tipo_documento) || '.pdf',
       CURRENT_DATE - 5, CURRENT_DATE + 300
FROM cenarios_risco c
JOIN atendimentos a ON a.notas = 'Cenário de risco (massa de dados) #' || c.numero
JOIN encaminhamentos e ON e.atendimento_id = a.id
JOIN tipos_documento_exigido tde ON tde.especialidade_ou_exame = c.especialidade;
