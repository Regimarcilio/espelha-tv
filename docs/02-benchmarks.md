# 02 — Benchmarks e Alternativas

## 1. Critérios de comparação

- **Cliente na TV:** funciona só no navegador, sem instalar app?
- **Controle remoto:** envia mouse/teclado?
- **Latência/banda:** adequado para LAN?
- **Complexidade de implantação:** depende de quantos processos/serviços?
- **Stack:** Java? Permite projeto acadêmico em Java?
- **Maturidade/manutenção:** projeto ativo, documentação, comunidade?
- **Licença:** compatível com uso educacional/doméstico?

## 2. Soluções de referência

| Solução | Cliente na TV | Controle | Stack | Prós | Contras | Licença |
|---|---|---|---|---|---|---|
| **Apache Guacamole** | Sim, HTML5 | Sim (VNC/RDP/SSH) | Java (webapp) + guacd (C) | Maduro, protocolo próprio, multi-protocolo, autenticação | Implantação mais pesada (Tomcat, guacd, DB); cliente JS pode ser pesado para TVs antigas | Apache-2.0 |
| **noVNC** | Sim, HTML5 | Sim (VNC) | JavaScript + servidor VNC | Leve, simples, muito usado | Exige VNC no PC; não é Java; teclado/controle na TV é limitado | MPL-2.0 |
| **RustDesk** | Não (app nativo) | Sim | Rust | Auto-hospedável, performance, relay próprio | Não é browser; exige instalar cliente na TV (inviável) | AGPL-3.0 |
| **Deskreen** | Sim, navegador | Não (compartilha tela) | Electron + WebRTC | Referência de compartilhamento via browser | Foco em compartilhar tela, não em controle; Electron no host | open source |
| **Sunshine + Moonlight** | Não (cliente nativo) | Sim | C++ | Latência baixíssima, hardware encode | Cliente nativo; não roda no navegador da TV | GPL-3.0 |
| **Chrome Remote Desktop** | Sim, mas via web/Google | Sim | Proprietário/cloud | Fácil | Depende de internet/Google; não é LAN fechada | proprietária |
| **VNC tradicional** | Não (cliente nativo) | Sim | C | Maduro, leve | Sem cliente no navegador da TV | GPL variada |
| **Solução custom MJPEG (este projeto)** | Sim, `<img>` | Sim, opcional | Java puro + HTML/JS | Zero dependência de runtime; máxima compatibilidade; didático | Banda alta; sem áudio; sem aceleração; input via HTTP | próprio |

## 3. Comparação de transportes de vídeo

| Transporte | Compatibilidade | Latência LAN | Banda | Complexidade | Decisão |
|---|---|---|---|---|---|
| **MJPEG over HTTP** | Muito alta (inclusive WebKit antigo) | 150–400 ms | Alta (JPEG por frame) | Baixa | **MVP** |
| WebSocket + JPEG | Alta (Chromium 38+) | 100–300 ms | Alta | Média | Evolução para controle/ajustes |
| H.264 + MSE | Média (Tizen/webOS modernos) | 300–1000 ms | Baixa/média | Alta | Evolução se banda doer |
| WebRTC | Baixa/incerta em TV | 50–200 ms | Baixa | Muito alta no servidor Java | Fora do MVP |

## 4. Evidências técnicas consultadas

- **LG webOS:** apps web são HTML/CSS/JS e o browser é Chromium (versões por ano; webOS 3.x = Chromium 38 em 2016, webOS 6.x = Chromium 79 em 2021). MJPEG via `<img>` é o caminho de menor denominador comum.
- **Samsung Tizen:** o motor web suporta Canvas, WebSocket, MSE e H.264; a documentação de mídia informa MSE até FHD e H.264 FHD nível 4.2. Isso viabiliza uma evolução para H.264/MSE, mas não garante WebRTC em todos os modelos.
- **Java `Robot`:** no Linux Wayland a captura pode retornar preto; o OpenJDK 21 introduziu `awt.robot.screenshotMethod=dbusScreencast`, ainda marcado como configuração não suportada. Em Windows e X11 o caminho é estável. Em macOS há permissões de Tela e Acessibilidade.
- **Guacamole:** arquitetura Java + `guacd` + cliente HTML5; é a referência mais próxima de "acessar desktop pelo navegador" e deve ser considerada se o objetivo for produtividade, não aprendizado.

## 5. Decisão

1. **Implementar um servidor Java próprio**, sem dependências de runtime, usando `com.sun.net.httpserver` e `java.awt.Robot`.
2. **MJPEG primeiro** para maximizar a chance de funcionar no navegador da TV antiga.
3. **Controle via HTTP POST** (não WebSocket) no MVP: eventos são discretos (teclas/setas), o volume é baixo e isso elimina dependências e problemas de proxy.
4. **Guacamole como fallback** documentado: se o usuário quiser uma solução pronta e madura, ela é a primeira alternativa a testar.
5. **H.264/MSE ou WebRTC** somente depois de medir banda/latência reais na TV alvo; não são pré-requisito do MVP.

## 6. Consequências

- O MVP não terá áudio nem vídeo acelerado; isso é aceitável para "ver a tela" e para controle leve.
- A banda em 1080p pode ser alta; o padrão será 720p/10 fps, configurável.
- A experiência de controle pela TV depende do navegador; o app empacotado webOS/Tizen fica como evolução.
- Caso a TV seja pré-2014 (LG NetCast) ou pré-2015 (Samsung Orsay), o teste com MJPEG é obrigatório antes de prometer suporte.
