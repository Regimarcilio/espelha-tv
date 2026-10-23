# SRS — Espelha-TV

**Versão:** 1.0  
**Status:** aprovado para o MVP  
**Última atualização:** 2026-10-23

## 1. Introdução

### 1.1 Propósito
Especificar o sistema **Espelha-TV**, que transmite a tela de um PC para o navegador de uma TV Smart em rede local fechada, com visualização por padrão e controle opcional de mouse/teclado.

### 1.2 Escopo
Servidor Java no PC, cliente web na TV, transporte MJPEG, autenticação por token, modo espectador, controle com trava de um único controlador, modo demo e testes automatizados. Não inclui áudio, Android na TV, WebRTC/H.264, TLS de produção ou acesso pela internet.

### 1.3 Definições
| Termo | Significado |
|---|---|
| Espectador | Sessão autenticada que só recebe vídeo; não envia input |
| Controlador | Sessão que recebeu o token de controle e pode enviar mouse/teclado |
| Frame | Imagem JPEG capturada, escalada e codificada |
| MJPEG | Stream HTTP `multipart/x-mixed-replace` com uma imagem JPEG por parte |
| Sessão | Cookie `HttpOnly` emitido após login, com TTL |
| Token de acesso | Segredo compartilhado para login |
| Token de controle | Segredo efêmero emitido ao assumir o controle |
| Release all | Liberar todas as teclas/botões pressionados no PC |

### 1.4 Referências
- `docs/01-visao-e-escopo.md`
- `docs/02-benchmarks.md`
- `docs/03-arquitetura-e-uml.md`
- OpenJDK `java.awt.Robot`; webOS TV Developer; Samsung TV Web Engine; Apache Guacamole.

## 2. Descrição Geral

### 2.1 Perspectiva
O sistema é composto por:
1. **Servidor Java** no PC: captura, codifica, serve HTTP, autentica, gerencia controle e injeta input.
2. **Cliente web** na TV: login, exibição do stream, botões de modo, teclado e mapeamento do controle remoto.
3. **Rede local** como único canal; não há cloud.

### 2.2 Usuários
- Usuário doméstico que quer ver o PC na TV.
- Professor/suporte que projeta a tela e eventualmente controla.
- Estudante que executa o projeto integrador.
- Operador que cede/retoma o controle.

### 2.3 Restrições
- Java 21 no PC; sem JVM na TV.
- Navegador da TV precisa suportar MJPEG via `<img>`; WebSocket/MSE são opcionais.
- `Robot` exige sessão gráfica ativa; Wayland tem limitações.
- Sem áudio no MVP.
- Um controlador por vez.
- Nenhum dado persistido em disco (exceto logs).

### 2.4 Premissas
- PC e TV na mesma LAN.
- PC ligado, desbloqueado e com sessão do usuário.
- Usuário autoriza o acesso à própria tela.
- Firewall libera a porta apenas na LAN.

## 3. Requisitos Funcionais

> Prioridade MoSCoW. Critérios de aceite em Gherkin.

### RF-001 — Login por token
- **Ator:** usuário na TV.
- **Descrição:** o sistema deve exigir um token de acesso para criar uma sessão.
- **Prioridade:** Must.
- **Regras:** token comparado em tempo constante; no máximo 10 tentativas por IP por minuto; sessão em cookie `HttpOnly`, `SameSite=Strict`, `Path=/`, TTL configurável (padrão 12 h).
- **Aceite:**
```gherkin
Cenário: login válido
  Dado que o servidor está no ar com token "ABC123"
  Quando a TV envia POST /api/login com token "ABC123"
  Então recebe 200 e um cookie de sessão
  E acessa GET / com 200

Cenário: token inválido
  Quando a TV envia POST /api/login com token "errado"
  Então recebe 401
  E não recebe cookie de sessão
```

### RF-002 — Servir cliente web
- **Ator:** usuário.
- **Descrição:** servir `login.html` sem sessão e `index.html` com sessão, além de `app.js`, `login.js`, `style.css` e `favicon.svg`.
- **Prioridade:** Must.
- **Aceite:** `GET /` sem cookie retorna a página de login; com cookie válido retorna a aplicação; assets têm `Content-Type` correto e não permitem path traversal.

