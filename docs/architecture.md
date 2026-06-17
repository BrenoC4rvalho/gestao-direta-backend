# Arquitetura do Gestão Direta API

## Visão geral

O Gestão Direta API usa:

```text
Monólito modular + arquitetura em camadas
```

O sistema é um único backend Spring Boot, organizado por módulos de domínio.

Essa decisão oferece:
- simplicidade para o MVP;
- menor custo de infraestrutura;
- facilidade de manutenção;
- separação clara de responsabilidades;
- possibilidade de evolução futura.

## Stack

```text
Java 21
Spring Boot 3.5.15
Maven
PostgreSQL
Flyway
Spring Security
Spring Data JPA
Spring Web
Validation
SpringDoc OpenAPI
Auth0 Java JWT
Docker Compose Support
JUnit
Mockito
Testcontainers
```

## Pacote base

```text
br.com.gestaodireta
```

## Módulos do MVP

```text
shared
user
auth
farm
financial
```

## Estrutura geral

```text
br.com.gestaodireta
├── shared
├── user
├── auth
├── farm
└── financial
```

## Fluxo padrão

```text
Controller -> Service -> Repository -> Database
```

## Responsabilidades

### shared

Recursos comuns:
- BaseEntity;
- exceções;
- tratamento global de erros;
- resposta padrão;
- paginação;
- constantes;
- utilitários de segurança;
- configurações comuns.

### user

Dados globais do usuário:
- criação administrativa de usuários;
- consulta do usuário autenticado;
- atualização do próprio perfil;
- listagem administrativa;
- status global;
- tipo global.

### auth

Autenticação:
- login;
- logout;
- sessão atual;
- troca de senha;
- geração e validação de JWT;
- cookie gd_session;
- filtros de segurança.

Não existe cadastro público.

### farm

Fazendas e vínculos:
- cadastro de fazendas;
- listagem;
- atualização;
- inativação;
- vínculo de usuário com fazenda;
- papel por fazenda;
- autorização operacional por fazenda.

### financial

Núcleo financeiro:
- receitas;
- despesas;
- categorias;
- status de pagamento;
- formas de pagamento;
- exclusão lógica;
- resumo financeiro;
- contas a vencer;
- registro do usuário que criou ou alterou a transação.

## Modelo de acesso

Existem dois níveis de permissão:

```text
UserType
FarmUserRole
```

## UserType

Define o tipo global no sistema.

Valores:

```text
ADMIN
USER
```

### ADMIN

Administrador global. Pode acessar todas as fazendas e ações administrativas globais. Não precisa de vínculo com fazenda.

### USER

Usuário comum. Depende de vínculo com fazenda para acessar dados operacionais.

## FarmUserRole

Define o papel do usuário em uma fazenda específica.

Valores:

```text
PRODUCER
EMPLOYEE
ACCOUNTANT
INACTIVE
```

## Regras centrais

- ADMIN é global.
- USER depende de vínculo com fazenda.
- FarmUserRole fica no módulo farm.
- O mesmo usuário pode ter papéis diferentes em fazendas diferentes.
- FarmUserRole.INACTIVE remove acesso apenas naquela fazenda.
- UserStatus.INACTIVE remove acesso ao sistema inteiro.
- UserStatus.BLOCKED remove acesso ao sistema inteiro.

## Exemplo

| Usuário | UserType | Fazenda | FarmUserRole |
|---|---|---|---|
| Ana | ADMIN | Todas | Não precisa de vínculo |
| Breno | USER | Fazenda Boa Esperança | PRODUCER |
| João | USER | Fazenda Boa Esperança | EMPLOYEE |
| Maria | USER | Fazenda Boa Esperança | ACCOUNTANT |
| Carlos | USER | Fazenda Boa Esperança | INACTIVE |

## Relacionamentos principais

```text
User 1:N FarmUser
Farm 1:N FarmUser
Farm 1:N FinancialTransaction
User 1:N FinancialTransaction como createdByUser
User 1:N FinancialTransaction como updatedByUser
FinancialCategory 1:N FinancialTransaction
```

## Autenticação

Usar:

```text
JWT + Cookie HttpOnly/Secure
```

Cookie:

```text
gd_session
```

Claims recomendadas:

```text
sub: userId
email
userType
iat
exp
```

Não colocar:
- password;
- document;
- dados financeiros;
- dados sensíveis.

## Banco de dados

Banco:

```text
PostgreSQL
```

Migrations:

```text
Flyway
```

Regra:

```text
Flyway cria e altera estrutura.
Hibernate apenas valida.
```

Configuração:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

## Exclusões lógicas

Usar:
- FarmStatus.INACTIVE;
- FarmUserRole.INACTIVE;
- FinancialRecordStatus.DELETED;
- FinancialCategoryStatus.INACTIVE.

## Fora do MVP

Não implementar agora:
- harvest;
- credit;
- whatsapp;
- ai;
- notification;
- advanced-report.
