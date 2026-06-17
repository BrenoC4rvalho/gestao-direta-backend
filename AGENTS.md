# AGENTS.md

## Projeto

Este repositório contém a API backend do Gestão Direta, um sistema de gestão financeira voltado ao agronegócio.

O MVP deve permitir:
- autenticação segura;
- gestão administrativa de usuários;
- cadastro e gestão de fazendas;
- vínculo entre usuários e fazendas;
- controle de permissões por fazenda;
- cadastro e consulta de movimentações financeiras;
- categorias financeiras;
- resumo financeiro;
- contas a vencer.

## Stack

- Java 21
- Spring Boot 3.5.15
- Maven
- PostgreSQL
- Flyway
- Spring Web
- Spring Security
- Spring Data JPA
- Bean Validation
- SpringDoc OpenAPI
- Auth0 Java JWT
- JWT em cookie HttpOnly/Secure
- Spring Boot Docker Compose Support
- JUnit
- Mockito
- Spring Security Test
- Testcontainers PostgreSQL

## Pacote base

Usar o pacote base:

```text
br.com.gestaodireta
```

## Arquitetura

Usar monólito modular + arquitetura em camadas.

Módulos do MVP:

```text
shared
user
auth
farm
financial
```

Cada módulo deve seguir, quando aplicável:

```text
controller/
dto/
entity/
enumeration/
mapper/
repository/
service/
```

## Ordem de implementação

Implementar nesta ordem:

```text
1. shared
2. user
3. auth
4. farm
5. financial
```

Não implementar módulos futuros sem solicitação explícita.

## Dependências permitidas

```text
auth -> user
auth -> shared

user -> shared

farm -> user
farm -> shared

financial -> farm
financial -> user
financial -> shared
```

Não criar dependência circular.

## Regras globais

- ADMIN é administrador global do sistema.
- USER é usuário comum do sistema.
- USER depende de vínculo com fazenda para acessar dados operacionais.
- O papel dentro da fazenda fica em FarmUserRole.
- FarmUserRole deve ter: PRODUCER, EMPLOYEE, ACCOUNTANT, INACTIVE.
- ADMIN não precisa de vínculo com fazenda para acessar fazendas.
- FarmUserRole.INACTIVE não permite acesso à fazenda.
- UserStatus.INACTIVE não permite acesso ao sistema.
- UserStatus.BLOCKED não permite acesso ao sistema.
- Não existe cadastro público.
- Usuários são criados apenas por ADMIN no módulo User.
- O primeiro ADMIN deve ser criado por migration/seed ou manualmente.
- Toda transação financeira deve pertencer a uma fazenda.
- Toda transação financeira deve registrar o usuário autenticado que cadastrou.
- Exclusões importantes devem ser lógicas, não físicas.
- Nunca retornar password em response.
- Não aceitar createdByUserId vindo do frontend.
- Não aceitar updatedByUserId vindo do frontend.
- O usuário autenticado deve ser recuperado pelo backend.

## Autenticação

- Usar JWT com Auth0 Java JWT.
- O JWT deve ser armazenado em cookie chamado gd_session.
- O cookie deve ser HttpOnly.
- O cookie deve ser Secure em produção.
- O cookie deve usar SameSite adequado.
- O JWT não deve ser retornado no body da resposta.
- O frontend deve usar credentials/include.
- O backend deve permitir credentials no CORS.
- Não usar Allowed-Origin * com cookie.
- Rotas privadas devem exigir cookie válido.
- JWT expirado deve retornar 401.
- JWT inválido deve retornar 401.

## Banco de dados

- Usar PostgreSQL.
- Usar Flyway para migrations.
- Criar migrations em src/main/resources/db/migration.
- Não usar spring.jpa.hibernate.ddl-auto=update.
- Usar spring.jpa.hibernate.ddl-auto=validate.
- Criar foreign keys.
- Criar unique constraints quando necessário.
- Preferir inativação lógica com status.

## Convenções

- Código em inglês.
- Pacotes em minúsculo.
- Classes em PascalCase.
- Campos em camelCase.
- Tabelas e colunas em snake_case.
- Endpoints REST no plural.
- Enums em UPPER_CASE.
- DTOs terminando com Request ou Response.
- Services terminando com Service.
- Controllers terminando com Controller.
- Repositories terminando com Repository.
- Mappers terminando com Mapper.

## Testes

Sempre que implementar ou alterar uma regra, criar ou atualizar testes.

Rodar:

```bash
./mvnw test
```

## Regras obrigatórias sobre testes

- Nunca remover testes para fazer o build passar.
- Nunca ignorar testes com `@Disabled`, `@Ignore` ou equivalente sem autorização explícita.
- Nunca alterar uma asserção para algo mais fraco apenas para o teste passar.
- Nunca remover cenários de teste existentes sem explicar o motivo e sem autorização.
- Nunca alterar regras de negócio para se adequar ao teste; corrigir a implementação conforme a regra documentada.
- Se um teste estiver errado, explicar por que está errado antes de alterar.
- Se uma alteração quebrar testes existentes, corrigir a causa real.
- Sempre executar `./mvnw test` após implementar ou alterar código.
- Se não conseguir fazer todos os testes passarem, informar exatamente quais falharam e o motivo provável.
- Não usar comandos para pular testes, como `-DskipTests`, `-Dmaven.test.skip=true` ou equivalentes.

## Restrições

- Não implementar cadastro público.
- Não retornar JWT no body.
- Não criar módulos não solicitados.
- Não alterar arquitetura sem solicitação.
- Não remover regras de permissão definidas.
- Não misturar UserType com FarmUserRole.
- Não criar papel operacional dentro de User.
- Não substituir Flyway por ddl-auto update.
- Não remover, enfraquecer ou desabilitar testes para fazer a tarefa passar.
- Não executar build pulando testes.