### RF-003 — Stream MJPEG
- **Ator:** espectador/controlador.
- **Descrição:** servir `GET /stream` com `multipart/x-mixed-replace; boundary=frame`, uma parte JPEG por frame, somente para sessão autenticada.
- **Prioridade:** Must.
- **Aceite:**
```gherkin
Cenário: primeiro frame
  Dado que estou autenticado
  Quando abro GET /stream
  Então recebo 200 com Content-Type multipart/x-mixed-replace
  E o primeiro corpo começa com o boundary e contém um JPEG (FF D8 ... FF D9)
```

### RF-004 — Modo espectador
- **Ator:** usuário.
- **Descrição:** por padrão, a sessão só visualiza; nenhum evento de input é aceito.
- **Prioridade:** Must.
- **Aceite:**
```gherkin
Cenário: espectador não interfere
  Dado que estou autenticado e não assumi o controle
  Quando envio POST /api/input com qualquer evento
  Então recebo 403
  E nenhum evento chega ao InputSink
```

### RF-005 — Assumir controle
- **Ator:** usuário.
- **Descrição:** `POST /api/control` com `action=claim` concede um token de controle se não houver controlador.
- **Prioridade:** Must.
- **Aceite:**
```gherkin
Cenário: primeiro a assumir vence
  Dado que não há controlador
  Quando envio action=claim
  Então recebo 200 com token de controle
  E /api/state indica isController=true
```

### RF-006 — Trava de controlador único
- **Ator:** sistema.
- **Descrição:** uma segunda sessão que tentar assumir recebe `409` enquanto a primeira mantiver o controle.
- **Prioridade:** Must.
- **Aceite:**
```gherkin
Cenário: segundo controlador bloqueado
  Dado que a sessão A é controladora
  Quando a sessão B envia action=claim
  Então B recebe 409
  E A continua controladora
```

### RF-007 — Entrada de mouse, clique e roda
- **Ator:** controlador.
- **Descrição:** aceitar `move` (x/y normalizados), `moveRelative` (dx/dy), `click` (button/down) e `wheel` (delta), somente com token de controle válido.
- **Prioridade:** Must.
- **Aceite:** eventos válidos chamam o `InputSink`; token inválido/expirado retorna 403; coordenadas são limitadas à tela.

### RF-008 — Entrada de teclado
- **Ator:** controlador.
- **Descrição:** aceitar `key` com `code` (padrão `KeyboardEvent.code`) e `down`, mapeando para `KeyEvent.VK_*`.
- **Prioridade:** Must.
- **Aceite:** setas, Enter, Escape, Tab, Backspace, letras, dígitos e F1–F12 são mapeados; código desconhecido retorna 400 e não injeta nada. Teclas de mídia/volume não fazem parte do MVP.

### RF-009 — Teclado virtual / texto
- **Ator:** controlador.
- **Descrição:** modal com campo de texto; ao enviar, o servidor digita os caracteres ASCII no PC.
- **Prioridade:** Should.
- **Aceite:** texto de até 128 caracteres é aceito; caracteres não ASCII são ignorados pelo `RobotInputSink`; payload maior retorna 400.

### RF-010 — Liberar controle e timeout
- **Ator:** controlador/servidor.
- **Descrição:** `action=release` libera imediatamente; inatividade por 5 min (configurável) libera automaticamente.
- **Prioridade:** Must.
- **Aceite:**
```gherkin
Cenário: timeout libera
  Dado que sou controlador e não envio input por 300 s
  Quando o cleanup roda
  Então o controle é liberado
  E /api/state indica controllerActive=false
```

### RF-011 — Release all ao sair
- **Ator:** sistema.
- **Descrição:** ao liberar, expirar ou fechar o stream do controlador, liberar todas as teclas e botões pressionados. O fechamento do stream tem carência de 3 s para reconexão antes de liberar.
- **Prioridade:** Must.
- **Aceite:** após `key down` de Ctrl e queda de sessão/stream, o `InputSink` recebe `releaseAll`; se o stream reconectar dentro da carência, o controle é mantido.

### RF-012 — Fullscreen e reconexão
- **Ator:** usuário.
- **Descrição:** botão de fullscreen; o cliente tenta reconectar o stream a cada 2 s com backoff limitado.
- **Prioridade:** Should.
- **Aceite:** ao derrubar o servidor e subir de novo, a TV volta a exibir o stream sem recarregar a página.

