# Auditoria técnica — Backend Spring Boot

**Sistema:** Gestão Direta

**Data de referência:** 24/10/2026
**Escopo:** código Java/Spring Boot, configuração, testes, persistência/migrations e integrações Telegram/Gemini presentes no repositório. Os resultados de teste são os obtidos durante a auditoria anterior, exceto quando indicado; não foram repetidos apenas para gerar este documento.

## 1. Resumo executivo

A auditoria analisou autenticação JWT por cookie, autorização por usuário/fazenda, DTOs, movimentações e relatórios financeiros, processamento e aprovação de sugestões de IA, integração Telegram, tratamento de exceções e migrations PostgreSQL.

Além das quatro correções anteriores, foram implementadas respostas públicas neutras na recuperação de senha, normalização de referências financeiras, política de transições com reabertura/cancelamento controlados e concorrência otimista. A configuração JWT foi preservada sem alterações nesta etapa.

| Situação backend | Quantidade | Observação |
|---|---:|---|
| Correções de código aplicadas | 8 | JWT/configuração, logs, precisão monetária, filtro JWT, recuperação, isolamento, transições e concorrência. |
| Achados pendentes | 0 de código | SEC-BE-002, SEC-BE-003 e LOG-BE-002 foram implementados e testados. |
| Ação operacional crítica | 1 | Configurar segredo novo e forte no ambiente e rotacionar qualquer ambiente que tenha usado o segredo conhecido. |
| Limitação de validação | 1 | OWASP Dependency-Check não completou por falta de feed/chave NVD; não é resultado de dependências limpas. |

## 2. Arquitetura e módulos analisados

Backend Java 21/Spring Boot organizado como monólito modular em camadas. Os pacotes relevantes incluem auth, user, farm, harvest, financial, messaging, ai, system e shared. Controllers e DTOs expõem REST; services concentram lógica transacional; Spring Data JPA persiste no PostgreSQL; Flyway mantém o schema.

| Módulo | Superfície considerada |
|---|---|
| auth/user | Login, sessão, logout, alteração/recuperação de senha, papel global e status de usuário. |
| farm/harvest | Fazendas, vínculos/roles por fazenda, safras, atividades e escopo de dados. |
| financial | Categorias, movimentações, agenda, contas a vencer, relatórios, exportações, pendências e aprovação/rejeição. |
| messaging/ai | Webhook Telegram, mensagens de texto/voz, extração estruturada e notificações. |
| shared/system | Segurança/configuração transversal, exceções, auditoria e status do sistema. |
| Persistência | Entidades JPA, constraints e 27 migrations Flyway numeradas até V29. |

## 3. Autenticação e segurança JWT

O backend autentica por JWT HMAC no cookie gd_session, sem retornar o token no body. O serviço confere assinatura, issuer e expiração; o filtro carrega usuário ativo e compara credentialsVersion. O cookie é HttpOnly, SameSite configurável e Secure controlado por APP_COOKIE_SECURE.

### SEC-BE-001 — segredo JWT padrão configurável de forma insegura

