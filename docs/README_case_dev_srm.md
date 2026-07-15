# Desafio Técnico: Plataforma de Cessão de Crédito Multimoedas (SRM Credit Engine)

## 1. Contexto Empresarial

A **SRM Asset** é uma referência em fundos de investimento, especialmente em FIDCs (Fundos de Investimento em Direitos Creditórios). Nossa operação envolve a aquisição de ativos (duplicatas, contratos, recebíveis) de empresas cedentes, provendo liquidez ao mercado.

Com a globalização do portfólio, o fundo passou a operar com caixa multimoedas (BRL e USD). O time de mesas de operação necessita de um sistema robusto, o **SRM Credit Engine**, para precificar e liquidar esses ativos com segurança e precisão decimal.

**O Problema de Negócio:**
Precisamos de uma plataforma que receba um lote de recebíveis, calcule o "deságio" (desconto) baseado no risco do ativo e na moeda de pagamento, e registre a transação de forma auditável.

---

## 2. Política de Uso de IA (AI as a Co-Pilot)

Acreditamos que a Inteligência Artificial é uma alavanca de produtividade. O uso de LLMs (ChatGPT, Claude, Gemini, Copilot) é **permitido e encorajado**, sob as seguintes diretrizes:

1.  **Autoria Intelectual:** Você deve dominar 100% do código entregue. "Foi o Copilot que gerou" não é uma defesa aceitável para falhas de segurança ou lógica.
2.  **Documentação de Uso:** Inclua um arquivo `AI_USAGE.md` no repositório descrevendo:
    * Prompts estratégicos utilizados (ex: geração de massa de dados, refatoração de queries, scaffolding).
    * Trechos onde a IA alucinou ou gerou código inseguro e como você corrigiu.
    * Análise crítica: Onde a IA economizou tempo e onde ela atrapalhou?

---

## 3. Escopo Técnico - Backend (Agnóstico)

Você tem liberdade para escolher a stack tecnológica, desde que seja uma escolha adequada para o ambiente financeiro (tipagem forte e frameworks maduros são diferenciais).

### Requisitos Funcionais Principais

1.  **Gestão de Câmbio (Currency Engine):**
    * Sistema capaz de armazenar e prover taxas de câmbio (ex: USD para BRL).
    * Endpoint para atualização manual ou integração (mockada) de taxas.

2.  **Motor de Precificação (Strategy Pattern):**
    * Cada tipo de recebível possui uma regra de risco (Spread) diferente. Aplique o padrão **Strategy** para desacoplar a regra do cálculo.
    * *Fórmula Base:* `Valor Presente = Valor Face / (1 + Taxa Base + Spread)^Prazo`
    * *Variações de Risco (Exemplo):*
        * Duplicata Mercantil: Spread de 1.5% a.m.
        * Cheque Pré-datado: Spread de 2.5% a.m.
    * Se a operação for cross-currency (Título em BRL, Pagamento em USD), aplicar a conversão cambial no final.

3.  **Persistência e Integridade:**
    * Uso de Banco de Dados Relacional (preferencialmente).
    * Transações financeiras devem respeitar as propriedades **ACID**. Nenhuma liquidação pode ficar "pela metade" (cuidado com *race conditions*).

4.  **API RESTful (API First):**
    * Design de APIs claro, seguindo verbos HTTP corretos e códigos de status semânticos.
    * Documentação via OpenAPI/Swagger.

5.  **Consultas Analíticas:**
    * Implementar uma rota de "Extrato de Liquidação" que permita filtrar grandes volumes de dados por período, cedente e tipo de moeda.
    * *Diferencial:* Uso de Query Builders ou SQL nativo otimizado para performance em vez de ORMs puros para relatórios.

6.  **Arquitetura em camadas para o backend:**
    * Separação das lógicas de aplicação, de negócio e de persistência em 3 camadas.
    * Relatórios podem ser organizados em duas camadas apenas sem necessidade de passar pela de negócios.

---

## 4. Escopo Técnico - Frontend (Agnóstico)

Escolha um framework de SPA moderno (React, Vue, Angular, Svelte, etc).

1.  **Painel do Operador:**
    * Interface para input dos dados do recebível (Valor, Vencimento, Tipo).
    * Exibição em tempo real do cálculo do valor líquido (simulação).

2.  **Grid de Transações:**
    * Tabela de histórico com paginação (Server-Side).
    * Filtros dinâmicos.

3.  **Arquitetura de Front:**
    * Separação clara entre lógica de apresentação (UI Components) e lógica de negócio/estado.
    * Gerenciamento de Estado Global (se necessário).

---

## 5. Requisitos não Funcionais

1.  **Tratamento de Exceções:**
    * Implementar tratamento de exceções para garantir resiliência, lidando com erros inesperados de forma controlada, sem interromper o fluxo da aplicação de forma abrupta.

2.  **Critérios de Aceite:**
    * Planejar e definir critérios de aceite que garantam usabilidade, segurança, desempenho e escalabilidade.
---

## 6. System Design, Git Workflow & Expectativas por Senioridade

A complexidade da entrega deve escalar conforme o nível da vaga. **O uso do Git será avaliado** como reflexo da sua organização e capacidade de trabalhar em times de alta performance.