### RF-013 — Estado da sessão
- **Ator:** cliente.
- **Descrição:** `GET /api/state` retorna autenticação, se a sessão é controladora, se há controlador, número de espectadores, resolução/FPS e timeout.
- **Prioridade:** Must.
- **Aceite:** JSON válido, sem segredos; `401` sem sessão.

### RF-014 — Modo demo
- **Ator:** operador.
- **Descrição:** `--demo` usa uma fonte sintética (gradiente animado) em vez de `Robot`, permitindo validar TV/rede sem desktop.
- **Prioridade:** Should.
- **Aceite:** `--demo` inicia sem `AWTException`, serve frames e permite controle (sem efeito real).

### RF-015 — Health
- **Ator:** operador.
- **Descrição:** `GET /health` retorna `ok` com versão e uptime, sem autenticação.
- **Prioridade:** Must.
- **Aceite:** `200` e corpo `ok`; não expõe token, IP ou dados sensíveis.

### RF-016 — Logout
- **Ator:** usuário.
- **Descrição:** `POST /api/logout` invalida a sessão e expira o cookie.
- **Prioridade:** Should.
- **Aceite:** após logout, `GET /api/state` retorna 401 e `/` volta ao login.

### RF-017 — Múltiplos espectadores
- **Ator:** sistema.
- **Descrição:** vários espectadores podem receber o mesmo frame; cada um tem fila de capacidade 1 (descarta frame antigo).
- **Prioridade:** Should.
- **Aceite:** com 3 espectadores, todos recebem frames; um cliente lento não trava os demais.

### RF-018 — Configuração
- **Ator:** operador.
- **Descrição:** CLI/env para porta, bind, token, FPS, resolução máxima, qualidade JPEG, timeout, TTL, cursor e demo.
- **Prioridade:** Must.
- **Aceite:** `--help` lista opções; valores inválidos falham com mensagem clara; CLI sobrepõe env.

### RF-019 — Expiração de sessão
- **Ator:** sistema.
- **Descrição:** sessões expiram por TTL; cleanup periódico remove expiradas e libera controle associado.
- **Prioridade:** Must.
- **Aceite:** sessão vencida recebe 401; controle dela é liberado.

### RF-020 — Rate limit de login
- **Ator:** sistema.
- **Descrição:** limitar tentativas por IP para dificultar brute force.
- **Prioridade:** Should.
- **Aceite:** 11ª tentativa em 60 s retorna 429; sucesso zera o contador.

## 4. Requisitos Não Funcionais

| ID | Categoria | Requisito | Critério |
|---|---|---|---|
| NFR-001 | Desempenho | 720p a 10 fps em LAN, latência perceptual 150–400 ms | Demo OK; captura real no Wayland ≈4 fps, homologação pendente |
| NFR-002 | Segurança | Token forte, cookie HttpOnly/SameSite, CSP, sem CORS, sem internet | Testes de auth/CSRF; SAST formal pendente |
| NFR-003 | Compatibilidade | Funcionar em webOS 3+ e Tizen 2016+ com MJPEG | Chromium 16:9 OK; TV física pendente |
| NFR-004 | Portabilidade | Windows e Linux X11; Wayland documentado | `Robot` no PC; demo no CI |
| NFR-005 | Usabilidade | Botões grandes, foco visível, setas/Enter mapeados | Teste com controle remoto |
| NFR-006 | Confiabilidade | Reconexão do stream; drop de frame; release all | Testes de integração e queda de rede |
| NFR-007 | Manutenibilidade | Java 21, sem dependências de runtime, testes JUnit 5 | `mvn test` verde; código modular |
| NFR-008 | Privacidade | Nada gravado; stream só com sessão; logs sem token | Revisão de código e logs |
| NFR-009 | Observabilidade | Logs de início, login, controle, erros; `/health` | Inspeção de log |
| NFR-010 | Acessibilidade | Contraste, foco visível, texto legível a 3 m | Inspeção visual |

## 5. Regras de Negócio

