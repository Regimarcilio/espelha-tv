const video = document.getElementById('video');
const status = document.getElementById('status');
const stats = document.getElementById('stats');
const btnConnect = document.getElementById('btnConnect');
const btnDisconnect = document.getElementById('btnDisconnect');

// MediaMTX roda no mesmo host que serve esta página (porta 8889).
const WHEP_URL = `http://${location.hostname}:8889/stream/whep`;

let pc = null;
let statsInterval = null;

async function connect() {
  try {
    status.textContent = 'Conectando em ' + WHEP_URL + ' ...';
    status.className = 'status';

    // LAN local: candidatos host do MediaMTX bastam (sem STUN/TURN).
    pc = new RTCPeerConnection({ iceServers: [] });

    pc.ontrack = (event) => {
      video.srcObject = event.streams[0];
      status.textContent = 'Conectado - Reproduzindo';
      status.className = 'status ok';
      btnConnect.disabled = true;
      btnDisconnect.disabled = false;
      stats.classList.remove('hidden');
    };

    pc.oniceconnectionstatechange = () => {
      console.log('ICE state:', pc.iceConnectionState);
      if (pc.iceConnectionState === 'failed' || pc.iceConnectionState === 'disconnected') {
        status.textContent = 'Conexão falhou: ' + pc.iceConnectionState;
        status.className = 'status err';
      }
    };

    // Somente recebe vídeo
    pc.addTransceiver('video', { direction: 'recvonly' });

    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);

    // WHIP/WHEP do MediaMTX: POST do offer SDP, resposta 201 com answer SDP.
    const response = await fetch(WHEP_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/sdp' },
      body: offer.sdp
    });

    if (!response.ok) {
      throw new Error(`WHEP falhou: ${response.status} ${response.statusText}`);
    }

    const answerSdp = await response.text();
    await pc.setRemoteDescription({ type: 'answer', sdp: answerSdp });

    let lastBytes = 0;
    let lastTs = 0;
    statsInterval = setInterval(async () => {
      if (!pc || pc.connectionState !== 'connected') {
        return;
      }
      const reports = await pc.getStats();
      reports.forEach(report => {
        if (report.type === 'inbound-rtp' && report.kind === 'video') {
          const now = report.timestamp;
          const kbps = lastTs ? ((report.bytesReceived - lastBytes) * 8 / (now - lastTs)).toFixed(0) : 0;
          lastBytes = report.bytesReceived;
          lastTs = now;
          stats.textContent =
            `FPS: ${(report.framesPerSecond || 0).toFixed(1)} | Bitrate: ${kbps} kbps | ` +
            `Res: ${report.frameWidth || 0}x${report.frameHeight || 0} | ICE: ${pc.iceConnectionState}`;
        }
      });
    }, 1000);

  } catch (err) {
    status.textContent = 'Erro: ' + err.message;
    status.className = 'status err';
    console.error(err);
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
  status.textContent = 'Desconectado';
  status.className = 'status err';
  btnConnect.disabled = false;
  btnDisconnect.disabled = true;
  stats.classList.add('hidden');
}

btnConnect.addEventListener('click', connect);
btnDisconnect.addEventListener('click', disconnect);
