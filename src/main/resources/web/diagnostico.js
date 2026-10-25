const out = document.getElementById('out');
const status = document.getElementById('status');
const hlsVideo = document.getElementById('hlsVideo');
const btnRun = document.getElementById('btnRun');
const btnHls = document.getElementById('btnHls');
const btnWebrtc = document.getElementById('btnWebrtc');
const btnHlsPlayer = document.getElementById('btnHlsPlayer');

const WHEP_URL = `http://${location.hostname}:8889/stream/whep`;
const HLS_MEDIA_URL = `http://${location.hostname}:8888/stream/index.m3u8`;
const HLS_PLAYER_URL = `http://${location.hostname}:8888/stream/`;

function log(line) {
  out.textContent += line + '\n';
}

function setStatus(text, kind) {
  status.textContent = text;
  status.className = 'status ' + (kind || '');
}

function withTimeout(promise, ms, label) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(label + ' (timeout)')), ms);
    promise.then(
      value => { clearTimeout(timer); resolve(value); },
      error => { clearTimeout(timer); reject(error); }
    );
  });
}

function canPlay(type) {
  const probe = document.createElement('video');
  return probe.canPlayType(type) || 'não';
}

async function probeWebRtc() {
  if (typeof RTCPeerConnection === 'undefined') {
    log('WebRTC: AUSENTE (RTCPeerConnection indefinido)');
    return;
  }
  log('WebRTC: RTCPeerConnection presente');
  let pc = null;
  try {
    pc = new RTCPeerConnection({ iceServers: [] });
    pc.addTransceiver('video', { direction: 'recvonly' });
    const offer = await withTimeout(pc.createOffer(), 6000, 'createOffer');
    await withTimeout(pc.setLocalDescription(offer), 6000, 'setLocalDescription');
    const sdpSize = pc.localDescription && pc.localDescription.sdp ? pc.localDescription.sdp.length : 0;
    log('WebRTC createOffer: OK (' + sdpSize + ' bytes de SDP)');
  } catch (e) {
    log('WebRTC createOffer: FALHOU - ' + e.message);
  } finally {
    if (pc) {
      try { pc.close(); } catch (e) { /* ignora */ }
    }
  }
}

async function probeHttp() {
  if (typeof fetch === 'undefined') {
    log('HTTP WHEP: fetch AUSENTE');
    return;
  }
  try {
    const response = await withTimeout(
      fetch(WHEP_URL, { method: 'GET' }),
      6000,
      'GET WHEP'
    );
    log('HTTP WHEP: alcançável (status ' + response.status + ')');
  } catch (e) {
    log('HTTP WHEP: FALHOU - ' + e.message);
  }
}

async function run() {
  out.textContent = '';
  setStatus('Executando diagnóstico...', '');
  log('UA: ' + navigator.userAgent);
  log('Host: ' + location.hostname);
  log('fetch: ' + (typeof fetch));
  log('MediaSource: ' + (typeof MediaSource));
  log('HLS nativo apple: ' + canPlay('application/vnd.apple.mpegurl'));
  log('HLS nativo x-mpegURL: ' + canPlay('application/x-mpegURL'));
  await probeWebRtc();
  await probeHttp();
  setStatus('Diagnóstico concluído. Se WebRTC falhar, use o player HLS.', 'warn');
}

function testHls() {
  hlsVideo.classList.remove('hidden');
  hlsVideo.src = HLS_MEDIA_URL;
  hlsVideo.load();
  setStatus('Testando HLS nativo...', '');
  log('HLS nativo: tentando ' + HLS_MEDIA_URL);

  const timeout = setTimeout(() => {
    log('HLS nativo: timeout (não iniciou). Use o player HLS do MediaMTX.');
    setStatus('HLS nativo não iniciou. Use "Abrir player HLS".', 'warn');
  }, 12000);

  hlsVideo.addEventListener('playing', () => {
    clearTimeout(timeout);
    log('HLS nativo: PLAYING ' + hlsVideo.videoWidth + 'x' + hlsVideo.videoHeight);
    setStatus('HLS nativo funcionando!', 'ok');
  }, { once: true });

  hlsVideo.addEventListener('error', () => {
    clearTimeout(timeout);
    const code = hlsVideo.error ? hlsVideo.error.code : '?';
    log('HLS nativo: ERRO (code ' + code + '). Use o player HLS do MediaMTX.');
    setStatus('HLS nativo falhou. Use "Abrir player HLS".', 'warn');
  }, { once: true });

  const playPromise = hlsVideo.play();
  if (playPromise && playPromise.catch) {
    playPromise.catch(e => log('HLS play(): ' + e.message));
  }
}

btnRun.addEventListener('click', run);
btnHls.addEventListener('click', testHls);
btnWebrtc.addEventListener('click', () => { location.href = '/test-webrtc.html'; });
btnHlsPlayer.addEventListener('click', () => { location.href = HLS_PLAYER_URL; });