| ID | Regra |
|---|---|
| RN-001 | Existe no máximo um controlador ativo. |
| RN-002 | Espectador nunca tem input encaminhado, mesmo que envie payload. |
| RN-003 | Todo input exige token de controle válido e sessão autenticada. |
| RN-004 | Liberar/expirar/desconectar dispara `releaseAll`. |
| RN-005 | Sessão expira por TTL e não pode ser reutilizada. |
| RN-006 | O token de acesso nunca é logado nem retornado pela API. |
| RN-007 | O stream exige sessão; não há URL pública de vídeo. |
| RN-008 | Coordenadas de mouse são normalizadas (0..1); valores fora do intervalo são rejeitados com 400. |
| RN-009 | Payload de API é limitado a 4 KB; texto a 128 caracteres. |
| RN-010 | POST autenticado exige cabeçalho `X-EspelhaTV: 1` (CSRF/one-shot). |
| RN-011 | O controle é liberado após 300 s sem input (configurável). |
| RN-012 | O modo demo não injeta input real. |

## 6. Matriz de Rastreabilidade

| RF | RN | Testes |
|---|---|---|
| RF-001 | RN-006, RN-010 | `AuthServiceTest`, `HttpApiIntegrationTest` |
| RF-002 | — | `HttpApiIntegrationTest` |
| RF-003 | RN-007 | `HttpApiIntegrationTest`, `FrameHubTest` |
| RF-004 | RN-002, RN-003 | `ControlManagerTest`, `HttpApiIntegrationTest` |
| RF-005 | RN-001 | `ControlManagerTest` |
| RF-006 | RN-001 | `ControlManagerTest` |
| RF-007 | RN-008 | `InputControllerTest`, `KeyMapperTest` |
| RF-008 | RN-003 | `KeyMapperTest` |
| RF-009 | RN-009 | `InputControllerTest` |
| RF-010 | RN-011 | `ControlManagerTest` |
| RF-011 | RN-004 | `InputControllerTest`, `HttpApiIntegrationTest` |
| RF-012 | RN-006 | `HttpApiIntegrationTest` (manual) |
| RF-013 | RN-006 | `HttpApiIntegrationTest` |
| RF-014 | RN-012 | `HttpApiIntegrationTest`, `CaptureServiceTest` |
| RF-015 | RN-006 | `HttpApiIntegrationTest` |
| RF-016 | RN-005 | `HttpApiIntegrationTest` |
| RF-017 | RN-002 | `FrameHubTest` |
| RF-018 | — | `AppConfigTest` |
| RF-019 | RN-005 | `SessionStoreTest`, `ControlManagerTest` |
| RF-020 | — | `AuthServiceTest` |

## 7. Riscos e Mitigações

| Risco | Mitigação |
|---|---|
| Navegador da TV não renderiza MJPEG | Teste precoce com `--demo`; fallback para JPEG via fetch/canvas em evolução |
| Wayland bloqueia captura/input | Detectar `WAYLAND_DISPLAY`; avisar; recomendar X11 ou portal |
| CPU alta em 1080p | Padrão 720p/10 fps; escala e qualidade configuráveis |
| Tecla presa | `releaseAll` em release/timeout/erro/shutdown |
| Acesso não autorizado | Token forte, rate limit, LAN, sem port forward, logs |
| UAC/tela bloqueada | Documentar que não há captura/controle nessas telas |
| Back/Home da TV sai da página | Documentar; evoluir para app webOS/Tizen |

## 8. Status de implementação (v1.0.0)

Implementado e coberto por testes: RF-001 a RF-014 (demo), RNF-005 a RNF-009.
Pendências de homologação: captura visual em X11 nativo, TV física (RNF-003), input
real, performance (RNF-001) e SAST/DAST formal (NFR-002). Evidências em
[`07-testes-e-validacao.md`](07-testes-e-validacao.md).

## 9. Glossário

- **MJPEG:** sequência de imagens JPEG em um único stream HTTP.
- **Cookie de sessão:** identificador opaco, `HttpOnly`, usado para autenticar requisições.
- **Control token:** token efêmero que autoriza input; separado do token de acesso.
- **FrameHub:** componente que distribui o frame mais recente para os clientes.
- **Drop de frame:** descartar frames antigos quando o cliente está lento.
- **Release all:** liberar teclas/botões pressionados para evitar estado preso.
