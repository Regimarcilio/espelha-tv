const video = document.getElementById('video');
const status = document.getElementById('status');
const stats = document.getElementById('stats');
const btnConnect = document.getElementById('btnConnect');
const btnDisconnect = document.getElementById('btnDisconnect');
const btnFull = document.getElementById('btnFull');
const btnFloatFull = document.getElementById('btnFloatFull');
const btnFloatExit = document.getElementById('btnFloatExit');
const hlsLink = document.getElementById('hlsLink');

// MediaMTX roda no mesmo host que serve esta página (porta 8889).
const WHEP_URL = `http://${location.hostname}:8889/stream/whep`;
const HLS_URL = `http://${location.hostname}:8888/stream/`;
hlsLink.href = HLS_URL;

const OFFER_TIMEOUT_MS = 6000;
const WHEP_TIMEOUT_MS = 8000;

let pc = null;
let statsInterval = null;
let uiTimer = null;

function withTimeout(promise, ms, label) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(label + ' (timeout de ' + ms + 'ms)')), ms);
    promise.then(
      value => { clearTimeout(timer); resolve(value); },
      error => { clearTimeout(timer); reject(error); }
    );
  });
}

async function postSdp(url, sdp, ms) {
  const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
  const timer = controller ? setTimeout(() => controller.abort(), ms) : null;
  try {
    return await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/sdp' },
      body: sdp,
      signal: controller ? controller.signal : undefined
    });
  } finally {
    if (timer) {
      clearTimeout(timer);
    }
  }
}

function setStatus(text, kind) {
  status.textContent = text;
  status.className = 'status ' + (kind || '');
}

function requestFullscreenSafe() {
  const root = document.documentElement;
  const fn = root.requestFullscreen || root.webkitRequestFullscreen || root.msRequestFullscreen;
  if (fn) {
    try {
      const result = fn.call(root);
      if (result && result.catch) {
        result.catch(() => {});
      }
    } catch (e) {
      console.warn('Fullscreen indisponível:', e.message);
    }
  } else if (video.webkitEnterFullscreen) {
    try { video.webkitEnterFullscreen(); } catch (e) { console.warn('Fullscreen indisponível:', e.message); }
  }
}

function exitFullscreenSafe() {
  const fn = document.exitFullscreen || document.webkitExitFullscreen || document.msExitFullscreen;
  if (fn) {
    try {
      const result = fn.call(document);
      if (result && result.catch) {
        result.catch(() => {});
      }
    } catch (e) {
      // ignora
    }
  }
}

function showUi() {
  document.body.classList.add('show-ui');
  clearTimeout(uiTimer);
  uiTimer = setTimeout(() => document.body.classList.remove('show-ui'), 3000);
}

function enterImmersive() {
  document.body.classList.add('playing');
  showUi();
}

function exitImmersive() {
  document.body.classList.remove('playing');
  document.body.classList.remove('show-ui');
  clearTimeout(uiTimer);
}

async function connect() {
  // Precisa ser chamado dentro do gesto do usuário para o fullscreen valer.
  requestFullscreenSafe();

  try {
    setStatus('Conectando em ' + WHEP_URL + ' ...', '');

    if (typeof RTCPeerConnection === 'undefined') {
      setStatus('Este navegador não suporta WebRTC. Use o player HLS: ' + HLS_URL, 'warn');
      exitFullscreenSafe();
      return;
    }
    if (typeof fetch === 'undefined') {
      setStatus('Este navegador não suporta fetch. Use o player HLS: ' + HLS_URL, 'warn');
      exitFullscreenSafe();
      return;
    }

    // LAN local: candidatos host do MediaMTX bastam (sem STUN/TURN).
    pc = new RTCPeerConnection({ iceServers: [] });

    pc.ontrack = (event) => {
      video.srcObject = event.streams[0];
      setStatus('Conectado - Reproduzindo', 'ok');
      btnConnect.disabled = true;
      btnDisconnect.disabled = false;
      stats.classList.remove('hidden');
      enterImmersive();
    };

    pc.oniceconnectionstatechange = () => {
      console.log('ICE state:', pc.iceConnectionState);
      if (pc.iceConnectionState === 'failed' || pc.iceConnectionState === 'disconnected') {
        setStatus('Conexão falhou: ' + pc.iceConnectionState, 'err');
        exitImmersive();
      }
    };

    // Somente recebe vídeo
    pc.addTransceiver('video', { direction: 'recvonly' });

    const offer = await withTimeout(pc.createOffer(), OFFER_TIMEOUT_MS, 'createOffer');
    await withTimeout(pc.setLocalDescription(offer), OFFER_TIMEOUT_MS, 'setLocalDescription');

    // WHEP do MediaMTX: POST do offer SDP, resposta 201 com answer SDP.
    let response;
    try {
      response = await postSdp(WHEP_URL, offer.sdp, WHEP_TIMEOUT_MS);
    } catch (e) {
      throw new Error('POST WHEP falhou: ' + (e.name === 'AbortError' ? 'timeout' : e.message));
    }

    if (!response.ok) {
      throw new Error(`WHEP falhou: ${response.status} ${response.statusText}`);
    }

    const answerSdp = await response.text();
    await withTimeout(
      pc.setRemoteDescription({ type: 'answer', sdp: answerSdp }),
      OFFER_TIMEOUT_MS,
      'setRemoteDescription'
    );

    let lastBytes = 0;
    let lastTs = 0;
    statsInterval = setInterval(async () => {
      if (!pc || pc.connectionState !== 'connected') {
        return;
      }
      try {
        const reports = await pc.getStats();
        reports.forEach(report => {
          const isVideo = report.type === 'inbound-rtp' && (report.kind === 'video' || report.mediaType === 'video');
          if (!isVideo) {
            return;
          }
          const now = report.timestamp;
          const kbps = lastTs && now > lastTs
            ? ((report.bytesReceived - lastBytes) * 8 / (now - lastTs)).toFixed(0)
            : 0;
          lastBytes = report.bytesReceived;
          lastTs = now;
          stats.textContent =
            `FPS: ${(report.framesPerSecond || 0).toFixed(1)} | Bitrate: ${kbps} kbps | ` +
            `Res: ${report.frameWidth || 0}x${report.frameHeight || 0} | ICE: ${pc.iceConnectionState}`;
        });
      } catch (e) {
        console.warn('Stats indisponíveis:', e.message);
      }
    }, 1000);

  } catch (err) {
    setStatus('Erro: ' + err.message, 'err');
    console.error(err);
    exitImmersive();
    exitFullscreenSafe();
  }
}

function disconnect() {
  if (pc) {
    pc.close();
    pc = null;
  }
  if (statsInterval) {
    clearInterval(statsInterval);
    statsInterval = null;
  }
  video.srcObject = null;
  setStatus('Desconectado', 'err');
  btnConnect.disabled = false;
  btnDisconnect.disabled = true;
  stats.classList.add('hidden');
  exitImmersive();
  exitFullscreenSafe();
}

btnConnect.addEventListener('click', connect);
btnDisconnect.addEventListener('click', disconnect);
btnFull.addEventListener('click', requestFullscreenSafe);
btnFloatFull.addEventListener('click', requestFullscreenSafe);
btnFloatExit.addEventListener('click', disconnect);
video.addEventListener('click', () => {
  if (document.body.classList.contains('playing')) {
    showUi();
  }
});
