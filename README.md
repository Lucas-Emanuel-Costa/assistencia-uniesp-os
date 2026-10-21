# Assistência UNIESP — Abertura de Ordem de Serviço

Atividade prática do Professor Jonas: implementação de `POST /ordens`, a partir do
[repositório original](https://github.com/JonasBernardino/assistencia-uniesp).
O histórico de commits do projeto base foi preservado.

## Requisitos implementados

| Regra | Implementação |
| --- | --- |
| RN01 | O construtor do domínio rejeita cliente inativo com a mensagem exigida. |
| RN02 | O domínio compara o ID do proprietário do equipamento com o ID do cliente informado. |
| RN03 | A OS nasce em `RECEBIDA`, com data e hora de abertura definidas no servidor. |
| RN04 | Protocolo `OS-YYYYMMDD-XXXX`, sequência hexadecimal diária, bloqueio pessimista e restrição `UNIQUE` no banco. |
| RN14 | Histórico inicial persistido em cascata: `RECEBIDA`, responsável `SISTEMA`, observação de abertura. |

Os contratos de entrada e saída são **records DTO**, separados das entidades JPA.
O controller aplica Bean Validation e devolve `201 Created`. O tratamento global
usa `ProblemDetail` e `application/problem+json` para os erros `400`, `404` e `422`.

A abertura inteira é transacional: OS, histórico e reserva do protocolo são
confirmados juntos. Uma falha desfaz os três. A data do protocolo é a mesma da
abertura, inclusive na virada do dia. O sufixo usa `0000` a `FFFF` (65.536 OS/dia);
ao esgotar, retorna `422`, sem repetir protocolos. A sequência é compartilhada
por todas as instâncias da aplicação que usam o mesmo banco.

A migração `V3` cria as tabelas de OS e histórico e completa as tabelas das demais
entidades já existentes no projeto base, necessárias para `ddl-auto: validate`.

## Executar os testes

Requisito: **JDK 21**, com `JAVA_HOME` configurado. O Maven Wrapper está incluído.

Windows / PowerShell:

```powershell
.\mvnw.cmd -B -ntp verify
```

Linux / macOS:

```sh
./mvnw -B -ntp verify
```

Os 22 testes usam H2 em modo PostgreSQL, executam as migrações reais do Flyway e
validam o schema pelo Hibernate. Não precisam de Docker ou banco externo.
Cobrem os cinco cenários obrigatórios, as mensagens exatas de negócio, histórico
persistido, JSON inválido, rollback, virada de data, esgotamento e 18 aberturas
concorrentes com protocolos distintos. Também verificam as regras diretamente
no domínio, sem passar pelo serviço.

## Executar a aplicação com PostgreSQL

Requisitos adicionais: Docker com Compose, ou um PostgreSQL disponível.

```powershell
docker compose -f docker/docker-compose.yml up -d
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

O perfil `local` usa o PostgreSQL do Compose em `localhost:5432`, banco
`assistencia`, usuário `postgres` e senha de desenvolvimento `123456`, conforme
a configuração original. O Flyway prepara o schema na inicialização.

Para usar outro banco, configure as variáveis e execute sem o perfil `local`:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/assistencia'
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = 'sua-senha'
.\mvnw.cmd spring-boot:run
```

## Testar a API manualmente

Depois de iniciar a aplicação, em outro terminal, carregue os dados de demonstração:

```powershell
Get-Content docs/dados-exemplo.sql | docker compose -f docker/docker-compose.yml exec -T postgres psql -U postgres -d assistencia
```

O script mostra os IDs criados. Substitua os IDs abaixo pelos valores exibidos,
usando o equipamento de Ana e o ID da própria Ana:

```powershell
$corpo = @{
    clienteId = 1
    equipamentoId = 1
    descricaoProblema = 'Notebook não liga após queda de energia elétrica.'
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/ordens -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($corpo))
```

Exemplo de resposta (`201 Created`):

```json
{
  "id": 1,
  "numeroProtocolo": "OS-20261015-0000",
  "status": "RECEBIDA",
  "dataAbertura": "2026-10-15T20:30:00"
}
```

| Cenário | Requisição | Resposta |
| --- | --- | --- |
| Sucesso | Cliente ativo, equipamento próprio, descrição preenchida | `201` com os quatro campos do contrato |
| Campos obrigatórios | `{}` ou descrição em branco | `400`, `ProblemDetail.erros` por campo |
| Recurso inexistente | ID de cliente ou equipamento não cadastrado | `404`, recurso e identificador |
| Cliente inativo | Cliente Caio e equipamento dele | `422`, mensagem RN01 |
| Equipamento de outro cliente | Cliente Bia e equipamento de Ana | `422`, mensagem RN02 |

Exemplo de erro de validação:

```json
{
  "type": "about:blank",
  "title": "Dados de requisição inválidos",
  "status": 400,
  "detail": "Erro na validação dos campos informados",
  "instance": "/ordens",
  "erros": {
    "clienteId": "O ID do cliente é obrigatório",
    "equipamentoId": "O ID do equipamento é obrigatório",
    "descricaoProblema": "A descrição do problema é obrigatória"
  }
}
```

As mensagens de negócio exigidas são preservadas literalmente:

- `Não é permitido abrir Ordem de Serviço para um cliente inativo.`
- `O equipamento informado não pertence ao cliente indicado.`
