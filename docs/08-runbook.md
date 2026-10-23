# 08 — Runbook de Operação

Procedimentos para subir, verificar, diagnosticar e encerrar o Espelha-TV em rede local.

## 1. Antes de começar

- PC e TV na **mesma LAN**.
- Porta escolhida (padrão `8080`) liberada **somente na LAN**.
- Token forte definido por `--token` ou `ESPELHATV_TOKEN`.
- Para captura real: sessão X11/XWayland funcional. Em Wayland puro, prefira `--demo`
  ou uma sessão X11.
- **Nunca** exponha a porta na internet: o transporte é HTTP sem TLS.

## 2. Build

```bash
./scripts/bootstrap-toolchain.sh   # primeira vez
./scripts/build.sh                 # mvn clean verify + JAR
```

Saída esperada: `BUILD SUCCESS`, 52 testes e `JAR: .../target/espelha-tv.jar`.

## 3. Subir o servidor

### Demo (validação de rede/TV)

```bash
./scripts/run.sh --demo --token="demo"
```

### Captura real

```bash
./scripts/run.sh --token="uma-senha-forte"
```

### Porta/resolução customizadas

```bash
./scripts/run.sh --port=9090 --fps=8 --width=1280 --height=720 \
  --quality=0.55 --control-timeout=300 --token="uma-senha-forte"
```

O processo roda em foreground; `Ctrl+C` encerra e libera a porta.

### systemd (opcional, usuário)

`~/.config/systemd/user/espelha-tv.service`:

```ini
[Unit]
Description=Espelha-TV
After=graphical-session.target

[Service]
Type=simple
Environment=ESPELHATV_TOKEN=troque-este-token
Environment=DISPLAY=:0
ExecStart=/home/SEU_USUARIO/PROJETOS/espelha-tv/scripts/run.sh --port=8080
Restart=on-failure
RestartSec=3

[Install]
WantedBy=default.target
```

```bash
systemctl --user daemon-reload
systemctl --user enable --now espelha-tv
journalctl --user -u espelha-tv -f
```

Atenção: serviços de usuário podem não ter acesso ao servidor gráfico após logout.

## 4. Verificação rápida

```bash
# 1. processo e porta
ss -ltnp | grep ':8080'

# 2. health
curl -fsS http://127.0.0.1:8080/health

# 3. login e estado
curl -c /tmp/espelha.cookie \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'token=SEU_TOKEN' http://127.0.0.1:8080/api/login
curl -b /tmp/espelha.cookie http://127.0.0.1:8080/api/state

# 4. stream (deve crescer)
curl -b /tmp/espelha.cookie --max-time 3 http://127.0.0.1:8080/stream -o /tmp/s.bin
ls -l /tmp/s.bin
```

Smoke automatizado:

```bash
./scripts/smoke-demo.sh
```

## 5. Acesso pela TV

1. Confirme o IP do PC: `hostname -I`.
2. Abra `http://IP-DO-PC:8080` no navegador da TV.
3. Informe o token e entre.
4. Use **Assumir controle** quando precisar de mouse/teclado.
5. **Escape** libera o controle; **Sair** encerra a sessão.

## 6. Troubleshooting

| Sintoma | Causa provável | Ação |
|---|---|---|
| TV não abre a página | IP/porta errados, firewall, `--bind` restrito | `curl` do próprio PC; conferir `hostname -I`, regra de firewall e `ss -ltnp` |
| `/health` OK, mas TV não conecta | isolamento de clientes/Wi-Fi (AP isolation) | desativar isolamento ou usar a mesma rede/VLAN |
| `401` no login | token errado/expirado (novo processo gera outro) | usar o token impresso no start ou definir `--token` |
| `403` em `/api/input` | sem cookie, sem header CSRF ou token de controle inválido | relogar, enviar `X-EspelhaTV: 1`, refazer claim |
| `409` no claim | outro dispositivo controla | liberar no outro dispositivo ou aguardar timeout |
| `429` no login | mais de 10 tentativas em 60 s | aguardar 1 minuto |
| Tela preta (modo real) | Wayland/XWayland sem superfície capturável; DRM; lock screen; UAC | testar `--demo`; usar sessão X11; evitar conteúdo protegido |
| Stream não carrega | sem sessão, proxy/firewall, captura sem frames | testar `/api/state`, `/stream` com `curl`, ver logs |
| Stream carrega e para | reconexão do cliente; troca de rede | app reconecta; validar `viewers` em `/api/state` |
| Controle “preso” | cliente fechou sem liberar | servidor libera após timeout (300 s) ou 3 s após fechar o último stream |
| Mouse/teclado não funcionam | input real bloqueado pelo SO | verificar permissões de acessibilidade/X11; testar `--demo` |
| Lentidão/travadas | captura 1080p + escala + JPEG + Wi-Fi | reduzir `--fps`, `--width`, `--height`, `--quality` |
| CPU alta | captura ativa com espectador | normal; reduzir FPS/resolução; encerrar sessões ociosas |
| Porta ocupada | outro processo | `ss -ltnp`; escolher outra `--port` |

### Ver logs

Em foreground, o banner e os erros saem no terminal. Com systemd:

```bash
journalctl --user -u espelha-tv -n 200 --no-pager
```

O servidor não grava arquivos de log próprios e não persiste frames.

### Diagnosticar captura

```bash
# fonte e estado
curl -b /tmp/espelha.cookie http://127.0.0.1:8080/api/state

# backend de captura
echo "$XDG_SESSION_TYPE" "$WAYLAND_DISPLAY" "$DISPLAY"
ESPELHATV_SCREENSHOT_METHOD=x11 ./scripts/run.sh --token="teste"
```

Se a imagem continuar preta, valide em sessão X11 nativa ou use `--demo` para
isolar o problema de rede/TV.

## 7. Segurança operacional

- Rotacione o token reiniciando com `--token` novo; não reutilize o de demonstração.
- Restrinja a porta à sub-rede da TV.
- Não faça port forwarding; não use Wi-Fi público.
- Monitore tentativas `401`/`429` nos logs.
- Se a porta foi exposta: encerre o processo, bloqueie no firewall, troque o token e
  verifique sessões ativas (reiniciar invalida todas).

## 8. Encerrar

```bash
# foreground
Ctrl+C

# systemd
systemctl --user stop espelha-tv

# conferir porta livre
ss -ltnp | grep ':8080' || echo "porta livre"
```

O shutdown chama `releaseAll()` e encerra captura/servidor.

## 9. Atualização e rollback

```bash
git pull
./scripts/build.sh
# reiniciar o serviço/processo
```

Rollback: mantenha o JAR anterior (`target/espelha-tv.jar`) ou faça checkout da tag
`v1.0.0` e rebuild. Não há migração de dados (nada é persistido).

## 10. Checklist pós-deploy

- [ ] `/health` responde 200.
- [ ] Login funciona na TV.
- [ ] Stream MJPEG aparece em 16:9.
- [ ] Espectador não consegue input.
- [ ] Claim exclusivo; segundo cliente recebe 409.
- [ ] Setas/Enter funcionam; Escape libera.
- [ ] Teclado virtual envia texto.
- [ ] Logout encerra a sessão.
- [ ] Firewall restrito à LAN.
- [ ] Token não aparece em logs/prints.
