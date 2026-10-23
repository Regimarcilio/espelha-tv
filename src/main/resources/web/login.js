(function () {
  'use strict';

  var form = document.getElementById('loginForm');
  var tokenInput = document.getElementById('token');
  var errorBox = document.getElementById('error');
  var submitButton = form.querySelector('button[type="submit"]');

  function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
  }

  function clearError() {
    errorBox.hidden = true;
    errorBox.textContent = '';
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    clearError();

    var token = tokenInput.value.trim();
    if (!token) {
      showError('Informe o token exibido no PC.');
      tokenInput.focus();
      return;
    }

    submitButton.disabled = true;
    submitButton.textContent = 'Entrando…';

    fetch('/api/login', {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'X-EspelhaTV': '1'
      },
      body: 'token=' + encodeURIComponent(token)
    })
      .then(function (response) {
        if (response.ok) {
          window.location.replace('/');
          return;
        }
        if (response.status === 429) {
          showError('Muitas tentativas. Aguarde um minuto e tente novamente.');
        } else if (response.status === 401) {
          showError('Token inválido. Confira o valor no terminal do PC.');
        } else {
          showError('Não foi possível entrar. Verifique a conexão com o PC.');
        }
        tokenInput.select();
      })
      .catch(function () {
        showError('Falha de rede. Confirme se o PC e a TV estão na mesma rede.');
      })
      .then(function () {
        submitButton.disabled = false;
        submitButton.textContent = 'Entrar';
      });
  });
})();
