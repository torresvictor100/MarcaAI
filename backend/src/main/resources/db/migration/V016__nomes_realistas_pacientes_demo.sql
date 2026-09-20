-- Os 60 pacientes de demonstração da V015 nasceram com nome genérico ("Paciente Demo 001"..."060"),
-- só para ficar fácil de rastrear durante o desenvolvimento da massa de dados. Esta migration só troca
-- o nome de exibição (usuarios.nome e pacientes.nome) por nomes brasileiros reais, mantendo o login
-- (paciente.demo001..060), CPF, data de nascimento e todo o resto exatamente como estão — não altera
-- nenhuma relação, só corrige a aparência do dado pra ficar mais parecida com produção.
WITH ordenados AS (
    SELECT u.id AS usuario_id, ROW_NUMBER() OVER (ORDER BY u.login) AS rn
    FROM usuarios u
    WHERE u.login LIKE 'paciente.demo%'
),
nomes AS (
    SELECT
        usuario_id,
        (ARRAY['Ana', 'Bruno', 'Carla', 'Diego', 'Elaine', 'Fábio', 'Gabriela', 'Henrique', 'Isabela', 'João']
            )[((rn - 1) % 10 + 1)::int]
        || ' ' ||
        (ARRAY['Souza', 'Oliveira', 'Santos', 'Pereira', 'Costa', 'Almeida']
            )[((rn - 1) / 10 + 1)::int]
        AS nome_novo
    FROM ordenados
)
UPDATE usuarios u SET nome = n.nome_novo
FROM nomes n
WHERE u.id = n.usuario_id;

UPDATE pacientes p SET nome = u.nome
FROM usuarios u
WHERE p.usuario_id = u.id AND u.login LIKE 'paciente.demo%';
