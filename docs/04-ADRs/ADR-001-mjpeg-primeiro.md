# ADR-001 — MJPEG como transporte inicial

**Status:** aceito  
**Data:** 2026-10-23

## Contexto
O navegador da TV é o elo mais fraco: modelos variam de WebKit antigo a Chromium moderno, e não há garantia de WebSocket, MSE, WebRTC ou codecs. O objetivo do MVP é "ver a tela" com o menor risco possível.

## Decisão
Usar **MJPEG sobre HTTP** (`multipart/x-mixed-replace`) no MVP, com uma imagem JPEG por frame. O cliente usa um `<img>` apontando para `/stream`.

## Alternativas
- **WebSocket + JPEG:** mais controle e menor overhead por evento, mas exige WebSocket e JS de canvas; fica para evolução.
- **H.264 + MSE:** banda menor, porém mais complexo (ffmpeg/GStreamer, fMP4, segmentação) e depende de MSE/H.264 no modelo.
- **WebRTC:** menor latência, mas servidor Java complexo (JNI/libwebrtc) e suporte incerto na TV.

## Consequências
- Compatibilidade máxima e implementação simples.
- Banda alta e sem áudio/aceleração.
- 720p/10 fps como padrão; escala e qualidade configuráveis.
- Se a banda doer, evoluir para H.264/MSE após medir na TV alvo.
