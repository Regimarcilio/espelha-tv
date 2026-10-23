# 06 — Plano de Entrega

## 1. Estratégia de branches

```text
main        ●──────────────────────────────● release
             \                            /
develop       ●──●──●──●──●──●──●──●──●──●
                 \     /       \   /
feature/*         ●───●         ●─●
```

- `main`: versões estáveis.
- `develop`: integração contínua (branch padrão de trabalho).
- `feature/<slug>`: uma história por branch; PR para `develop`.
- `release/<versão>`: estabilização; merge em `main` e `develop`.
- Commits: Conventional Commits (`feat(server): ...`, `fix(stream): ...`, `docs(plan): ...`, `test: ...`).

## 2. Build e execução

### 2.1 Com toolchain do sistema
```bash
sudo apt install openjdk-21-jdk maven
mvn clean package
java -jar target/espelha-tv.jar --token="minha-senha-forte"
```

### 2.2 Com toolchain local (sem sudo)
```bash
./scripts/bootstrap-toolchain.sh
./scripts/build.sh
./scripts/run.sh --token="minha-senha-forte"
```

### 2.3 Modo demo (sem captura de tela)
```bash
./scripts/run.sh --demo --token="demo"
# abrir http://IP-DO-PC:8080 na TV
```

### 2.4 Empacotamento nativo (opcional)
```bash
jpackage --type app-image --name EspelhaTV \
  --input target --main-jar espelha-tv.jar \
  --main-class br.com.espelhatv.Main
```

## 3. Rede e firewall

- PC e TV na mesma LAN; IP do PC fixo ou reserva DHCP.
- Liberar a porta somente na LAN:
```bash
# Linux (ufw)
sudo ufw allow from 192.168.0.0/16 to any port 8080 proto tcp

# Windows (PowerShell como admin)
New-NetFirewallRule -DisplayName "Espelha-TV" -Direction Inbound `
  -Protocol TCP -LocalPort 8080 -RemoteAddress LocalSubnet -Action Allow
```
- **Nunca** fazer port forwarding no roteador.
- Ideal: VLAN isolada para IoT/TV.

## 4. CI

O workflow versionado em `.github/workflows/ci.yml` roda em `push`/`pull_request`
para `develop` e `main`:

- checkout;
- Temurin 21 com cache Maven;
- `mvn -B clean verify` (52 testes + gate JaCoCo de 70% de linhas);
- upload do relatório `target/site/jacoco/`.

## 5. Checklist anti-retrabalho

- [x] SRS e AC revisados antes de codar.
- [x] Contrato de API congelado (rotas/campos) antes do frontend.
- [x] Teste de stream com `--demo` em navegador real cedo.
- [ ] Teste na TV alvo antes de otimizar qualidade.
- [x] `releaseAll` coberto por teste.
- [x] Nenhum token/segredo no git; `.gitignore` cobre `target/` e `.toolchain/`.
- [x] Firewall documentado; sem exposição à internet.
- [x] Limitações (Wayland, UAC, DRM, áudio) escritas no README.
- [x] Documentação atualizada no mesmo commit da mudança.

## 6. Roadmap pós-MVP

| Versão | Entrega |
|---|---|
| 1.1 | WebSocket para input; métricas de FPS/latência; ajuste de qualidade na UI |
| 1.2 | H.264/MSE opcional; áudio; seleção de monitor |
| 1.3 | App webOS/Tizen empacotado (`.ipk`/`.wgt`) com fullscreen e teclas nativas |
| 2.0 | TLS, autenticação por usuário, múltiplas sessões/contas, auditoria |

## 7. Critérios de release

- `mvn clean verify` verde.
- Teste manual em pelo menos uma TV real (ou navegador Chromium simulando viewport 16:9).
- `/health` ok; login; stream; espectador sem input; claim; input; release; timeout.
- README e docs atualizados.
- Tag `v1.0.0` em `main` após merge de `release/1.0.0`.

## 8. Status da entrega (v1.0.0)

| Item | Status |
|---|---|
| Código e JAR | Pronto (`mvn clean verify`, 52 testes) |
| Cobertura | 70,99% de linhas; gate de 70% ativo |
| Demo + smoke HTTP | Validados |
| UI em Chromium 1280×720 | Validada (login, claim, input, teclado, logout) |
| Captura real X11 | Pendente de sessão X11 nativa |
| Wayland | Captura preta via XWayland; documentado |
| TV física | Pendente |
| SAST (Semgrep) | 0 achados em 167 regras |
| DAST baseline (ZAP) | 0 fail, 3 warn; pentest autenticado pendente |
| Tag `v1.0.0` | Pendente de homologação em TV real |

Comandos de fechamento:

```bash
./scripts/build.sh
./scripts/smoke-demo.sh
git add .
git commit -m "feat: entrega v1.0.0 do Espelha-TV"
```
