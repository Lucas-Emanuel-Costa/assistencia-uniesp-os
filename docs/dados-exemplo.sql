-- Dados fictícios para demonstração local. Execute após iniciar a aplicação.
INSERT INTO clientes (nome, cpf, email, ativo) VALUES
    ('Ana Exemplo', '11111111111', 'ana.exemplo@example.com', TRUE),
    ('Bia Exemplo', '22222222222', 'bia.exemplo@example.com', TRUE),
    ('Caio Exemplo', '33333333333', 'caio.exemplo@example.com', FALSE)
ON CONFLICT DO NOTHING;

INSERT INTO equipamentos (tipo, marca, modelo, numero_serie, cliente_id)
SELECT 'Notebook', 'Dell', 'Inspiron', 'DEMO-ANA-001', id
FROM clientes
WHERE email = 'ana.exemplo@example.com'
  AND NOT EXISTS (SELECT 1 FROM equipamentos WHERE numero_serie = 'DEMO-ANA-001');

INSERT INTO equipamentos (tipo, marca, modelo, numero_serie, cliente_id)
SELECT 'Notebook', 'Lenovo', 'ThinkPad', 'DEMO-CAIO-001', id
FROM clientes
WHERE email = 'caio.exemplo@example.com'
  AND NOT EXISTS (SELECT 1 FROM equipamentos WHERE numero_serie = 'DEMO-CAIO-001');

SELECT id AS cliente_id, nome, ativo FROM clientes WHERE email LIKE '%.exemplo@example.com' ORDER BY id;
SELECT e.id AS equipamento_id, e.modelo, c.id AS cliente_id, c.nome
FROM equipamentos e JOIN clientes c ON c.id = e.cliente_id
WHERE e.numero_serie IN ('DEMO-ANA-001', 'DEMO-CAIO-001') ORDER BY e.id;
