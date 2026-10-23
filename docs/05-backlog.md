# 05 — Backlog Scrum

## 1. Épicos

| ID | Épico | Objetivo |
|---|---|---|
| E1 | Fundação | Projeto Maven, configuração, servidor HTTP, health |
| E2 | Stream | Captura, escala, JPEG, MJPEG, FrameHub |
| E3 | Controle | Sessões, auth, controlador único, input, release |
| E4 | UX TV | Login, tela de visualização, controle remoto, teclado |
| E5 | Qualidade | Testes, segurança, documentação, entrega |

## 2. User Stories

### E1 — Fundação

**US-01 — Configuração por CLI/env** (3 pts)  
Como operador, quero configurar porta, token, FPS e resolução sem editar código.  
AC: `--help` lista opções; CLI > env > default; valores inválidos falham com mensagem; token gerado é impresso uma vez.

**US-02 — Servidor HTTP e health** (3 pts)  
Como operador, quero subir o servidor e verificar `/health`.  
AC: `GET /health` → 200 `ok`; bind/porta configuráveis; shutdown gracioso.

**US-03 — Sessão e login** (5 pts)  
Como usuário, quero entrar com um token e receber um cookie de sessão.  
AC: login válido cria cookie `HttpOnly`/`SameSite=Strict`; inválido → 401; 11 tentativas/min → 429; logout invalida.

### E2 — Stream

**US-04 — Captura e encode** (5 pts)  
Como sistema, quero capturar a tela, escalar e codificar JPEG.  
AC: `ScreenSource` abstrai `Robot`/demo; frame respeita `maxWidth`/`maxHeight`; qualidade configurável; cursor opcional.

**US-05 — MJPEG e FrameHub** (5 pts)  
Como espectador, quero receber frames contínuos.  
AC: `/stream` exige sessão; boundary/Content-Type corretos; fila de 1 frame por cliente; cliente lento não trava os demais.

**US-06 — Modo demo** (3 pts)  
Como operador, quero validar TV/rede sem desktop.  
AC: `--demo` inicia sem `Robot`; frames animados; input não tem efeito real.

### E3 — Controle

**US-07 — ControlManager** (5 pts)  
Como usuário, quero assumir o controle com exclusividade.  
AC: primeiro claim recebe token; segundo → 409; release libera; timeout libera; `releaseAll` ao liberar.

**US-08 — Input de mouse/teclado** (5 pts)  
Como controlador, quero mover mouse, clicar, rolar e digitar.  
AC: eventos válidos chamam `InputSink`; token inválido → 403; teclas mapeadas; release all em queda.

**US-09 — Texto** (2 pts)  
Como controlador, quero digitar texto pela TV.  
AC: texto ASCII ≤128; não ASCII ignorado; payload grande → 400.

**US-10 — Estado da sessão** (3 pts)  
Como cliente, quero saber se sou controlador e quantos espectadores há.  
AC: `/api/state` retorna JSON sem segredos; 401 sem sessão.

### E4 — UX TV

**US-11 — Página de login** (3 pts)  
Como usuário, quero uma tela simples para digitar o token.  
AC: funciona com controle remoto; erro visível; após login redireciona para `/`.

**US-12 — Tela de visualização** (5 pts)  
Como espectador, quero ver a tela em tela cheia com status.  
AC: `<img>` MJPEG; reconexão automática; botão fullscreen; layout legível a 3 m.

**US-13 — Assumir/liberar controle** (5 pts)  
Como usuário, quero assumir e liberar o controle com indicação clara.  
AC: botão "Assumir controle"; borda/indicador "CONTROLE ATIVO"; "Liberar"; mensagem 409; timeout refletido no estado.

**US-14 — Mapeamento do controle remoto** (5 pts)  
Como usuário, quero usar as setas e Enter da TV.  
AC: setas movem o mouse; Enter clica; Escape/Back libera (quando possível); teclado USB funciona; teclado virtual para texto.

### E5 — Qualidade

**US-15 — Testes automatizados** (8 pts)  
Como equipe, quero testes unitários e de integração.  
AC: `mvn test` verde; auth, controle, input, config, frames e API cobertos; teste de stream lê o primeiro JPEG.

