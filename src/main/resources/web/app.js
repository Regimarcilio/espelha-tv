(function () {
  'use strict';

  var stream = document.getElementById('stream');
  var stage = document.getElementById('stage');
  var connecting = document.getElementById('connecting');
  var statusEl = document.getElementById('status');
  var connDot = document.getElementById('connDot');
  var claimBtn = document.getElementById('claimBtn');
  var releaseBtn = document.getElementById('releaseBtn');
  var fullscreenBtn = document.getElementById('fullscreenBtn');
  var tvExitBtn = document.getElementById('tvExitBtn');
  var logoutBtn = document.getElementById('logoutBtn');
  var keyboardBtn = document.getElementById('keyboardBtn');
  var modeLabel = document.getElementById('modeLabel');
  var viewersLabel = document.getElementById('viewersLabel');
  var overlay = document.getElementById('overlay');
  var overlayTitle = document.getElementById('overlayTitle');
  var overlayText = document.getElementById('overlayText');
  var overlayClose = document.getElementById('overlayClose');
  var keyboardModal = document.getElementById('keyboardModal');
  var textInput = document.getElementById('textInput');
  var textSend = document.getElementById('textSend');
  var textCancel = document.getElementById('textCancel');

  var state = {
    info: null,
    isController: false,
    controllerActive: false,
    controlToken: null,
    claimInFlight: false,
    autoClaimTried: false,
    modalOpen: false,
    overlayOpen: false,
    streamOnline: false,
    streamRetry: null,
    lastMove: 0,
    mouseDown: false
  };

  function encodeForm(data) {
    var parts = [];
    var key;
    for (key in data) {
      if (Object.prototype.hasOwnProperty.call(data, key)) {
        parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(data[key]));
      }
    }
    return parts.join('&');
  }

  function request(path, data, keepalive) {
    return fetch(path, {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'X-EspelhaTV': '1'
      },
      body: encodeForm(data),
      keepalive: !!keepalive
    });
  }

  function toLogin() {
    window.location.replace('/login');
  }

  function post(path, data) {
    return request(path, data, false).then(function (response) {
      if (response.status === 401) {
        toLogin();
        throw new Error('unauthorized');
      }
      return response;
    });
  }

  function responseJson(response) {
    return response.json().catch(function () {
      return {};
    });
  }

  function setStatus(text, offline) {
    statusEl.textContent = text;
    statusEl.className = offline ? 'status offline' : 'status';
    connDot.className = 'conn-dot ' + (offline ? 'offline' : 'online');
  }

  function render() {
    var info = state.info;
    var active = state.isController;
    var otherActive = !!(info && info.controllerActive && !state.isController);

    document.body.classList.add('app-page');
    document.body.classList.toggle('control-active', active);
    claimBtn.hidden = state.isController && !!state.controlToken;
    claimBtn.disabled = otherActive;
    claimBtn.textContent = state.isController && !state.controlToken
      ? 'Retomar controle'
      : (otherActive ? 'Controle em uso' : 'Assumir controle');
    releaseBtn.hidden = !state.isController || !state.controlToken;
    keyboardBtn.disabled = !state.isController || !state.controlToken;
    modeLabel.textContent = state.isController
      ? 'Controle ativo'
      : (otherActive ? 'Espectador (controle em uso)' : 'Espectador');
    viewersLabel.textContent = info ? (info.viewers + ' espectador(es)') : '';

    if (!state.streamOnline) {
      setStatus('Reconectando ao stream…', true);
      return;
    }
    if (info) {
      var text = info.width + '×' + info.height + ' • ' + info.fps + ' fps';
      if (info.demo) {
        text += ' • DEMO';
      }
      statusEl.title = info.captureError || '';
      setStatus(text, false);
    } else {
      setStatus('Conectando…', false);
    }
  }

  function loadState() {
    fetch('/api/state', { credentials: 'same-origin', cache: 'no-store' })
      .then(function (response) {
        if (response.status === 401) {
          toLogin();
          throw new Error('unauthorized');
        }
        if (!response.ok) {
          throw new Error('state');
        }
        return response.json();
      })
      .then(function (info) {
        state.info = info;
        state.isController = !!info.isController;
        state.controllerActive = !!info.controllerActive;
        if (!state.isController) {
          state.controlToken = null;
        }
        render();
        if (state.isController && !state.controlToken && !state.autoClaimTried) {
          state.autoClaimTried = true;
          claim(true);
        }
      })
      .catch(function () {
        setStatus('Sem conexão com o PC', true);
      });
  }

  function claim(silent) {
    if (state.claimInFlight) {
      return;
    }
    state.claimInFlight = true;
    post('/api/control', { action: 'claim' })
      .then(function (response) {
        return responseJson(response).then(function (data) {
          return { response: response, data: data };
        });
      })
      .then(function (result) {
        if (result.response.ok && result.data.granted) {
          state.controlToken = result.data.token;
          state.isController = true;
          state.controllerActive = true;
          render();
          if (!silent) {
            showOverlay('Controle ativo',
              'Setas movem o mouse. Enter clica. Escape libera. Use o botão Teclado para digitar.');
          } else {
            stage.focus();
          }
        } else {
          state.controlToken = null;
          state.isController = false;
          if (!silent) {
            showOverlay('Controle em uso',
              result.data.message || 'Outro dispositivo está controlando. Aguarde a liberação.');
          }
          loadState();
        }
      })
      .catch(function () {
        // post() já redireciona em 401; falha de rede é tratada no próximo estado.
      })
      .then(function () {
        state.claimInFlight = false;
      });
  }

  function release() {
    var token = state.controlToken;
    state.controlToken = null;
    state.isController = false;
    state.controllerActive = false;
    state.autoClaimTried = false;
    state.mouseDown = false;
    hideOverlay();
    render();
    if (!token) {
      return Promise.resolve();
    }
    return post('/api/control', { action: 'release', token: token })
      .catch(function () {
        return null;
      })
      .then(function () {
        loadState();
      });
  }

  function sendInput(type, fields) {
    if (!state.controlToken) {
      return Promise.resolve(null);
    }
    var data = { token: state.controlToken, type: type };
    var key;
    for (key in fields) {
      if (Object.prototype.hasOwnProperty.call(fields, key)) {
        data[key] = fields[key];
      }
    }
    return request('/api/input', data, false)
      .then(function (response) {
        if (response.status === 401) {
          toLogin();
          return null;
        }
        if (response.status === 403) {
          state.isController = false;
          state.controlToken = null;
          render();
          return null;
        }
        return response;
      })
      .catch(function () {
        return null;
      });
  }

  function sendKeyTap(code) {
    if (!code) {
      return;
    }
    sendInput('key', { code: code, down: 'true' });
    window.setTimeout(function () {
      sendInput('key', { code: code, down: 'false' });
    }, 28);
  }

  function click() {
    sendInput('click', { button: 1, down: 'true' });
    window.setTimeout(function () {
      sendInput('click', { button: 1, down: 'false' });
    }, 34);
  }

  function releaseHeldKeys() {
    if (state.isController) {
      sendInput('releaseAll', {});
    }
  }

  function showOverlay(title, text) {
    overlayTitle.textContent = title;
    overlayText.textContent = text;
    overlay.hidden = false;
    state.overlayOpen = true;
    overlayClose.focus();
  }

  function hideOverlay() {
    if (state.overlayOpen) {
      overlay.hidden = true;
      state.overlayOpen = false;
      stage.focus();
    }
  }

  function openKeyboard() {
    if (!state.isController) {
      return;
    }
    state.modalOpen = true;
    keyboardModal.hidden = false;
    textInput.value = '';
    textInput.focus();
  }

  function closeKeyboard() {
    state.modalOpen = false;
    keyboardModal.hidden = true;
    stage.focus();
  }

  function sendText() {
    var text = textInput.value;
    if (!text) {
      return;
    }
    textSend.disabled = true;
    sendInput('text', { text: text })
      .then(function (response) {
        textSend.disabled = false;
        if (response && response.ok) {
          textInput.value = '';
          closeKeyboard();
        } else if (response && response.status === 400) {
          setStatus('Texto inválido para o PC', true);
        } else {
          setStatus('Falha ao enviar texto', true);
        }
      })
      .catch(function () {
        textSend.disabled = false;
        setStatus('Falha ao enviar texto', true);
      });
  }

  function setTvMode(active) {
    if (active) {
      document.body.classList.add('tv-mode');
      document.body.classList.add('cinema-mode');
    } else {
      document.body.classList.remove('tv-mode');
      document.body.classList.remove('cinema-mode');
    }
    updateFullscreenLabel();
  }

  function toggleTvMode() {
    setTvMode(!document.body.classList.contains('tv-mode'));
  }

  function toggleFullscreen() {
    var doc = document;
    var element = doc.documentElement;
    var fullscreenElement = doc.fullscreenElement || doc.webkitFullscreenElement;
    try {
      if (fullscreenElement) {
        var exit = doc.exitFullscreen || doc.webkitExitFullscreen;
        if (exit) {
          exit.call(doc);
        }
        setTvMode(false);
        return;
      }
      var requestFullscreen = element.requestFullscreen || element.webkitRequestFullscreen;
      if (!requestFullscreen) {
        // webOS/TV antigos: usa tela cheia por CSS.
        toggleTvMode();
        return;
      }
      var result = requestFullscreen.call(element);
      if (result && result.catch) {
        result.catch(function () {
          setTvMode(true);
        });
      }
    } catch (error) {
      setTvMode(true);
    }
  }

  function updateFullscreenLabel() {
    var active = document.fullscreenElement || document.webkitFullscreenElement
      || document.body.classList.contains('tv-mode')
      || document.body.classList.contains('cinema-mode');
    fullscreenBtn.textContent = active ? 'Sair da tela cheia' : 'Tela cheia';
  }

  function handleFullscreenChange() {
    if (document.fullscreenElement || document.webkitFullscreenElement) {
      document.body.classList.add('cinema-mode');
    } else if (!document.body.classList.contains('tv-mode')) {
      document.body.classList.remove('cinema-mode');
    }
    updateFullscreenLabel();
  }

  function logout() {
    var token = state.controlToken;
    state.controlToken = null;
    state.isController = false;
    state.controllerActive = false;
    state.autoClaimTried = false;
    state.modalOpen = false;
    var releasePromise = token
      ? post('/api/control', { action: 'release', token: token }).catch(function () { return null; })
      : Promise.resolve();
    releasePromise
      .then(function () {
        return post('/api/logout', {});
      })
      .catch(function () {
        return null;
      })
      .then(function () {
        window.location.replace('/login');
      });
  }

  function imagePoint(event) {
    var rect = stream.getBoundingClientRect();
    var naturalWidth = stream.naturalWidth;
    var naturalHeight = stream.naturalHeight;
    if (!naturalWidth || !naturalHeight || !rect.width || !rect.height) {
      return null;
    }
    var scale = Math.min(rect.width / naturalWidth, rect.height / naturalHeight);
    var width = naturalWidth * scale;
    var height = naturalHeight * scale;
    var left = rect.left + (rect.width - width) / 2;
    var top = rect.top + (rect.height - height) / 2;
    var x = (event.clientX - left) / width;
    var y = (event.clientY - top) / height;
    if (x < 0 || x > 1 || y < 0 || y > 1) {
      return null;
    }
    return { x: x.toFixed(4), y: y.toFixed(4) };
  }

  function keyCodeToCode(keyCode) {
    if (keyCode >= 65 && keyCode <= 90) {
      return 'Key' + String.fromCharCode(keyCode);
    }
    if (keyCode >= 48 && keyCode <= 57) {
      return 'Digit' + String.fromCharCode(keyCode);
    }
    if (keyCode >= 112 && keyCode <= 123) {
      return 'F' + (keyCode - 111);
    }
    switch (keyCode) {
      case 8: return 'Backspace';
      case 9: return 'Tab';
      case 13: return 'Enter';
      case 16: return 'ShiftLeft';
      case 17: return 'ControlLeft';
      case 18: return 'AltLeft';
      case 27: return 'Escape';
      case 32: return 'Space';
      case 33: return 'PageUp';
      case 34: return 'PageDown';
      case 35: return 'End';
      case 36: return 'Home';
      case 37: return 'ArrowLeft';
      case 38: return 'ArrowUp';
      case 39: return 'ArrowRight';
      case 40: return 'ArrowDown';
      case 45: return 'Insert';
      case 46: return 'Delete';
      case 186: return 'Semicolon';
      case 187: return 'Equal';
      case 188: return 'Comma';
      case 189: return 'Minus';
      case 190: return 'Period';
      case 191: return 'Slash';
      case 192: return 'Backquote';
      case 219: return 'BracketLeft';
      case 220: return 'Backslash';
      case 221: return 'BracketRight';
      case 222: return 'Quote';
      default: return '';
    }
  }

  function keyName(event) {
    if (event.key) {
      return event.key;
    }
    switch (event.keyCode) {
      case 13: return 'Enter';
      case 27: return 'Escape';
      case 32: return ' ';
      case 37: return 'ArrowLeft';
      case 38: return 'ArrowUp';
      case 39: return 'ArrowRight';
      case 40: return 'ArrowDown';
      default: return '';
    }
  }

  function onKeyDown(event) {
    var key = keyName(event);

    if (state.modalOpen) {
      if (key === 'Escape') {
        event.preventDefault();
        closeKeyboard();
      }
      return;
    }

    if (state.overlayOpen) {
      if (key === 'Escape' || key === 'Enter') {
        event.preventDefault();
        hideOverlay();
      }
      return;
    }

    if (!state.isController) {
      return;
    }
    if (event.ctrlKey || event.metaKey || event.altKey) {
      return;
    }
    if (key === 'F5' || key === 'F11' || key === 'F12') {
      return;
    }
    if (key === 'Shift' || key === 'Control' || key === 'Alt' || key === 'Meta' || key === 'CapsLock') {
      return;
    }
    if (key === 'Escape') {
      event.preventDefault();
      release();
      return;
    }
    if (key === 'ArrowUp' || key === 'ArrowDown' || key === 'ArrowLeft' || key === 'ArrowRight') {
      event.preventDefault();
      var step = event.shiftKey ? 80 : 24;
      var dx = key === 'ArrowLeft' ? -step : (key === 'ArrowRight' ? step : 0);
      var dy = key === 'ArrowUp' ? -step : (key === 'ArrowDown' ? step : 0);
      sendInput('moveRelative', { dx: dx, dy: dy });
      return;
    }
    if (key === 'Enter') {
      event.preventDefault();
      click();
      return;
    }

    var code = event.code || keyCodeToCode(event.keyCode);
    if (!code) {
      return;
    }
    event.preventDefault();
    sendKeyTap(code);
  }

  function startStream() {
    if (state.streamRetry) {
      window.clearTimeout(state.streamRetry);
      state.streamRetry = null;
    }
    state.streamOnline = false;
    connecting.hidden = false;
    stream.src = '/stream?t=' + Date.now();
    render();
  }

  stream.addEventListener('load', function () {
    state.streamOnline = true;
    connecting.hidden = true;
    render();
  });

  stream.addEventListener('error', function () {
    state.streamOnline = false;
    connecting.hidden = false;
    render();
    if (state.streamRetry) {
      window.clearTimeout(state.streamRetry);
    }
    state.streamRetry = window.setTimeout(startStream, 2500);
  });

  stream.addEventListener('mousemove', function (event) {
    if (!state.isController) {
      return;
    }
    var now = Date.now();
    if (now - state.lastMove < 40) {
      return;
    }
    state.lastMove = now;
    var point = imagePoint(event);
    if (point) {
      sendInput('move', point);
    }
  });

  stream.addEventListener('mousedown', function (event) {
    if (!state.isController || event.button !== 0) {
      return;
    }
    var point = imagePoint(event);
    if (!point) {
      return;
    }
    event.preventDefault();
    state.mouseDown = true;
    sendInput('move', point);
    sendInput('click', { button: 1, down: 'true' });
  });

  document.addEventListener('mouseup', function (event) {
    if (!state.mouseDown) {
      return;
    }
    state.mouseDown = false;
    if (event.button === 0) {
      sendInput('click', { button: 1, down: 'false' });
    }
  });

  stream.addEventListener('contextmenu', function (event) {
    if (!state.isController) {
      return;
    }
    event.preventDefault();
    sendInput('click', { button: 3, down: 'true' });
    window.setTimeout(function () {
      sendInput('click', { button: 3, down: 'false' });
    }, 34);
  });

  stream.addEventListener('wheel', function (event) {
    if (!state.isController) {
      return;
    }
    event.preventDefault();
    sendInput('wheel', { delta: event.deltaY > 0 ? 1 : -1 });
  }, false);

  document.addEventListener('keydown', onKeyDown, false);
  window.addEventListener('blur', releaseHeldKeys);
  document.addEventListener('visibilitychange', function () {
    if (document.hidden) {
      releaseHeldKeys();
    }
  });
  window.addEventListener('pagehide', releaseHeldKeys);

  claimBtn.addEventListener('click', function () { claim(false); });
  releaseBtn.addEventListener('click', release);
  keyboardBtn.addEventListener('click', openKeyboard);
  overlayClose.addEventListener('click', hideOverlay);
  fullscreenBtn.addEventListener('click', toggleFullscreen);
  tvExitBtn.addEventListener('click', function () {
    setTvMode(false);
    var doc = document;
    var exit = doc.exitFullscreen || doc.webkitExitFullscreen;
    if ((doc.fullscreenElement || doc.webkitFullscreenElement) && exit) {
      exit.call(doc);
    }
  });
  logoutBtn.addEventListener('click', logout);
  textCancel.addEventListener('click', closeKeyboard);
  textSend.addEventListener('click', sendText);
  textInput.addEventListener('keydown', function (event) {
    if (event.key === 'Enter') {
      event.preventDefault();
      sendText();
    } else if (event.key === 'Escape') {
      event.preventDefault();
      closeKeyboard();
    }
  });
  document.addEventListener('fullscreenchange', handleFullscreenChange);
  document.addEventListener('webkitfullscreenchange', handleFullscreenChange);

  window.setInterval(loadState, 5000);
  window.setInterval(function () {
    if (!state.isController || !state.controlToken) {
      return;
    }
    post('/api/heartbeat', { token: state.controlToken })
      .then(function (response) {
        if (response && response.status === 403) {
          state.isController = false;
          state.controlToken = null;
          render();
        }
      })
      .catch(function () {
        // O estado periódico decide se a sessão ainda vale.
      });
  }, 10000);

  handleFullscreenChange();
  loadState();
  startStream();
})();
