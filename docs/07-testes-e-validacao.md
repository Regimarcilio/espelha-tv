# 07 — Testes e Validação

Evidências da versão **1.0.0**, executadas em **2026-10-23** no PC de desenvolvimento
(Linux, sessão Wayland com XWayland, JDK 21.0.13, Maven 3.9.16).

## 1. Resumo

| Camada | Resultado |
|---|---|
| Testes automatizados | **52 testes, 0 falhas, 0 erros** |
| Cobertura de linhas | **70,99%** (935/1317) — gate de 70% atendido |
| Smoke HTTP | **OK** (health, login, state, claim, input, MJPEG, logout) |
| E2E de interface | **OK** em Chromium headless (demo) |
| Captura real | Fluxo HTTP OK; **imagem preta no Wayland/XWayland** (limitação documentada) |
| TV física | **Não validada** (sem acesso ao modelo alvo) |

## 2. Testes automatizados

Comando:

```bash
./scripts/build.sh
# equivalente a: mvn -B clean verify
```

Saída relevante:

```text
Tests run: 52, Failures: 0, Errors: 0, Skipped: 0
All coverage checks have been met.
BUILD SUCCESS
```

| Classe | Testes | Cobertura principal |
|---|---:|---|
| `AppConfigTest` | 5 | defaults, CLI, env, validação, `--help` |
| `AuthServiceTest` | 4 | token correto/errado, rate limit, janela |
| `SessionStoreTest` | 4 | criação, TTL deslizante, remoção, cleanup |
| `ControlManagerTest` | 6 | claim exclusivo, release, timeout, idle, `releaseBySession` |
| `KeyMapperTest` | 4 | teclas suportadas e desconhecidas |
| `InputControllerTest` | 7 | mouse, clique, roda, teclado, texto, ranges inválidos |
| `JpegEncoderTest` | 2 | JPEG válido e dimensões |
| `FrameScalerTest` | 4 | downscale, proporção, sem upscale |
| `FrameHubTest` | 3 | subscribe/publish/unsubscribe e última moldura |
| `CaptureServiceTest` | 2 | captura periódica e erro da fonte |
| `HttpApiIntegrationTest` | 10 | login, cookie, CSRF, claim 409, input 204, stream, heartbeat, disconnect, logout |
| `NetworkInfoTest` | 1 | descoberta de IPv4 |
| **Total** | **52** | |

Apoio: `MutableClock` e `RecordingInputSink` (dublês determinísticos).

### Cobertura JaCoCo

```text
INSTRUCTION  4068/5691  71,48%
BRANCH        345/610   56,56%
LINE          935/1317  70,99%
COMPLEXITY    267/532   50,19%
METHOD        142/195   72,82%
```

O relatório inclui `Main` e `Robot*`, que não são exercitados no build headless.
O gate configurado no `pom.xml` exige **70% de linhas** em `verify`.

## 3. Smoke test HTTP

```bash
./scripts/smoke-demo.sh
```

Saída:

```text
OK: health, login, estado, claim, input, stream MJPEG e logout.
```

O script sobe o JAR em `--demo`, porta aleatória, e valida com `curl`:
`/health`, `/api/login`, `/api/state`, `/api/control?action=claim`, `/api/input`,
`/stream` (primeiro frame JPEG) e `/api/logout`.

## 4. E2E de interface (demo)

Ferramenta: Playwright + Chromium headless (`chromium-1248`), servidor demo em
`127.0.0.1:18080`.

Fluxo validado:

1. `/` sem sessão redireciona para `/login`.
2. Login com token → `/`; cookie de sessão presente.
3. `<img id="screen">` carrega frame **1280×720**.
4. **Assumir controle** → estado `Controle ativo`, overlay visível.
5. Teclas **seta** geram `type=move`; **Enter** gera `click` down/up.
6. **Escape** libera → `Espectador`; botão **Assumir controle** reaparece.
7. Modal de teclado envia `type=text&text=Ola%20TV`.
8. **Sair** encerra a sessão e volta para `/login`.
9. Console do navegador: **nenhum erro**.

Requisições observadas (extrato):