### 🟢 Nível Júnior
* **Foco:** Código limpo, funcional e bem organizado.
* **Git & Versionamento:**
    * **Commits Atômicos:** Evite commits gigantes com a mensagem "finalizado". Quebre em tarefas menores (ex: "cria tabela cliente", "adiciona validação cpf").
    * **Branching Básico:** Não trabalhe direto na `main`/`master`. Crie branches para suas funcionalidades (ex: `feature/calculo-desagio`).
* **Entregáveis:**
    * API e Frontend rodando localmente.
    * Lógica de cálculo correta.
    * Banco de dados normalizado (Diagrama ER básico).
    * Instruções de "Como rodar" claras no README.

### 🟡 Nível Pleno
* **Foco:** Padrões de Projeto, Robustez e Fluxo de Trabalho.
* **Git & Versionamento (Acumulativo):**
    * **Conventional Commits:** Uso obrigatório de padronização nas mensagens (ex: `feat: add currency strategy`, `fix: calculation rounding`, `docs: update readme`).
    * **Pull Requests (Simulação):** Mesmo trabalhando sozinho, abra Pull Requests (PRs) para mergear suas features na branch principal. Descreva no PR o que foi feito.
    * **Histórico Limpo:** Demonstre controle sobre o histórico, evitando commits de merge desnecessários ou poluídos.
* **Entregáveis (Acumulativo):**
    * Uso correto de **Docker** e **Docker Compose** para orquestrar a aplicação e o banco.
    * Tratamento de erros global (Exception Handlers).
    * Validações de input robustas (segurança).
    * Testes Unitários cobrindo as regras de precificação (Strategy).

### 🔴 Nível Sênior
* **Foco:** Observabilidade, Escalabilidade e Automação.
* **Git & Versionamento (Acumulativo):**
    * **Git Hooks:** Configure ferramentas (como Husky, Pre-commit) para rodar linters ou testes unitários antes do commit/push.
    * **Semantic Versioning (Tags):** Utilize **Tags** do Git para marcar a entrega da versão final (ex: `v1.0.0`).
    * **Interactive Rebase:** Uso de `rebase` para organizar commits antes do merge (squash de commits de fix pequenos, reordenação de lógica) mantendo uma linearidade profissional.
* **Entregáveis (Acumulativo):**
    * **Diagrama C4 (Nível 1 e 2):** Diagrama de Contexto e Container da solução.
    * **Observabilidade:** Logs estruturados, Métricas (ex: Prometheus/Grafana) ou Tracing.
    * **CI/CD:** Pipeline (GitHub Actions ou similar) rodando testes e linter.
    * **Resiliência:** Retries ou Circuit Breaker em chamadas externas.
    * **Concorrência:** Optimistic Locking para evitar conflito de liquidação.

### 🟣 Nível Especialista / Staff / Principal
* **Foco:** Arquitetura Distribuída, Governança e Gestão de Crise.
* **Git & Versionamento (Acumulativo):**
    * **Estratégia de Branching:** No README, defina e justifique qual fluxo escolheu (Git Flow, Trunk Based, GitHub Flow) e por que ele se adequa a este projeto.
    * **Simulação de Gestão de Crise:**
        * Crie uma situação onde um bug crítico foi para a `main`.
        * Demonstre o uso de `git revert` para desfazer a alteração de forma segura.
        * Ou demonstre um `git cherry-pick` simulando a aplicação de um hotfix em produção.
* **Entregáveis (Acumulativo):**
    * **ADR (Architecture Decision Records):** Documente as decisões difíceis (ex: SQL vs NoSQL, Monolito vs Microserviços).
    * **Design de Alta Escala:** No README, descreva arquitetura para **1 milhão de transações/minuto** (Caching, Sharding, Consistência Eventual).
    * **IaC:** (Opcional) Terraform ou Kubernetes manifests.
    * **Modelagem de Eventos:** Proposta de arquitetura EDA.

---

## 7. Modelagem de Dados e Scripts

Independente da ferramenta de migração (Flyway, Liquibase, etc) ou ORM, forneça no README ou numa pasta `/docs`:
1.  **Diagrama ER:** Mostrando relacionamentos entre Moedas, Produtos (Tipos de Recebíveis), Transações e Taxas.
2.  **Scripts DDL:** SQL necessário para criar a estrutura do banco.

---

## 8. Critérios de Avaliação

1.  **Fundamentação Teórica:** Capacidade de justificar a escolha da linguagem e das bibliotecas.
2.  **Design de Código:** Aderência a princípios SOLID, DRY e KISS.
3.  **Domínio do Git:** O histórico do repositório conta uma história? Os commits são rastreáveis? Você demonstra controle sobre o versionamento?
4.  **Domínio do Negócio:** Entendimento de como a modelagem de dados reflete o problema financeiro (precisão numérica, segurança transacional).
5.  **Uso da IA:** A IA foi usada para potenciar a engenharia ou para mascarar falta de conhecimento?
6.  **Maturidade de System Design:** (Para Sênior+) A arquitetura proposta aguenta o tranco em produção? É segura? É observável?

---

## 9. Entrega

1.  Repositório público (GitHub/GitLab).
2.  Prazo: **3 a 4 dias úteis** (ajustável conforme complexidade entregue).
3.  O README é a "cara" do seu projeto: Capriche na documentação de setup, design e decisões.

**Boa sorte! Mostre-nos como você constrói o futuro do mercado de crédito.**