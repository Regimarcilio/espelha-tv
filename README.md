# Espelha-TV

Transmissão da tela de um PC para o navegador de uma TV Smart, em rede local fechada.
O PC roda um servidor Java; a TV abre uma página e recebe vídeo **MJPEG** por HTTP.
O controle de mouse/teclado é opcional, exclusivo de um dispositivo por vez.

**Status:** MVP `1.0.0` implementado, com 52 testes automatizados, smoke test HTTP e E2E em Chromium.

## Destaques

- **Java 21 + Maven**, sem dependências de runtime além do JDK (`com.sun.net.httpserver`).
- **MJPEG** com `<img src="/stream">`: máxima compatibilidade com webOS/Tizen antigos.
- **Modo espectador por padrão**; controle com token efêmero e um único controlador.
- **Modo demo** (`--demo`) para validar TV, rede e interface sem capturar a tela real.
- Sessão por cookie `HttpOnly`/`SameSite=Strict`; login com rate limit; CSRF em POST.
- Reconexão automática do stream; `releaseAll` ao liberar, expirar ou fechar o stream.
- Configuração por CLI/env; token gerado automaticamente se não for informado.

## Requisitos

- PC com **Java 21** (Temurin recomendado) e **Maven 3.9+** para compilar.
- TV e PC na **mesma LAN**. Não exponha a porta na internet.
- TV com navegador HTML5 e suporte a MJPEG (webOS 3+, Tizen 2016+; testar o modelo alvo).
- Para captura real: sessão gráfica X11 (ou XWayland). Em Wayland puro, use `--demo` ou uma sessão X11.

## Quick start

### 1. Toolchain local (sem sudo)

```bash
./scripts/bootstrap-toolchain.sh   # baixa JDK 21 + Maven em .toolchain/
./scripts/build.sh                 # mvn clean verify + JAR
```

O `.toolchain/` é ignorado pelo Git. Se já tiver Java 21/Maven no sistema, pode usá-los
diretamente; `scripts/env.sh` prioriza o toolchain local.

### 2. Validar sem capturar a tela (recomendado)

```bash
./scripts/run.sh --demo --token=demo
```

Abra no PC: `http://127.0.0.1:8080`. Na TV, use `http://IP-DO-PC:8080`.
O modo demo gera uma tela sintética animada e não injeta input real.

Smoke test automatizado (health, login, claim, input, MJPEG e logout):

```bash
./scripts/smoke-demo.sh
```

### 3. Executar com captura real

```bash
./scripts/run.sh --token="uma-senha-forte"
```

O servidor imprime a URL e o token (se gerado). Informe o token na tela de login da TV.

## Acesso pela TV

1. Descubra o IPv4 do PC:
   ```bash
   hostname -I
   ```
2. No navegador da TV, abra `http://IP-DO-PC:8080`.
3. Digite o token e toque em **Entrar**.
4. Para controlar o PC, toque em **Assumir controle** (um dispositivo por vez).
5. Use **Tela cheia** para ocupar a tela; **Sair** encerra a sessão.

Se a TV não abrir, confirme que o firewall libera a porta **apenas na LAN**:

```bash
# Linux/ufw
sudo ufw allow from 192.168.0.0/16 to any port 8080 proto tcp

# Windows/PowerShell (admin)
New-NetFirewallRule -DisplayName "Espelha-TV" -Direction Inbound `
  -Protocol TCP -LocalPort 8080 -RemoteAddress LocalSubnet -Action Allow
```

## Controles

| Ação | Tecla/botão |
|---|---|
| Mover mouse | Setas do controle remoto |
| Clicar | Enter |
| Liberar controle | Escape (ou botão **Liberar controle**) |
| Texto | Botão **Teclado** e campo de texto (ASCII, até 128) |
| Tela cheia | Botão **Tela cheia** / F11 quando disponível |
| Voltar | Escape no overlay; o botão Voltar da TV pode sair da página |

O controle expira após **300 s sem input** (configurável). Ao fechar o stream do
controlador, o servidor aguarda **3 s** de reconexão e libera o controle.

## Configuração

| CLI | Env | Padrão | Descrição |
|---|---|---|---|
| `--port` | `ESPELHATV_PORT` | `8080` | Porta HTTP |
| `--bind` | `ESPELHATV_BIND` | `0.0.0.0` | Interface de escuta |
| `--token` | `ESPELHATV_TOKEN` | gerado | Token de acesso |
| `--fps` | — | `10` | FPS de captura (1–30) |
| `--width` | — | `1280` | Largura máxima do frame (320–3840) |
| `--height` | — | `720` | Altura máxima do frame (240–2160) |
| `--quality` | — | `0.60` | Qualidade JPEG (0.10–1.00) |
| `--control-timeout` | — | `300` | Segundos sem input para liberar |
| `--session-ttl` | — | `720` | Minutos de vida da sessão |
| `--cursor` / `--no-cursor` | — | `true` | Desenha o cursor na captura |
| `--screen` | — | `0` | Monitor (MVP: primário; outros são ignorados) |
| `--demo` | — | `false` | Fonte sintética, sem captura real |
| `--help` | — | — | Ajuda |
| — | `ESPELHATV_SCREENSHOT_METHOD` | `x11` em Wayland | Backend do `Robot` (`x11` ou `dbusScreencast`) |

Exemplo:

```bash
java -jar target/espelha-tv.jar \
  --port=8080 --fps=8 --width=1280 --height=720 --quality=0.55 \
  --control-timeout=300 --token="minha-senha"