```text
token=...&type=move&x=0.5000&y=0.4990
token=...&type=click&button=1&down=true
token=...&type=click&button=1&down=false
token=...&type=text&text=Ola%20TV
```

> O teste E2E usa ferramenta temporária fora do repositório; o smoke HTTP é a
> verificação versionada e reproduzível no CI.

## 5. Captura real (não-demo)

Ambiente: `DISPLAY=:0`, `WAYLAND_DISPLAY=wayland-0`, tela 1920×1080.

```bash
java -jar target/espelha-tv.jar --port=18081 --bind=127.0.0.1 --token=real
curl -b cookie.txt --max-time 5 http://127.0.0.1:18081/stream -o /tmp/stream.bin
```

Resultado:

| Métrica | Valor |
|---|---|
| Bytes recebidos em 5 s | 319.149 |
| Partes MJPEG | 21 |
| Primeiro JPEG | 15.120 bytes, 1280×720 |
| Luminância média | **0,02** (imagem preta) |
| Erro de captura | nenhum |

Conclusões:

- O servidor **não trava** ao capturar em Wayland quando o backend `x11` é usado.
- O XWayland entrega uma superfície preta nesta sessão; não é possível validar
  visualmente a tela real sem sessão X11 nativa ou portal funcional.
- A tentativa anterior com `dbusScreencast` bloqueou aguardando permissão; por isso
  o padrão passou a ser `x11` e o backend é sobrescrevível por
  `ESPELHATV_SCREENSHOT_METHOD`.
- Taxa observada ≈4 fps no cenário preto (1920×1080 → 1280×720), abaixo dos 10 fps
  configurados; **performance real não foi homologada**.

## 6. Matriz de aceite

| Requisito | Evidência | Status |
|---|---|---|
| RF-001 iniciar/health | `AppConfigTest`, `HttpApiIntegrationTest`, smoke | OK |
| RF-002 login | `AuthServiceTest`, `HttpApiIntegrationTest`, E2E | OK |
| RF-003 espectador | `SessionStoreTest`, E2E | OK |
| RF-004 MJPEG | `HttpApiIntegrationTest`, smoke, E2E | OK |
| RF-005 estado | `HttpApiIntegrationTest`, smoke, E2E | OK |
| RF-006 controle exclusivo | `ControlManagerTest`, `HttpApiIntegrationTest` | OK |
| RF-007 mouse | `InputControllerTest`, E2E (demo) | OK (demo); input real não exercitado |
| RF-008 teclado | `KeyMapperTest`, E2E (demo) | OK (demo); input real não exercitado |
| RF-009 texto | `InputControllerTest`, E2E (demo) | OK |
| RF-010 liberar/timeout | `ControlManagerTest`, E2E | OK |
| RF-011 release all | `InputControllerTest`, `ControlManagerTest`, disconnect 3 s | OK |
| RF-012 reconexão | E2E + teste de disconnect | OK |
| RF-013 logout | `HttpApiIntegrationTest`, E2E | OK |
| RF-014 demo | `HttpApiIntegrationTest`, smoke, E2E | OK |
| RNF segurança | headers, CSRF, rate limit, cookie, revisão manual | Parcial (SAST formal pendente) |
| RNF compatibilidade TV | Chromium 16:9 | Pendente (TV física não testada) |
| RNF performance | captura real ≈4 fps | Pendente (não homologado) |

## 7. Lacunas e próximos testes

1. **TV física**: login, MJPEG, fullscreen, setas/Enter, teclado USB e botão Voltar.
2. **X11 nativo**: confirmar captura visual e latência; comparar 720p/1080p.
3. **Input real**: validar `RobotInputSink` em ambiente controlado (mouse/teclado reais).
4. **Soak test**: 2 h com 1 espectador + 1 controlador; memória, reconexões e vazamentos.
5. **Segurança**: SAST/DAST formal; teste de força bruta e headers em proxy.
6. **Performance**: medir captura + escala + JPEG + rede em 720p e 1080p.
7. **Reconexão do controlador**: fechar/abrir stream dentro da carência de 3 s.
