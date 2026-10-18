# Foody Delivery

Versão simplificada de um sistema de rastreamento de pedidos de delivery. Clientes se cadastram, fazem pedidos e
acompanham o status; um operador leva cada pedido pelas etapas de preparo e entrega.

## Arquitetura

O sistema tem três partes, e o Docker Compose sobe todas elas com um único comando:

```
  Navegador ──► Front-end (React)  ──► Back-end (Spring Boot)  ──► PostgreSQL
                    :5173                    :8080                    :5432
```

- **Front-end:** a interface. Roda no navegador e conversa com a API por HTTP.
- **Back-end:** a API REST. Cuida do cadastro, do login (token JWT), das regras dos pedidos e das permissões.
- **Banco de dados:** guarda usuários e pedidos. As tabelas são criadas sozinhas na primeira execução.

Estrutura do repositório:

```
desafio-foody-delivery/
├── docker-compose.yaml        # sobe banco, back-end e front-end juntos
├── rpd-backend/               # API (tem README e docker-compose próprios)
└── rpd-frontend/              # interface (tem README e docker-compose próprios)
```

Os detalhes de cada parte estão nos READMEs [do back-end](rpd-backend/README.md) e
[do front-end](rpd-frontend/README.md).

## Tecnologias

| Parte    | Tecnologias                                                                              |
|----------|------------------------------------------------------------------------------------------|
| Front-end | React, Vite e JavaScript                                                                |
| Back-end  | Java 17, Spring Boot (Web, Data JPA, Validation, Security), JWT, Liquibase e Swagger/OpenAPI |
| Banco     | PostgreSQL (H2 em memória nos testes automatizados)                                     |
| Infra     | Docker e Docker Compose                                                                  |

## Como executar

### Requisitos

- Docker com Docker Compose v2.20 ou superior (comando `docker compose`, sem hífen)
- Portas `5173`, `8080` e `5432` livres

### Subindo tudo

Na raiz do projeto:

```
docker compose up -d --build
```

A primeira execução demora alguns minutos, porque baixa as dependências do back-end e do front-end. Para saber se a
API já está pronta, use `docker compose ps` e espere o `rpd-backend` aparecer como `healthy`.

| O que                         | Endereço                                      |
|-------------------------------|-----------------------------------------------|
| Aplicação (interface)         | http://localhost:5173                         |
| API                           | http://localhost:8080                         |
| Documentação da API (Swagger) | http://localhost:8080/swagger-ui/index.html   |

### Outros comandos

```
docker compose logs -f rpd-backend     # acompanhar os logs da API
docker compose down                    # parar tudo
docker compose down -v                 # parar tudo e apagar também os volumes
```

O banco não usa volume nomeado, então `docker compose down` **descarta os dados**. Para pausar sem perdê-los, use
`docker compose stop` e, depois, `docker compose start`.

### Testes automatizados do back-end

```
docker compose --profile test run --rm --build tests
```

## Credenciais do operador

Ao iniciar, o sistema cria automaticamente um usuário com o papel de operador:

| Campo  | Valor              |
|--------|--------------------|
| E-mail | `operador@rpd.com` |
| Senha  | `Operador@123`     |

Qualquer pessoa que se cadastrar pela tela de cadastro (ou pelo endpoint `/auth/register`) recebe sempre o papel de
cliente. Não é possível criar um operador pela aplicação.

> Essas credenciais servem apenas para avaliação do projeto.

## Permissões

| Ação                 | Cliente                                                                        | Operador                              |
|----------------------|--------------------------------------------------------------------------------|---------------------------------------|
| Criar pedido         | sim (o pedido pertence a ele)                                                  | sim                                   |
| Listar pedidos       | apenas os próprios                                                             | todos                                 |
| Buscar pedido por ID | apenas os próprios                                                             | qualquer pedido                       |
| Atualizar status     | somente cancelar o próprio pedido, e apenas enquanto estiver `RECEBIDO`        | qualquer transição válida             |

Quando um cliente tenta acessar o pedido de outra pessoa, a API responde como se o pedido não existisse (`404`). Por
isso, para avançar um pedido por preparo, entrega e conclusão, é preciso entrar com a conta do operador.

## Status do pedido

Todo pedido nasce como `RECEBIDO` e só pode seguir os caminhos abaixo. Qualquer outra mudança é recusada.

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

## Roteiro rápido de avaliação

1. Abra http://localhost:5173, clique em **Ainda não tenho conta** e cadastre um cliente.
2. Clique em **Novo pedido**, preencha o endereço e os itens e crie o pedido. Ele aparece na lista como `Recebido`.
3. Clique em **Sair** e entre com a conta do operador (veja [Credenciais do operador](#credenciais-do-operador)).
4. O operador vê todos os pedidos e usa os botões de cada um para avançar o status até `Entregue`.
5. Entre de novo como o cliente e confira que o pedido mostra o status atualizado.