```

## API HTTP

| Método | Rota | Auth | Descrição |
|---|---|---|---|
| GET | `/health` | não | Status, versão e uptime (sem segredos) |
| GET | `/` | cookie | Página principal ou redireciona para `/login` |
| GET | `/login` | não | Página de login |
| GET | `/stream` | cookie | MJPEG `multipart/x-mixed-replace` |
| GET | `/api/state` | cookie | Estado da sessão/controle/stream |
| POST | `/api/login` | não | `token` (form) → cookie de sessão |
| POST | `/api/logout` | cookie + CSRF | Invalida sessão e libera controle |
| POST | `/api/control` | cookie + CSRF | `action=claim` ou `action=release` |
| POST | `/api/input` | cookie + CSRF + control token | Mouse, teclado, texto |
| POST | `/api/heartbeat` | cookie + CSRF + control token | Renova atividade do controle |

POST autenticado exige `X-EspelhaTV: 1` e `Content-Type: application/x-www-form-urlencoded`.
O stream usa boundary `frame`; cada parte tem `Content-Type: image/jpeg` e `X-Sequence`.

## Testes e qualidade

```bash
./scripts/build.sh          # mvn clean verify
mvn test                    # somente testes
```

- **52 testes** JUnit 5: config, auth/sessão, controle, input, captura, JPEG, FrameHub,
  API HTTP e ciclo de vida.
- **Cobertura JaCoCo:** ~71% de linhas (inclui classes de plataforma não exercitadas no
  headless, como `Robot*` e `Main`); o build exige mínimo de 70% de linhas.
- **Smoke test:** `./scripts/smoke-demo.sh` valida o fluxo ponta a ponta com `curl`.
- **E2E manual/Playwright:** login, stream 1280×720, claim, setas/Enter, Escape, teclado,
  logout e ausência de erros de console foram validados em Chromium headless.

## Estrutura

```text
src/main/java/br/com/espelhatv/
  Main.java                  entrypoint e banner
  config/AppConfig.java      CLI/env e validação
  auth/                      token, sessões e cookie
  control/ControlManager.java controlador único + timeout
  input/                     KeyMapper, InputController, Robot/Noop
  capture/                   ScreenSource, FrameScaler, JPEG, FrameHub, CaptureService
  server/                    HttpApi, StaticFiles, AppServer
  util/NetworkInfo.java      IPv4 da LAN
src/main/resources/web/      login, app, CSS, JS e favicon
docs/                        SRS, arquitetura/UML, ADRs, backlog, entrega, testes, runbook
scripts/                     bootstrap, build, run, smoke
```

## Segurança e privacidade

- Transporte **HTTP sem TLS** no MVP: use apenas em LAN confiável/VLAN isolada; nunca
  faça port forwarding no roteador.
- Token de acesso comparado em tempo constante; nunca é logado nem retornado pela API.
- Cookie de sessão opaco, `HttpOnly`, `SameSite=Strict`, TTL deslizante.
- CSP restritiva, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`.
- Sem CORS; POST autenticado exige header CSRF; login com rate limit por IP.
- Nada é gravado em disco; frames existem apenas em memória.

## Limitações conhecidas

- **Wayland:** captura via XWayland/X11; em alguns desktops a imagem pode sair preta.
  `ESPELHATV_SCREENSHOT_METHOD=dbusScreencast` é experimental e pode bloquear aguardando
  permissão do portal. Prefira sessão X11 ou `--demo`.
- **Áudio:** fora do MVP.
- **DRM/proteção de conteúdo, UAC, lock screen e algumas janelas nativas** podem aparecer
  pretas ou não ser capturáveis.
- **Múltiplos monitores:** apenas o primário.
- **Navegadores de TV antigos:** MJPEG é o caminho compatível; codecs via MSE não são usados.
- **Botão Voltar/Home da TV** pode sair da página; um app webOS/Tizen empacotado é evolução.

## Documentação

- [`docs/SRS.md`](docs/SRS.md) — requisitos, regras e rastreabilidade.
- [`docs/03-arquitetura-e-uml.md`](docs/03-arquitetura-e-uml.md) — arquitetura, UML e API.
- [`docs/04-ADRs/`](docs/04-ADRs/) — decisões (MJPEG, HttpServer, controlador único, cookie).
- [`docs/05-backlog.md`](docs/05-backlog.md) — épicos, histórias, sprints e DoD.
- [`docs/06-plano-de-entrega.md`](docs/06-plano-de-entrega.md) — branches, build, firewall e release.
- [`docs/07-testes-e-validacao.md`](docs/07-testes-e-validacao.md) — evidências de teste.
- [`docs/08-runbook.md`](docs/08-runbook.md) — operação e troubleshooting.

> Use apenas em rede local confiável. O MVP não implementa TLS.
