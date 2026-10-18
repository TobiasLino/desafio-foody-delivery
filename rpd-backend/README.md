# RPD — Back-end de rastreamento de pedidos de delivery

API REST em Java + Spring Boot para criar e acompanhar pedidos de delivery. Usuários se cadastram, fazem login e
recebem um token JWT; com ele, criam pedidos e acompanham o status. Um usuário **operador** é responsável por avançar
os pedidos pelas etapas de preparo e entrega.

A API é documentada com OpenAPI e pode ser explorada e testada diretamente no navegador pelo **Swagger UI**:
[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) (com a aplicação rodando).

## Sumário

- [Tecnologias](#tecnologias)
- [Como executar](#como-executar)
- [Credenciais do operador](#credenciais-do-operador)
- [Documentação da API (Swagger)](#documentação-da-api-swagger)
- [Testando via curl](#testando-via-curl)
- [Permissões](#permissões)
- [Status do pedido](#status-do-pedido)
- [Tratamento de erros](#tratamento-de-erros)
- [Testes](#testes)
- [Decisões de projeto](#decisões-de-projeto)
- [Limitações conhecidas](#limitações-conhecidas)

## Tecnologias

- Java 17 e Maven
- Spring Boot (Web MVC, Data JPA, Validation, Security)
- Autenticação stateless com JWT (HS256) via Spring Security OAuth2 Resource Server
- PostgreSQL, com versionamento do schema por Liquibase
- springdoc-openapi (OpenAPI 3 e Swagger UI)
- H2 em memória nos testes
- Docker e Docker Compose
- JUnit 5, Mockito e JaCoCo

## Como executar

O projeto é executado com Docker. O Postgres e a aplicação sobem juntos, e não é preciso instalar Java, Maven nem banco
de dados.

### Requisitos

- Docker
- Docker Compose
- Portas `8080` (API) e `5432` (Postgres) livres

### Executando

Na raiz do projeto:

```
docker-compose up -d --build
```

> Em instalações mais novas do Docker o comando é `docker compose` (sem hífen).

O comando faz o build da aplicação, cria a imagem Docker e sobe dois containers: a API e o banco Postgres. A primeira
execução demora mais, porque as dependências do Maven são baixadas. Quando terminar, a API estará em
`http://localhost:8080` e o Swagger UI em `http://localhost:8080/swagger-ui/index.html`.

As tabelas são criadas automaticamente pelo Liquibase na inicialização, então não é necessária nenhuma configuração
adicional. O banco já vem com usuários e pedidos de exemplo, um pedido em cada status, além das credenciais do Operador (veja [Credenciais do operador](#credenciais-do-operador)).

### Acompanhando os logs

```
docker-compose logs -f rpd-backend
```

### Desligando

```
docker-compose down
```

Esse comando remove os containers. Como o Postgres não usa um volume nomeado, **os dados do banco são descartados**.
Para pausar a aplicação sem perder os dados, use `docker-compose stop` e, depois, `docker-compose start`.

### Variáveis de ambiente

Já estão definidas no `docker-compose.yaml`:

| Variável            | Descrição                                                      | Valor no compose         |
|---------------------|----------------------------------------------------------------|--------------------------|
| `JWT_SECRET`        | Chave usada para assinar os tokens (mínimo de 32 caracteres)   | valor de desenvolvimento |
| `OPERATOR_EMAIL`    | E-mail do usuário operador criado na inicialização (opcional)  | `operador@rpd.com`       |
| `OPERATOR_PASSWORD` | Senha do usuário operador criado na inicialização (opcional)    | `Operador@123`           |

Os valores do compose servem apenas para avaliação e desenvolvimento. Em outro ambiente, defina um `JWT_SECRET` próprio.
Se `OPERATOR_EMAIL` ou `OPERATOR_PASSWORD` não estiverem definidos, nenhum operador é criado.

### Executando a aplicação fora do Docker (opcional)

É necessário ter Java 17. Suba apenas o banco e rode a aplicação com o Maven Wrapper:

```
docker-compose up -d postgres

JWT_SECRET=troque-por-uma-chave-com-pelo-menos-32-caracteres \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/rpd \
OPERATOR_EMAIL=operador@rpd.com \
OPERATOR_PASSWORD='Operador@123' \
./mvnw spring-boot:run
```

## Credenciais do operador

Ao iniciar com o `docker-compose`, a aplicação cria automaticamente um usuário com o papel `OPERATOR`:

| Campo  | Valor              |
|--------|--------------------|
| E-mail | `operador@rpd.com` |
| Senha  | `Operador@123`     |

O operador enxerga todos os pedidos e pode alterar o status deles para qualquer etapa permitida. Já os usuários
cadastrados em `/auth/register` sempre recebem o papel `CLIENT`, e não há como se cadastrar como operador pela API.
Veja a tabela completa em [Permissões](#permissões).

> Essas credenciais são destinadas somente à avaliação do projeto e não devem ser usadas em produção.

## Documentação da API (Swagger)

Com a aplicação rodando:

| Recurso                  | URL                                           |
|--------------------------|-----------------------------------------------|
| Swagger UI (interativo)  | `http://localhost:8080/swagger-ui/index.html` |
| Especificação OpenAPI    | `http://localhost:8080/v3/api-docs`           |

Cada endpoint está documentado com descrição, parâmetros, exemplos de requisição e resposta, regras de validação dos
campos e os códigos de erro possíveis (400, 401, 403, 404, 409).

### Endpoints

| Método | Rota                  | Descrição                       | Autenticação |
|--------|-----------------------|---------------------------------|--------------|
| POST   | `/auth/register`      | Cadastra um usuário             | não          |
| POST   | `/auth/login`          | Autentica e retorna o token JWT | não          |
| POST   | `/orders`             | Cria um pedido                  | sim          |
| GET    | `/orders`             | Lista pedidos (paginado)        | sim          |
| GET    | `/orders/{id}`        | Busca um pedido por ID          | sim          |
| PATCH  | `/orders/{id}/status` | Atualiza o status do pedido     | sim          |

### Como autenticar no Swagger UI

1. Abra `POST /auth/register`, clique em **Try it out** e cadastre um cliente (ou pule este passo para usar o
   operador).
2. Abra `POST /auth/login`, informe e-mail e senha e execute. Copie o valor de `token` da resposta.
3. Clique em **Authorize** (cadeado, no topo da página), cole **somente o token** (sem o prefixo `Bearer`) e confirme.
4. Os endpoints de `/orders` agora podem ser executados. O token vale por 1 hora.

Para testar o fluxo completo, entre com um cliente para criar o pedido e, depois, com o operador para avançar o status
(troque o token no **Authorize**).

## Testando via curl

Alternativa ao Swagger UI: um cliente cria um pedido e o operador o leva adiante.

```bash
# 1. cadastrar um cliente
curl -s -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name": "Maria Silva", "email": "maria@email.com", "password": "senha1234"}'

# 2. login do cliente (guarda o token)
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email": "maria@email.com", "password": "senha1234"}' \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')

# 3. criar um pedido
curl -s -X POST http://localhost:8080/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
        "address": {
          "cep": "12289020", "street": "Avenida Brasil", "neighborhood": "Centro",
          "complement": "Apto 12", "number": "100", "city": "Caçapava", "uf": "SP"
        },
        "items": [
          {"description": "Pizza de calabresa"},
          {"description": "Refrigerante 2L"}
        ]
      }'

# 4. listar os pedidos do cliente
curl -s http://localhost:8080/orders -H "Authorization: Bearer $TOKEN"

# 5. login do operador e avanço do status (troque 10 pelo id do pedido criado)
OP_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email": "operador@rpd.com", "password": "Operador@123"}' \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')

curl -s -X PATCH http://localhost:8080/orders/10/status \
  -H "Authorization: Bearer $OP_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"status": "EM_PREPARO"}'
```

## Permissões

O papel do usuário vem no token JWT. Os clientes só enxergam e movimentam os próprios pedidos.

| Ação                 | `CLIENT`                                                                           | `OPERATOR`                            |
|----------------------|------------------------------------------------------------------------------------|---------------------------------------|
| Criar pedido         | sim (o pedido pertence a ele)                                                      | sim                                   |
| Listar pedidos       | apenas os próprios                                                                 | todos, com filtro opcional `clientId` |
| Buscar pedido por ID | apenas os próprios                                                                 | qualquer pedido                       |
| Atualizar status     | somente para `CANCELADO`, no próprio pedido e apenas enquanto estiver `RECEBIDO`   | qualquer transição válida             |

Detalhes do comportamento:

- Quando um cliente consulta ou altera o pedido de outra pessoa, a resposta é `404`, para não revelar que o pedido
  existe.
- Um cliente que usa `?clientId=` com o ID de outro usuário recebe `403`.
- Um cliente que tenta qualquer status diferente de `CANCELADO` recebe `403`.
- Um cliente que tenta cancelar um pedido que já saiu de `RECEBIDO` recebe `409`.

Por isso, para avançar um pedido por `EM_PREPARO`, `SAIU_PARA_ENTREGA` e `ENTREGUE`, use a conta do operador.

## Status do pedido

O pedido nasce como `RECEBIDO` e só pode seguir as transições abaixo. Qualquer outra retorna `409`.

```
RECEBIDO ──► EM_PREPARO ──► SAIU_PARA_ENTREGA ──► ENTREGUE
   │             │
   └──► CANCELADO ◄──┘
```

| De                  | Para permitido                   |
|---------------------|----------------------------------|
| `RECEBIDO`          | `EM_PREPARO`, `CANCELADO`        |
| `EM_PREPARO`        | `SAIU_PARA_ENTREGA`, `CANCELADO` |
| `SAIU_PARA_ENTREGA` | `ENTREGUE`                       |
| `ENTREGUE`          | nenhum (estado final)            |
| `CANCELADO`         | nenhum (estado final)            |

## Tratamento de erros

Os erros são tratados de forma centralizada (`@RestControllerAdvice`) e seguem o padrão
[RFC 9457 (Problem Details)](https://www.rfc-editor.org/rfc/rfc9457), com `Content-Type: application/problem+json`.
Isso vale também para as respostas `401` e `403` geradas pela camada de segurança.

Exemplo de erro de validação (`400`):

```json
{
  "type": "about:blank",
  "title": "Erro de validação",
  "status": 400,
  "detail": "Um ou mais campos são inválidos",
  "instance": "/orders",
  "errors": [
    { "field": "address.cep", "message": "CEP deve conter 8 dígitos" },
    { "field": "items", "message": "não deve estar vazio" }
  ]
}
```

| Status | Quando acontece                                                                                          |
|--------|----------------------------------------------------------------------------------------------------------|
| `400`  | Campos inválidos, corpo malformado, parâmetro com tipo errado ou campo de ordenação inválido              |
| `401`  | Token ausente, inválido ou expirado; e-mail ou senha incorretos no login                                  |
| `403`  | Usuário autenticado sem permissão para a operação                                                        |
| `404`  | Pedido não encontrado (ou pertencente a outro cliente) ou rota inexistente                               |
| `405`  | Método HTTP não suportado pela rota                                                                       |
| `409`  | E-mail já cadastrado, transição de status inválida ou violação de restrição do banco                      |
| `415`  | `Content-Type` não suportado                                                                              |
| `500`  | Erro inesperado (a resposta não expõe detalhes internos; a causa fica registrada no log)                  |

## Testes

Os testes usam o H2 em memória (modo de compatibilidade com PostgreSQL) com o schema criado pelo Liquibase, então não é
necessário ter um Postgres disponível.

### Via Docker

```
docker compose --profile test run --rm --build tests
```

O container faz o build do projeto, executa `mvn test` com o profile correto e mostra o resultado no terminal.

### Via Maven

Com Java 17 instalado:

```
./mvnw test
```

O JaCoCo gera o relatório de cobertura em `target/site/jacoco/index.html`.

### O que é coberto

- Máquina de estados do pedido: transições permitidas e proibidas
- Regras de negócio do `OrderService`: criação, busca, atualização de status e permissões de cliente e operador
- Cadastro de usuário: normalização do e-mail, hash da senha e e-mail duplicado
- Geração e validação do token JWT e carregamento do usuário para autenticação
- Mapeamento das entidades para os DTOs de resposta

## Decisões de projeto

- **PostgreSQL no lugar de SQLite:** o desafio aceita "SQLite ou similar". O Postgres se aproxima mais de um ambiente
  real, e o schema é versionado com Liquibase, com changesets só adicionados, nunca editados.
- **JWT stateless:** o login devolve um token assinado (HS256) com o e-mail do usuário e seu papel. Não há sessão no
  servidor.
- **Cliente do pedido vem do token:** o corpo da criação não recebe o cliente, o que impede alguém de criar um pedido em
  nome de outro usuário.
- **Papéis `CLIENT` e `OPERATOR`:** quem faz o pedido não controla o preparo e a entrega dele. O cadastro público sempre
  cria `CLIENT`, e o operador é provisionado por variáveis de ambiente.
- **404 em vez de 403 para pedidos de terceiros:** evita que um cliente descubra quais IDs existem.
- **DTOs separados das entidades:** a API nunca expõe a entidade JPA nem o hash da senha.
- **Ordenação restrita:** só `id` e `status` são aceitos em `sort`, evitando ordenação por campos internos.
- **Paginação por padrão:** a listagem nunca retorna todos os registros de uma vez.
- **Contrato documentado no código:** a documentação OpenAPI é gerada a partir de anotações nos controllers e DTOs
  (springdoc), então ela evolui junto com a API.
- **Organização em camadas:** `domain` (entidades e regras de negócio), `restapi` (controllers e DTOs) e `infra`
  (segurança, exceções e configurações).

## Limitações conhecidas

- Os itens do pedido têm apenas descrição, sem quantidade, preço ou vínculo com um catálogo de produtos.
- O papel do usuário fica no token e vale até ele expirar (1 hora). Não há refresh token nem revogação.
- Não há limitação de tentativas no login.
- O segredo do JWT e as credenciais do operador estão no `docker-compose.yaml` para facilitar a avaliação e devem ser
  substituídos fora desse contexto.
- O Swagger UI e a especificação OpenAPI são públicos, para facilitar a avaliação. Em produção, o acesso deve ser
  restrito ou desabilitado.
- O banco não usa volume persistente, então `docker-compose down` descarta os dados.