| Campo | Informação |
|---|---|
| Severidade | Crítica |
| Módulo | auth/configuração |
| Descrição/evidência | A configuração anterior admitia segredo padrão conhecido. A configuração agora exige APP_JWT_SECRET ([application.yaml](../src/main/resources/application.yaml#L109)); [JwtService](../src/main/java/br/com/gestaodireta/auth/security/JwtService.java#L45) rejeita o fallback conhecido e segredos com menos de 32 caracteres. O valor do segredo não é reproduzido neste relatório. |
| Impacto | Um segredo previsível poderia permitir assinatura de tokens forjados e acesso não autorizado com privilégios do token. |
| Correção/testes | Removido default permissivo; adicionados testes de configuração válida/inválida em [JwtServiceConfigurationTest](../src/test/java/br/com/gestaodireta/auth/security/JwtServiceConfigurationTest.java). |
| Estado | Corrigido no código; configuração/rotação operacional ainda necessária em cada ambiente. |

**Ação de implantação obrigatória:** definir APP_JWT_SECRET aleatório, forte, exclusivo por ambiente e armazenado em secret manager; rotacionar qualquer ambiente que tenha usado o valor padrão anterior (isso invalida sessões existentes). Definir APP_COOKIE_SECURE=true em produção. Não reutilizar nem registrar segredo em logs. APP_COOKIE_SECURE também é configuração obrigatória no YAML atual ([application.yaml](../src/main/resources/application.yaml#L122)).

Logout limpa o cookie no cliente, não mantém blacklist global. Mudança de credencial incrementa versão e invalida tokens anteriores quando reavaliados; JWT copiado pode continuar válido até expiração se não houve mudança de credencial. A política de revogação imediata deve ser decisão explícita de risco/produto, não tratada como vulnerabilidade confirmada nesta auditoria.

## 4. Autorização e isolamento de dados entre usuários

Os controllers usam Spring Security e controles contextuais por fazenda (classes de acesso de farm, harvest e financial). Para o cenário financeiro de dois usuários, testes MockMvc confirmaram que usuário A não lê, edita ou exclui transação de usuário B, não cria transação na fazenda B e não acessa o relatório alheio. A associação de categoria/safra estrangeira não é aceita. IDs de proprietário/cadastrador não são confiados ao cliente: o contexto autenticado é a origem da identidade.

O teste [FinancialTransactionControllerTest](../src/test/java/br/com/gestaodireta/financial/controller/FinancialTransactionControllerTest.java#L172) registra a tentativa multiusuário, incluindo leitura de relatório negada em caso posterior do mesmo teste ([linha 280](../src/test/java/br/com/gestaodireta/financial/controller/FinancialTransactionControllerTest.java#L280)). Os endpoints administrativos seguem papel global ADMIN; os testes dos controllers de usuário e messaging também exercitam autorização.

### SEC-BE-003 — diferença de resposta revela existência de categoria de outra fazenda

| Campo | Informação |
|---|---|
| Severidade | Baixa |
| Módulo | financial/categorias |
| Descrição/evidência | Criar ou filtrar movimentação com categoria pertencente à fazenda B na fazenda A e usar ID inexistente agora produzem 404 com a mesma mensagem “Financial category not found”. Safras inacessíveis seguem o mesmo padrão com “Harvest season not found”. |
| Impacto | Oráculo limitado para enumeração de IDs/estado de categorias. Não houve leitura ou alteração dos dados da outra fazenda. |
| Correção/testes | Resolução de categorias e safras converte ausência e falta de vínculo em recurso não encontrado, sem retornar dados estrangeiros. Testes de serviço e MockMvc foram atualizados para os cenários multi-fazenda. |
| Estado | Corrigido |

O bloqueio de acesso permanece confirmado e a resposta externa foi uniformizada. Nenhum BOLA de leitura/escrita foi reproduzido nos cenários testados.

## 5. Validação dos DTOs

Controllers usam Bean Validation em requests; são aplicadas restrições de presença, tamanho, enum, datas e valores. Campos de auditoria/usuário autenticado não devem vir do cliente. O achado de precisão abaixo foi a divergência financeira concreta corrigida.

### LOG-BE-001 — escala de valor monetário aceita acima de centavos

| Campo | Informação |
|---|---|
| Severidade | Alta |
| Módulo | financial/DTOs de movimentação |
| Descrição/evidência | BigDecimal aceitava valores com mais de duas casas; a gravação/serialização poderia arredondar em uma camada e produzir resposta divergente. |
| Arquivos | [FinancialTransactionRequest](../src/main/java/br/com/gestaodireta/financial/dto/FinancialTransactionRequest.java#L14), [FinancialTransactionUpdateRequest](../src/main/java/br/com/gestaodireta/financial/dto/FinancialTransactionUpdateRequest.java#L14), [ApprovePendingFinancialTransactionRequest](../src/main/java/br/com/gestaodireta/financial/dto/ApprovePendingFinancialTransactionRequest.java#L13) e [PendingFinancialTransactionUpdateRequest](../src/main/java/br/com/gestaodireta/financial/dto/PendingFinancialTransactionUpdateRequest.java#L13). |
| Correção/testes | Adicionado @Digits(integer=13, fraction=2) junto a restrições de valor positivo nos quatro requests; request com 10.005 é rejeitado em teste de controller. |
| Estado | Corrigido |

## 6. Integridade financeira e precisão monetária

Valores de domínio são representados com BigDecimal no backend; datas financeiras usam LocalDate, evitando deslocamento por fuso horário em vencimento/competência. Agregações e relatórios usam repositórios/serviços com filtros de fazenda, período, base e status. A validação de escala fecha a divergência identificada entre precisão recebida e escala persistida; não se introduziu arredondamento silencioso para aceitar input com fração de centavo.

Os testes cobrem valores positivos, valores não válidos, filtros e agregados sintéticos. Não foi executado recálculo independente de todas as contas a partir de dados de produção.

## 7. Regras de movimentações e transições de status

O modelo tem status de pagamento como PENDING, PAID, OVERDUE e CANCELED, além de estado de registro lógico. A política agora é centralizada em [FinancialTransactionStatusPolicy](../src/main/java/br/com/gestaodireta/financial/service/FinancialTransactionStatusPolicy.java), usada pelos fluxos de atualização, pagamento, cancelamento e reabertura.

### LOG-BE-002 — política de transições de status não está definida/garantida

| Campo | Informação |
|---|---|
| Severidade | Média (risco dependente da regra de negócio) |
| Módulo | financial/movimentações |
| Descrição/evidência | A implementação anterior atribuía status diretamente em update/pay/cancel, sem validar origem. |
| Impacto | Poderia deixar liquidação, compromissos e relatórios inconsistentes. |
| Correção/testes | PENDING/OVERDUE podem virar PAID ou CANCELED; PAID pode ser cancelado ou reaberto por endpoint explícito; reabertura limpa liquidação e recalcula PENDING/OVERDUE pela data; CANCELED não pode voltar. Testes cobrem transições inválidas, reabertura e cancelamento. |
| Estado | Corrigido |

Cancelamento lógico não desfaz transferência bancária e não cria estorno. O cancelamento preserva dados de liquidação para rastreabilidade, mas o status cancelado é excluído dos agregados financeiros ativos.

## 8. Relatórios e consultas financeiras

Controllers de relatório exigem autorização contextual de visualização da fazenda. Os serviços distinguem base de caixa/competência, período e granularidade; consultas e testes foram inspecionados para resumo, fluxo de caixa, categorias, safras, compromissos e filtros. Não foi reproduzida dupla contagem nem divergência de totais nos dados sintéticos testados.

A taxa de cumprimento usa a regra solicitada: liquidadosNoPrazo / (liquidadosNoPrazo + liquidadosEmAtraso + vencidosEmAberto) * 100, com denominador zero tratado; a implementação está em [FinancialReportRepository.java](../src/main/java/br/com/gestaodireta/financial/repository/FinancialReportRepository.java#L295), com testes em [FinancialReportServiceTest](../src/test/java/br/com/gestaodireta/financial/service/FinancialReportServiceTest.java). Próximos 7 dias usa data atual, independentemente do recorte histórico do relatório, conforme testes de serviço.

Safras não alocadas são preservadas na resposta de relatório; os totais por categoria/safra e séries de fluxo/evolução são filtrados pela fazenda/período. Os gráficos Angular e os indicadores correspondentes estão detalhados no relatório frontend. Não houve consulta a base real ou comparação com extrato bancário.

## 9. Segurança da integração Telegram/Gemini

O webhook Telegram exige segredo de validação quando habilitado e o processamento limita propriedades de entrada (como tamanho/duração de áudio) antes de encaminhar para transcrição/extração. O fluxo analisado separa recebimento, processamento, extração estruturada, validação e criação de pendência. A resposta do Gemini/IA não é considerada persistível apenas por ser JSON: DTOs e regras de domínio validam os campos.

Antes era registrado texto transcrito em log debug. Isso foi removido; o código agora registra apenas provedor e quantidade de caracteres em [TelegramVoiceMessageProcessor.java](../src/main/java/br/com/gestaodireta/messaging/service/TelegramVoiceMessageProcessor.java#L138). O texto segue sendo usado pelo fluxo de extração e armazenado conforme o ciclo da mensagem; política de retenção/exclusão de conteúdo deve ser confirmada com produto/privacidade.

Testes automatizados usam dependências simuladas. Não foram enviados payloads de ataque a Gemini/Telegram externos; prompt injection, indisponibilidade e respostas variáveis do provedor não foram exercitados ao vivo. Não foi confirmada vulnerabilidade de SSRF, injeção ou XSS no fluxo inspecionado.

## 10. Segurança do fluxo de aprovação humana

Sugestões permanecem em entidade de pendência e não viram movimentação definitiva antes de aprovação. Aprovação/rejeição valida estado e escopo, roda em transação; a consulta da pendência usa lock pessimista para serializar ações concorrentes. Notificação ocorre após commit. Esses controles e testes reduzem risco de aprovação repetida/registro parcial. Não foi feito teste de concorrência em múltiplas instâncias nem integração com webhook real.

## 11. Persistência PostgreSQL e migrations

Foram inspecionadas entidades, relacionamentos, migrations e consultas agregadas relevantes. Foi adicionada a migration V29, aditiva, para a coluna de versão otimista de `financial_transactions`; as migrations foram validadas em PostgreSQL 16 via Testcontainers. Nenhum banco de produção foi acessado/modificado.

As mutações do serviço financeiro são transacionais. Pendências de IA e conversas Telegram usam locks em operações concorrentes conforme a necessidade; não foi identificada necessidade de lock global. A auditoria não executou análise de carga/N+1 abrangente em banco de produção.

## 12. Tratamento de exceções

GlobalExceptionHandler retorna envelope consistente para validação, não encontrado, conflito, sem autorização, erro de parsing de IA e falha inesperada. Exceções inesperadas são convertidas em 500 genérico sem stack trace no corpo. Erros de autenticação inválida resultam em 401.

### INT-BE-001 — filtro JWT convertia falhas internas em 401

| Campo | Informação |
|---|---|
| Severidade | Média |
| Módulo | auth/security |
| Descrição/evidência | O filtro anterior abrangia exceções RuntimeException em torno do restante da cadeia, podendo classificar indisponibilidade/erro interno como credencial inválida e interromper a resposta do controller. |
| Correção/testes | Agora o filtro converte somente AuthenticationException, JWTVerificationException e NumberFormatException em 401; exceções posteriores seguem para o tratamento HTTP normal. Testes em [JwtAuthenticationFilterTest](../src/test/java/br/com/gestaodireta/auth/security/JwtAuthenticationFilterTest.java) verificam propagação de falha de controller/serviço. |
| Estado | Corrigido |

## 13. Vulnerabilidades e riscos identificados

### SEC-BE-002 — enumeração de conta na recuperação de senha

| Campo | Informação |
|---|---|
| Severidade | Média |
| Módulo | auth/recuperação de senha |
| Descrição/evidência | O contrato anterior expunha `telegramAvailable` e a etapa Telegram retornava os últimos dígitos do telefone. |
| Impacto | Facilita enumeração de usuários e perfil de recuperação, podendo apoiar phishing/ataques direcionados. Rate limit reduz abuso, mas não elimina o canal de informação. |
| Correção/testes | `/auth/password-recovery/options` agora sempre retorna mensagem fixa; o envio Telegram ocorre internamente e `/telegram` retorna somente 202 sem corpo. Não são retornados disponibilidade, telefone, token ou exceções de existência. Limite de solicitações, expiração, tentativas e invalidação foram preservados. Testes cobrem contas inexistentes, sem Telegram e fluxo de código. |
| Estado | Corrigido |

SEC-BE-003 está registrado na seção 4 como corrigido. Não houve acesso aos dados de categoria ou safra estrangeiros.

### SEC-BE-004 — transcrição sensível aparecia em log de debug

| Campo | Informação |
|---|---|
| Severidade | Média |
| Módulo | messaging/Telegram |
| Descrição/evidência | A transcrição de áudio era registrada integralmente em log debug; texto de voz pode conter dados financeiros ou pessoais. |
| Impacto | Exposição desnecessária em agregadores de log, backups e perfis de suporte com acesso aos logs. |
| Correção/testes | Removido o texto do log; agora são registrados provedor e tamanho da transcrição em [TelegramVoiceMessageProcessor.java](../src/main/java/br/com/gestaodireta/messaging/service/TelegramVoiceMessageProcessor.java#L138). Teste direcionado do processador de voz: 4 passaram. |
| Estado | Corrigido |

Não foram confirmados bypass de autenticação, leitura/escrita BOLA nos cenários executados, SQL injection, exposição de stack trace no corpo, mass assignment de proprietário ou criação definitiva por IA sem aprovação. CORS, CSRF e segurança de cookie devem ser validados contra a topologia real de produção.

## 14. Correções implementadas

| ID | Arquivos/área | Alteração | Estado |
|---|---|---|---|
| SEC-BE-001 | JwtService.java, application.yaml, README e testes de auth | Removido segredo default; segredo agora é obrigatório e validado; configuração operacional documentada sem expor valor. | Corrigido no código; implantação pendente |
| SEC-BE-004 | TelegramVoiceMessageProcessor.java | Removido conteúdo/transcrição dos logs; mantidos apenas provedor e tamanho do texto. | Corrigido |
| LOG-BE-001 | Quatro DTOs financeiros e teste de controller | Escala monetária limitada a duas casas, até 13 dígitos inteiros. | Corrigido |
| INT-BE-001 | JwtAuthenticationFilter.java e testes | Captura de exceções limitada a erros de autenticação/token; falhas downstream preservam status de erro interno. | Corrigido |
| SEC-BE-002 | AuthController, PasswordRecoveryService, DTOs e testes | Resposta pública genérica; Telegram continua sendo usado internamente sem expor disponibilidade, telefone ou falhas de envio. | Corrigido |
| SEC-BE-003 | FinancialTransactionService, FinancialCategoryService e testes | Categorias e safras inexistentes ou inacessíveis retornam recurso não encontrado uniforme. | Corrigido |
| LOG-BE-002 | FinancialTransactionStatusPolicy, FinancialTransactionService, V29 e testes | Transições explícitas, reabertura controlada, cancelamento de paga e versionamento otimista. | Corrigido |

## 15. Testes executados e resultados

| Verificação | Resultado | Observações |
|---|---|---|
| Testes Spring Boot (./mvnw test) | Aprovado | 563 testes passaram no conjunto completo, incluindo PostgreSQL/Testcontainers. |
| Testes direcionados de segurança e transições | Aprovado | Recuperação, isolamento, transições, reabertura, cancelamento e migrations V29 passaram. |
| Testes Telegram/transcrição | Aprovado | Teste direcionado após alteração do log: 4 testes passaram. |
| Testcontainers/PostgreSQL | Aprovado | PostgreSQL 16; migrations Flyway aplicadas no ambiente descartável dos testes. |
| Spotless (./mvnw spotless:check) | Falhou | Restam 16 arquivos preexistentes fora do escopo; os arquivos novos/alterados desta implementação foram mantidos formatados. |
| OWASP Dependency-Check Maven | Não concluído | Execução falhou porque a API NVD exigiu chave válida; nenhuma chave foi inventada ou gravada. A árvore Maven runtime foi gerada como verificação alternativa. |
| Build backend (./mvnw package -DskipTests) | Aprovado | JAR Spring Boot gerado com sucesso. |
| git diff --check | Aprovado | Sem whitespace errors nas alterações auditadas. |

O teste completo de 563 casos foi executado após as alterações e passou. O Dependency-Check não é considerado aprovado sem feed NVD válido.

## 16. Problemas pendentes

- Configurar APP_JWT_SECRET forte e único, substituir qualquer chave padrão já usada e definir APP_COOKIE_SECURE=true em produção.
- Configurar NVD/feed para completar Dependency-Check e revisar advisories do grafo Java.
- Validar CORS, CSRF, SameSite, secure cookie e rotação de segredo no ambiente implantado.

## Plano de ação recomendado

### Crítica

- Antes de qualquer implantação, provisionar APP_JWT_SECRET criptograficamente forte/aleatório e exclusivo; rotacionar qualquer ambiente que tenha usado o segredo padrão anterior; definir APP_COOKIE_SECURE=true. Componentes: secret manager, configuração e gestão de sessões. O código já falha fechado se o segredo for ausente/fraco.

### Alta

- Completar análise OWASP de dependências Java com feed NVD/API configurado e corrigir apenas vulnerabilidades confirmadas após análise de alcançabilidade/ambiente; afeta pom.xml, pipeline e dependências.
- Completar Dependency-Check com chave NVD fornecida por secret manager, fora do repositório, e revisar advisories Java por alcançabilidade.

### Média

- Executar testes de staging para configuração CORS/cookie/CSRF e revisão da política de retenção de mensagens/transcrições.

### Baixa

- Corrigir os 16 arquivos de formatação fora do escopo em tarefa dedicada e tornar Spotless obrigatório no CI sem misturar essa alteração com correções de segurança.
- Avaliar índices/consultas e concorrência sob carga representativa; a auditoria atual não demonstra gargalo em produção.

## 17. Limitações da auditoria

Nenhum banco de produção, dado pessoal/financeiro real ou serviço Telegram/Gemini externo foi usado. Respostas de IA e notificações foram avaliadas em testes controlados/mockados; não houve análise live de prompt injection nem concorrência multi-instância. A análise automatizada de dependências Java não terminou por falta de feed NVD configurado. Spotless reportou violações preexistentes em arquivos fora do diff. Os testes e revisões cobrem os fluxos registrados, mas não comprovam que o sistema esteja livre de vulnerabilidades ou erros.
