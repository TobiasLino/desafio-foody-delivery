# RPD — Front-end de rastreamento de pedidos de delivery

Interface web em React para o sistema de rastreamento de pedidos. Pelo navegador, o cliente se cadastra, faz login,
cria pedidos e acompanha o status de cada um. O operador enxerga todos os pedidos e os leva pelas etapas de preparo
e entrega.

A interface consome a API do [back-end](../rpd-backend/README.md), que valida os dados e aplica as regras de permissão.
O front apenas decide o que exibir.

## Sumário

- [Tecnologias](#tecnologias)
- [Como executar](#como-executar)
- [Configuração](#configuração)
- [Usuários para teste](#usuários-para-teste)
- [Funcionalidades](#funcionalidades)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Como o front conversa com a API](#como-o-front-conversa-com-a-api)
- [Autenticação](#autenticação)
- [Tratamento de erros](#tratamento-de-erros)
- [Scripts disponíveis](#scripts-disponíveis)
- [Decisões de projeto](#decisões-de-projeto)
- [Limitações conhecidas](#limitações-conhecidas)

## Tecnologias

- React 19 e JavaScript (módulos ES)
- Vite como servidor de desenvolvimento e ferramenta de build
- Oxlint para análise estática do código
- CSS puro, sem biblioteca de componentes
- Docker (imagem `node:22-alpine`)

O projeto não usa biblioteca de rotas, de requisições HTTP nem de estado: apenas o React e a API `fetch` do navegador.

## Como executar

### Pelo repositório raiz (recomendado)

Na raiz do repositório, um único comando sobe o banco, o back-end e o front-end:

```
docker compose up -d --build
```

Com tudo no ar, a interface fica em `http://localhost:5173`. Veja o [README da raiz](../README.md) para os detalhes.

### Somente o front-end

É preciso ter a API rodando em `http://localhost:8080` (por exemplo, subindo o back-end pelo compose da pasta
`rpd-backend`). Depois, na pasta `rpd-frontend`:

**Com Docker**

```
docker compose up -d --build
```

**Sem Docker** (requer Node.js 22 ou superior)

```
npm install
npm run dev
```

Nos dois casos, a interface fica em `http://localhost:5173`.

> O container executa o servidor de desenvolvimento do Vite, com o código montado como volume. Isso é suficiente para
> avaliar o projeto, mas não é uma imagem de produção (veja [Limitações conhecidas](#limitações-conhecidas)).

## Configuração

| Variável        | Descrição                                                    | Valor padrão            |
|-----------------|--------------------------------------------------------------|-------------------------|
| `VITE_API_URL`  | Endereço da API, **como o navegador enxerga**                | `http://localhost:8080` |

- Fora do Docker, o valor vem do arquivo `.env`, na raiz do front.
- No Docker, vem do `docker-compose.yaml`.
- O prefixo `VITE_` é obrigatório: o Vite só entrega ao código do navegador as variáveis que começam com ele.
- Use `localhost` e não o nome do serviço do Docker (`rpd-backend`), porque quem chama a API é o navegador do usuário,
  que está fora da rede do Docker.

A API só aceita chamadas vindas de `http://localhost:5173` e `http://localhost:3000`. Para acessar o front por outro
endereço (como `http://127.0.0.1:5173`), é preciso liberar a origem no back-end (variável
`APP_CORS_ALLOWED_ORIGINS`).

## Usuários para teste

| Papel    | Como obter                                                                   |
|----------|------------------------------------------------------------------------------|
| Cliente  | Clique em **Ainda não tenho conta** e faça o cadastro                        |
| Operador | E-mail `operador@rpd.com` e senha `Operador@123` (criado pelo back-end)      |

> As credenciais do operador servem apenas para avaliação do projeto.

## Funcionalidades

**Para todos os usuários**

- Cadastro com nome, e-mail e senha (de 8 a 72 caracteres). Ao cadastrar, o login é feito automaticamente.
- Login com e-mail e senha. A sessão é encerrada sozinha quando o token expira (1 hora).
- Lista de pedidos com o status atual, o endereço de entrega e os itens, paginada de 10 em 10, do mais novo para o
  mais antigo.
- Criação de pedido com endereço de entrega e quantos itens forem necessários.
- Botão **Atualizar** para recarregar a lista.

**Diferenças por papel**

| Recurso                  | Cliente                                                             | Operador                                        |
|--------------------------|---------------------------------------------------------------------|-------------------------------------------------|
| Pedidos exibidos         | Apenas os próprios ("Meus pedidos")                                 | Todos ("Todos os pedidos"), com o nome do cliente |
| Botões de status         | Somente **Cancelar pedido**, enquanto o pedido está `RECEBIDO`      | Todas as transições válidas do status atual     |

**Botões exibidos para o operador**

| Status atual        | Botões                                      |
|---------------------|---------------------------------------------|
| `RECEBIDO`          | Iniciar preparo, Cancelar pedido            |
| `EM_PREPARO`        | Saiu para entrega, Cancelar pedido          |
| `SAIU_PARA_ENTREGA` | Marcar como entregue                        |
| `ENTREGUE`          | nenhum (estado final)                       |
| `CANCELADO`         | nenhum (estado final)                       |

Essa tabela repete as regras do back-end apenas para esconder botões que não fariam sentido. Quem decide de verdade é a
API, que recusa qualquer transição inválida.

## Estrutura do projeto

```
rpd-frontend/
├── index.html
├── vite.config.js
├── .env                       # VITE_API_URL para uso fora do Docker
├── Dockerfile
├── docker-compose.yaml
└── src/
    ├── main.jsx               # ponto de entrada: monta o React e o AuthProvider
    ├── App.jsx                # cabeçalho e escolha da tela (login ou pedidos)
    ├── App.css / index.css    # estilos
    ├── services/
    │   └── api.js             # único arquivo que fala com o back-end
    ├── context/
    │   ├── AuthContext.js     # contexto e hook useAuth()
    │   └── AuthProvider.jsx   # guarda o token e expõe usuário, login e logout
    ├── pages/
    │   ├── AuthPage.jsx       # login e cadastro
    │   └── Orders.jsx         # lista, paginação, novo pedido e atualização de status
    ├── components/
    │   ├── NewOrderForm.jsx   # formulário de novo pedido
    │   ├── OrderCard.jsx      # cartão de um pedido, com os botões de status
    │   ├── OrderStatus.jsx    # selo colorido do status
    │   └── ErrorMessage.jsx   # exibição de erros da API
    └── utils/
        ├── jwt.js             # lê e-mail, papéis e validade do token
        └── orders.js          # rótulos de status e regras dos botões
```

## Como o front conversa com a API

Toda comunicação passa por `src/services/api.js`:

| Função                      | Chamada                       | Uso                                         |
|-----------------------------|-------------------------------|---------------------------------------------|
| `register`                  | `POST /auth/register`         | Cadastro                                    |
| `login`                     | `POST /auth/login`            | Login (retorna o token JWT)                 |
| `listOrders`                | `GET /orders`                 | Lista paginada (`page`, `size`, `sort`)     |
| `getOrder`                  | `GET /orders/{id}`            | Busca de um pedido (disponível para uso)    |
| `createOrder`               | `POST /orders`                | Criação de pedido                           |
| `updateOrderStatus`         | `PATCH /orders/{id}/status`   | Mudança de status                           |

O contrato completo (campos, validações e códigos de erro) está no Swagger do back-end, em
`http://localhost:8080/swagger-ui/index.html`.

A listagem retorna um objeto com os pedidos em `content` e os dados de paginação em `page`
(`number`, `size`, `totalElements`, `totalPages`).

## Autenticação

1. No login, a API devolve um token JWT, que o front guarda no `localStorage` (chave `token`).
2. Toda chamada autenticada envia o cabeçalho `Authorization: Bearer <token>`.
3. O front lê o token (`utils/jwt.js`) apenas para saber o e-mail, o papel (cliente ou operador) e a validade. Ele não
   confere a assinatura, porque isso é responsabilidade da API.
4. A sessão termina quando o usuário clica em **Sair**, quando o token expira ou quando a API responde `401` a uma
   chamada autenticada.
5. Login e cadastro **não** enviam o token. Assim, um token vencido guardado no navegador não atrapalha um novo login.

## Tratamento de erros

A API devolve erros no formato Problem Details, com uma mensagem em `detail` e, em erros de validação, a lista `errors`
com o campo e o motivo. O front converte isso em um `ApiError` e exibe:

- a mensagem principal, como "Email ou senha inválidos" ou "Pedido não encontrado";
- um item por campo inválido, com rótulos legíveis (por exemplo, `address.cep` aparece como "CEP" e
  `items[0].description` como "Item 1").

Quando o servidor não responde, a tela mostra "Não foi possível conectar ao servidor".

Os formulários também fazem uma validação básica antes de enviar (campos obrigatórios, CEP com 8 dígitos, UF com 2
letras, tamanhos máximos), mas a validação que vale é a do back-end.

## Scripts disponíveis

| Comando           | O que faz                                              |
|-------------------|--------------------------------------------------------|
| `npm run dev`     | Inicia o servidor de desenvolvimento (porta 5173)      |
| `npm run build`   | Gera a versão de produção na pasta `dist`              |
| `npm run preview` | Serve localmente a versão gerada pelo build            |
| `npm run lint`    | Executa o Oxlint                                       |

## Decisões de projeto

- **Sem bibliotecas extras:** com apenas duas telas, o estado do React e a API `fetch` bastam. Isso mantém o projeto
  pequeno e fácil de ler.
- **Um único ponto de contato com a API:** os componentes nunca chamam `fetch` diretamente. Qualquer mudança no
  contrato (URL, cabeçalhos, formato de erro) é feita em um só arquivo.
- **O papel vem do token:** o front não pergunta à API quem é o usuário. Ele lê o papel do próprio token, o que
  evita uma chamada a mais.
- **Regras de permissão só na API:** o front esconde botões, mas não confia nisso para segurança. Todas as regras são
  aplicadas e testadas no back-end.
- **Pedidos do mais novo para o mais antigo:** assim, um pedido recém-criado aparece logo na primeira página.
- **Confirmação ao cancelar:** cancelar é irreversível, então o navegador pede confirmação antes de enviar.

## Limitações conhecidas

- O token fica no `localStorage`, o que é simples, mas o deixa acessível a qualquer script da página. Um ambiente de
  produção deveria usar cookies `HttpOnly`.
- Não há refresh token: ao expirar (1 hora), o usuário precisa entrar de novo.
- A imagem Docker executa o servidor de desenvolvimento do Vite. Uma imagem de produção geraria o build
  (`npm run build`) e serviria a pasta `dist` com um servidor web, como o Nginx.
- A lista não tem filtros nem busca, apenas paginação.
- Os itens do pedido têm só a descrição, porque o back-end não trabalha com quantidade nem preço.
- Não há testes automatizados no front-end.
