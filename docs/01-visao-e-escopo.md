# 01 — Visão e Escopo

## 1. Problema

TVs Smart antigas e TVs LCD usadas como monitor secundário têm navegadores limitados e não aceitam aplicativos Android. Ao mesmo tempo, é comum querer **ver a tela de um PC na TV** sem passar HDMI, sem instalar nada na TV e sem depender de internet/cloud.

O projeto **Espelha-TV** resolve esse cenário com um servidor Java executando no PC e um cliente web aberto no navegador da TV, ambos na mesma rede local fechada.

## 2. Objetivo

Entregar um MVP que:

1. capture a tela do PC;
2. transmita vídeo para o navegador da TV em rede local;
3. permita **somente visualizar** (modo espectador, padrão);
4. permita **assumir o controle** de mouse/teclado de forma explícita, com um único controlador por vez;
5. não exija instalação, root ou JVM na TV;
6. funcione sem internet, sem cloud e sem abrir portas no roteador.

## 3. Público-alvo e personas

| Persona | Necessidade | Restrição |
|---|---|---|
| Usuário doméstico | Ver o PC na TV da sala | TV antiga, Wi-Fi doméstico |
| Professor / suporte | Projetar a tela para a turma/cliente | Rede fechada, quer controle opcional |
| Estudante / projeto integrador | Demonstrar arquitetura Java + web | Precisa de escopo viável e documentação |
| Operador de sala | Controlar o PC remotamente pela TV | Não pode sofrer interferência de espectadores |

## 4. Jornadas principais

### J1 — Espectador
1. O PC executa `java -jar espelha-tv.jar`.
2. O usuário abre `http://IP-DO-PC:8080` no navegador da TV.
3. Informa o token de acesso.
4. A tela do PC aparece em modo espectador.
5. Nenhuma tecla/clique da TV chega ao PC.

### J2 — Controlador
1. Na mesma tela, pressiona **Assumir controle**.
2. O servidor concede um token de controle (se ninguém estiver controlando).
3. Setas movem o mouse; Enter clica; teclado/USB ou teclado virtual digita.
4. Um indicador visível mostra "CONTROLE ATIVO".
5. **Liberar** ou timeout por inatividade devolve o PC ao modo espectador.

### J3 — Diagnóstico
1. O operador inicia com `--demo` para validar a rede/TV sem depender da captura do desktop.
2. O endpoint `/health` confirma que o servidor está de pé.
3. O modo demo exibe uma tela animada e permite testar login, stream e controle.

## 5. Escopo IN (MVP)

- Servidor Java 21, executável por `java -jar` ou `jpackage`.
- Captura de tela com `java.awt.Robot` no PC (Windows e Linux X11; Wayland documentado).
- Stream **MJPEG** sobre HTTP (`multipart/x-mixed-replace`).
- Cliente web responsivo para TV (HTML/CSS/JS, sem framework).
- Login por token e sessão em cookie `HttpOnly`/`SameSite=Strict`.
- Modo espectador por padrão.
- Assumir/liberar controle com trava de **um único controlador**.
- Entrada de mouse (absoluta e relativa), clique, roda e teclado.
- Teclado virtual simples para texto ASCII.
- Fullscreen, reconexão automática do stream, indicador de estado.
- Timeout de controle por inatividade e liberação de todas as teclas ao sair.
- Modo demo (`--demo`) e endpoint `/health`.
- Testes unitários e de integração HTTP/stream.
- Documentação: SRS, arquitetura/UML, ADRs, backlog, runbook e plano de testes.

## 6. Escopo OUT (não faz parte do MVP)

- App Android/APK ou instalação de Android na TV.
- App nativo webOS/Tizen empacotado (`.ipk`/`.wgt`) — evolução futura.
- Áudio do PC na TV.
- WebRTC, H.264/MSE, aceleração por GPU.
- Múltiplos monitores (MVP usa o monitor primário).
- TLS/HTTPS pronto para produção (documentado como evolução).
- Acesso pela internet, NAT traversal, STUN/TURN.
- Clipboard, transferência de arquivos, gravação de sessão.
- Controle de dispositivos (ligar/desligar PC, áudio, volume).
- Suporte a TVs sem navegador (TV "burra" sem browser).

## 7. Premissas

- O PC e a TV estão na mesma LAN (mesma sub-rede ou roteamento permitido).
- O navegador da TV é Chromium/WebKit minimamente moderno; MJPEG é o fallback.
- O PC permanece ligado, desbloqueado e com a sessão do usuário ativa.
- O usuário autoriza o acesso à própria tela; não há uso para vigilância de terceiros.
- O firewall do PC permite a porta escolhida apenas na LAN.
- O token é compartilhado por um canal seguro (verbal, papel, gerenciador de senhas).

## 8. Restrições

| Restrição | Impacto |
|---|---|
| Rede fechada | Sem STUN/TURN; conexão direta por IP; menor latência |
| Navegador da TV | Sem instalação; MJPEG primeiro; WebSocket/MSE podem não existir |
| Java no PC | `Robot` exige sessão gráfica; Wayland limita captura/input |
| CPU do PC | Captura + JPEG consomem CPU; escala/FPS configuráveis |
| Segurança | Tela + input = controle total; token, sessão e escopo LAN obrigatórios |
| LGPD/privacidade | Nada é gravado; stream existe só enquanto a sessão está aberta |

## 9. Critérios de sucesso

- Abrir a tela na TV em **≤ 3 s** após o login, em LAN cabeada ou Wi-Fi 5.
- Latência perceptual de **150–400 ms** para MJPEG em 720p/10 fps (meta, não garantia).
- **Zero** eventos de input aceitos de sessões espectadoras.
- **No máximo um** controlador ativo; tentativa concorrente recebe `409`.
- Stream sobrevive a queda momentânea de rede com reconexão automática.
- `mvn test` verde; cobertura relevante nos componentes de auth, controle, input e stream.
- Nenhum segredo no repositório; token gerado em runtime ou fornecido por variável de ambiente.

## 10. Riscos iniciais

| Risco | Probabilidade | Impacto | Mitigação |
|---|---|---|---|
| Navegador da TV não suportar o stream | Média | Alto | MJPEG como baseline; testar cedo; modo demo |
| Wayland bloquear `Robot` | Média (Linux) | Alto | Documentar; exigir X11 ou portal; testar no PC alvo |
| CPU/banda insuficientes em 1080p | Média | Médio | 720p padrão; qualidade/FPS configuráveis; drop de frames |
| UAC/tela de bloqueio não capturáveis | Alta | Médio | Documentar limitação; não prometer acesso pré-login |
| Uso indevido (acesso não autorizado) | Baixa | Alto | Token forte, rate limit, LAN, sem internet, logs |
| Tecla presa após desconexão | Média | Médio | `releaseAll` no release/timeout/erro |
| Back/Home da TV sair da página | Alta | Baixo | Documentar; app empacotado como evolução |

## 11. Referências

- Material do projeto integrador: https://integrador-cedup.glide.page/dl/a400f7
- webOS TV Developer — Web API/Web Engine: https://webostv.developer.lge.com/develop/specifications/web-api-and-web-engine
- Samsung Developer — Web Engine Specifications: https://developer.samsung.com/smarttv/develop/specifications/web-engine-specifications.html
- OpenJDK — `java.awt.Robot` e Wayland: https://bugs.openjdk.org/browse/JDK-8280982
- Apache Guacamole: https://guacamole.apache.org/
