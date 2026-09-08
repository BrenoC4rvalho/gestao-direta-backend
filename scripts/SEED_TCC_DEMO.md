# Base demonstrativa para desenvolvimento e TCC

O script [`reset_and_seed_tcc_demo.sql`](./reset_and_seed_tcc_demo.sql) recria a base demonstrativa do Gestão Direta. Ela foi preparada para desenvolvimento, screenshots, relatórios, gráficos e demonstrações do TCC.

> Atenção: o script é destrutivo. Execute-o somente no banco local de desenvolvimento `gestaodireta`.

## O que o script faz

Ele remove os dados de usuários, contatos, vínculos usuário x fazenda, fazendas, categorias, atividades produtivas, safras, itens de planejamento, movimentações, pendências financeiras, mensageria e recuperação de senha. A estrutura do banco, as migrations e `flyway_schema_history` são preservadas.

Em seguida, ele cria cinco fazendas, oito usuários, categorias e atividades por fazenda, 48 safras entre 2020 e 2028, planejamento detalhado e movimentações financeiras. A data de referência do cenário é **08/09/2026**.

Não são criados dados de Telegram, conversas, mensagens, pendências originadas por IA, transcrições ou respostas de IA. Agenda, contas a pagar/receber e alertas são derivados de movimentações `PENDING` e `OVERDUE` de 2026.

## Pré-requisitos e execução

- PostgreSQL local iniciado conforme [`compose.yaml`](../compose.yaml) ou uma instância local equivalente.
- Schema atualizado pelas migrations da aplicação.
- Arquivo `.env` configurado com `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`.
- Cliente `psql` instalado.

Na raiz do backend, execute:

```bash
set -a
source .env
set +a
PGPASSWORD="$DB_PASSWORD" psql "${DB_URL#jdbc:}" -U "$DB_USERNAME" -v ON_ERROR_STOP=1 -f scripts/reset_and_seed_tcc_demo.sql
```

O script rejeita qualquer banco cujo nome não seja `gestaodireta`. Antes de executá-lo, confirme que `DB_URL` usa `localhost` e não aponta para produção.

No fim, o `psql` imprime validações de totais, safras por fazenda, movimentações por fazenda/tipo, zero movimentações realizadas futuras e zero movimentações para safras planejadas. Uma execução bem-sucedida encerra com `COMMIT`.

Para retornar ao estado padrão da demonstração, repita o mesmo comando. O reset e a recriação tornam a execução idempotente.

## Credenciais de desenvolvimento

Todas as contas abaixo usam a senha **`Demo@123`**. Ela é inserida no banco apenas como hash BCrypt e só deve ser usada localmente em desenvolvimento/demonstração.

| Usuário | E-mail | Senha | Perfil | Acesso |
|---|---|---|---|---|
| Administradora | `admin@gestaodireta.local` | `Demo@123` | `ADMIN` | Todas as fazendas, conforme acesso global atual. |
| Produtor Boa Esperança | `produtor.boaesperanca@gestaodireta.local` | `Demo@123` | `USER` + `PRODUCER` | Fazenda Boa Esperança. |
| Produtora Santa Helena | `produtor.santahelena@gestaodireta.local` | `Demo@123` | `USER` + `PRODUCER` | Fazenda Santa Helena. |
| Produtor São Miguel | `produtor.saomiguel@gestaodireta.local` | `Demo@123` | `USER` + `PRODUCER` | Fazenda São Miguel. |
| Produtora Vale Verde | `produtor.valeverde@gestaodireta.local` | `Demo@123` | `USER` + `PRODUCER` | Fazenda Vale Verde. |
| Produtor Horizonte | `produtor.horizonte@gestaodireta.local` | `Demo@123` | `USER` + `PRODUCER` | Fazenda Horizonte. |
| Colaborador Vale Verde | `colaborador.valeverde@gestaodireta.local` | `Demo@123` | `USER` + `EMPLOYEE` | Fazenda Vale Verde; pode visualizar e gerenciar dados financeiros, mas não gerenciar a fazenda. |
| Contadora | `contador@gestaodireta.local` | `Demo@123` | `USER` + `ACCOUNTANT` | Leitura das cinco fazendas. |

`ADMIN` e `USER` são tipos globais. `PRODUCER`, `EMPLOYEE` e `ACCOUNTANT` são papéis do vínculo com cada fazenda.

## Fazendas e histórias do dataset

| Fazenda | Área | Atividades principais | Perfil dos dados |
|---|---:|---|---|
| Fazenda Boa Esperança | 68 ha | Café | Operação estável com evolução anual de café. |
| Fazenda Santa Helena | 245 ha | Soja e milho safrinha | Crescimento de área, histórico de soja e complemento com milho. |
| Fazenda São Miguel | 118 ha | Feijão | Maior variação de custos e uma conta vencida em 2026. |
| Fazenda Vale Verde | 430 ha | Feijão e milho safrinha | Perfil diversificado, com produtor e colaborador para testar permissões. |
| Fazenda Horizonte | 185 ha | Soja | Histórico financeiro estável até 2025, maior concentração de compromissos vencidos em 2026 e planejamento de recuperação para 2027. |

O histórico possui 48 safras: 33 finalizadas, 7 em andamento em 2026 e 8 planejadas para 2027–2028. Há comparações entre anos da mesma atividade, itens de orçamento repetidos por categoria, receitas/despesas previstas e realizadas, despesas não planejadas e lançamentos gerais sem safra.

Na Fazenda Horizonte, a safra de soja de 2026/2027 combina execução já realizada, obrigações vencidas e recebíveis vencidos, além de compromissos futuros. O modelo atual não possui saldo/liquidação parcial por lançamento; por isso o cenário usa lançamentos independentes, todos integralmente abertos ou integralmente pagos. A safra 2027/2028 contém apenas planejamento com custos mais controlados e margem esperada positiva: ela não quita artificialmente os compromissos de 2026 e não possui movimentações realizadas.

As safras futuras possuem somente planejamento. Movimentações realizadas e respectivas datas de pagamento nunca ultrapassam 08/09/2026.
