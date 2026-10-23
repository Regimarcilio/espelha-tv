# 07 — Testes e Validação

Evidências da versão **1.0.0**, executadas em **2026-10-23** no PC de desenvolvimento
(Linux, sessão Wayland com XWayland, JDK 21.0.13, Maven 3.9.16).

## 1. Resumo

| Camada | Resultado |
|---|---|
| Testes automatizados | **53 testes, 0 falhas, 0 erros** |
| Cobertura de linhas | **70,66%** (956/1353) — gate de 70% atendido |
| Smoke HTTP | **OK** (health, login, state, claim, input, MJPEG, logout) |
| E2E de interface | **OK** em Chromium headless (demo) |
| DAST baseline (ZAP) | **0 FAIL**, 3 WARN, 65 PASS |
| Captura real | Fluxo HTTP OK; **imagem preta no Wayland/XWayland** (limitação documentada) |
| TV física | Página demo carregou; stream e controles pendentes |

## 2. Testes automatizados

Comando:

```bash
./scripts/build.sh
# equivalente a: mvn -B clean verify
```

Saída relevante:

```text
Tests run: 53, Failures: 0, Errors: 0, Skipped: 0
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
| `NetworkInfoTest` | 2 | IPv4 da LAN e filtro de interfaces virtuais |
| **Total** | **53** | |

Apoio: `MutableClock` e `RecordingInputSink` (dublês determinísticos).

### Cobertura JaCoCo

```text
INSTRUCTION  4157/5829  71,32%
BRANCH        361/654   55,20%
LINE          956/1353  70,66%
COMPLEXITY    272/556   48,92%
METHOD        144/197   73,10%
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

### 4.1 Conectividade em TV física

Em 2026-10-23 o modo demo foi aberto no navegador de uma TV física da rede
`192.168.0.0/24` via `http://192.168.0.108:8090` e a página carregou. O modelo da TV
não foi registrado. Login, stream, controles e teclado na TV ainda precisam de
homologação formal.

O servidor nativo estava bloqueado pelo UFW (`DEFAULT_INPUT_POLICY=DROP`). Para o
teste, a porta foi publicada via Docker (`-p 8090:8090`), que usa a cadeia FORWARD
do Docker e não passa pelo INPUT do UFW. A solução permanente é liberar a porta na
LAN com `ufw allow from 192.168.0.0/24 to any port 8090 proto tcp`.

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

## 6. SAST e dependências

Semgrep 1.181.1 em container oficial, regras `p/java`, `p/secrets` e
`p/owasp-top-ten`:

```bash
docker run --rm -v "$PWD:/src" -w /src semgrep/semgrep:latest \
  semgrep scan --metrics=off --config p/java --config p/secrets \
  --config p/owasp-top-ten src scripts pom.xml
```

Resultado: **0 achados** em 36 arquivos e 167 regras executadas, incluindo Java,
JavaScript, HTML e shell. Não há dependências de runtime; a árvore Maven contém
somente JUnit 5 em escopo `test`.

Ressalva: Semgrep é SAST estático e não substitui DAST, teste de intrusão nem
revisão manual de lógica de autorização.

## 7. DAST baseline (OWASP ZAP)

Servidor demo em `127.0.0.1:18080`, scan passivo/ativo baseline do ZAP em container:

```bash
docker run --rm --network host -v /tmp/zap:/zap/wrk:rw zaproxy/zap-stable:latest \
  zap-baseline.py -t http://127.0.0.1:18080 -m 2 -J report.json -r report.html
```

Resultado: **0 FAIL**, **3 WARN**, **65 PASS** em 8 URLs.

| Alerta | Avaliação | Ação |
|---|---|---|
| Sensitive Information in URL (10024) | O ZAP injetou `?token=ZAP`; o app não lê token da query e agora responde 302 removendo a query | Sem segredo real; manter a limpeza |
| Non-Storable Content (10049) | `no-store`/redirects intencionais para sessão e API | Aceito por design |
| Modern Web Application (10109) | Informativo (SPA/HTML5) | Nenhuma |

Hardening aplicado após o primeiro scan: `Cross-Origin-Opener-Policy`,
`Cross-Origin-Embedder-Policy`, `Cross-Origin-Resource-Policy` e remoção de query
na rota `/login`. O E2E em Chromium foi repetido após a mudança (stream, claim,
input, teclado e logout continuam funcionando).

O baseline não cobre fluxos autenticados de input/claim nem CSRF por POST; esses
casos continuam cobertos pelos testes de integração e revisão manual.

## 8. Matriz de aceite

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
| RNF segurança | headers, CSRF, rate limit, cookie, Semgrep 0 achados, ZAP 0 fail | OK no SAST/DAST baseline; pentest pendente |
| RNF compatibilidade TV | Chromium 16:9 + página demo em TV física | Parcial (stream/controles na TV pendentes) |
| RNF performance | captura real ≈4 fps | Pendente (não homologado) |

## 9. Lacunas e próximos testes

1. **TV física**: login, MJPEG, fullscreen, setas/Enter, teclado USB e botão Voltar.
2. **X11 nativo**: confirmar captura visual e latência; comparar 720p/1080p.
3. **Input real**: validar `RobotInputSink` em ambiente controlado (mouse/teclado reais).
4. **Soak test**: 2 h com 1 espectador + 1 controlador; memória, reconexões e vazamentos.
5. **Segurança**: pentest/DAST autenticado; teste de força bruta e headers em proxy.
6. **Performance**: medir captura + escala + JPEG + rede em 720p e 1080p.
7. **Reconexão do controlador**: fechar/abrir stream dentro da carência de 3 s.