**US-16 — Segurança e SAST** (3 pts)  
Como equipe, quero evitar segredos e falhas óbvias.  
AC: sem token hardcoded; CSP/headers; rate limit; SAST sem achados críticos.

**US-17 — Documentação e runbook** (5 pts)  
Como usuário/avaliador, quero instruções completas.  
AC: README, SRS, arquitetura, ADRs, backlog, plano de testes, runbook e troubleshooting.

## 3. Sprints sugeridas

| Sprint | Foco | Stories | Pontos |
|---|---|---|---|
| **Sprint 0** | Planejamento | SRS, UML, ADRs, backlog | — |
| **Sprint 1** | Fundação + stream | US-01..US-06 | 24 |
| **Sprint 2** | Controle + UX | US-07..US-14 | 33 |
| **Sprint 3** | Qualidade + entrega | US-15..US-17 | 16 |

Velocidade inicial estimada: **20–25 pts/sprint** (1 dev). Ajustar após o primeiro.

## 4. DoR — Definition of Ready

- História com objetivo, AC em Gherkin e dependências claras.
- Contrato de API ou interface definido.
- Riscos de TV/SO identificados.
- Critério de teste definido.

## 5. DoD — Definition of Done

- Código compila em Java 21 e `mvn test` passa.
- AC verificados por teste automatizado ou checklist manual documentado.
- Sem segredos no repositório; SAST sem achados críticos.
- Logs e mensagens de erro úteis.
- Documentação afetada atualizada.
- Commit em `develop` com Conventional Commits.

## 6. Riscos de sprint

| Risco | Sprint | Ação |
|---|---|---|
| Navegador da TV não renderizar MJPEG | 1 | Teste precoce com `--demo` |
| Wayland bloquear `Robot` | 1 | Detectar e documentar; testar no PC alvo |
| `HttpServer` e long-lived stream | 1 | Teste de integração com cliente real |
| Input preso | 2 | `releaseAll` + testes |
| Controle remoto mapear teclas diferentes | 2 | Teste manual; fallback USB |
| Banda em 1080p | 2 | Padrão 720p; qualidade configurável |

## 7. Status de execução (v1.0.0, 2026-10-23)

| História | Status | Evidência |
|---|---|---|
| US-01 Configuração | Concluída | `AppConfigTest` (5), `--help` |
| US-02 Servidor/health | Concluída | `HttpApiIntegrationTest`, smoke |
| US-03 Sessão/login | Concluída | `AuthServiceTest`, `SessionStoreTest`, smoke, E2E |
| US-04 Captura/encode | Concluída (demo) | `CaptureServiceTest`, `JpegEncoderTest`, `FrameScalerTest`; real limitado no Wayland |
| US-05 MJPEG/FrameHub | Concluída | `HttpApiIntegrationTest`, `FrameHubTest`, smoke, E2E |
| US-06 Modo demo | Concluída | `HttpApiIntegrationTest`, smoke, E2E |
| US-07 ControlManager | Concluída | `ControlManagerTest` (6) |
| US-08 Mouse/teclado | Concluída (demo) | `InputControllerTest`, `KeyMapperTest`, E2E; input real não exercitado |
| US-09 Texto | Concluída | `InputControllerTest`, E2E |
| US-10 Estado | Concluída | `HttpApiIntegrationTest`, smoke, E2E |
| US-11 Login UI | Concluída | E2E (login, erro, redirect) |
| US-12 Visualização | Concluída (Chromium) | E2E 1280×720 + reconexão; TV física pendente |
| US-13 Claim/release UI | Concluída | E2E, teste de disconnect com carência de 3 s |
| US-14 Controle remoto | Parcial | setas/Enter/Escape/teclado validados em Chromium; TV física pendente |
| US-15 Testes | Concluída | 52 testes, JaCoCo 70,99% de linhas |
| US-16 Segurança | Parcial | headers, CSP, CSRF, rate limit, cookie e revisão manual; SAST formal pendente |
| US-17 Documentação | Concluída | README, SRS, arquitetura, ADRs, 05–08 |

Sprints 0–3 concluídas para o escopo demo/desktop. Pendências de homologação:
TV física, sessão X11 nativa, input real, soak test, SAST/DAST e medição formal de
latência/FPS.
