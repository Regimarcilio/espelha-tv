# ADR-003 — Espectador por padrão e controlador único

**Status:** aceito  
**Data:** 2026-10-23

## Contexto
Tela + teclado = controle total do PC. Em uma TV, o controle remoto é fácil de apertar por acidente. Vários espectadores podem querer controlar ao mesmo tempo, gerando disputa de mouse/teclado. O requisito do usuário é "poder somente visualizar sem interferência".

## Decisão
- Toda sessão nasce **espectadora**.
- Input só é aceito com um **token de controle** efêmero.
- **Um único controlador** por vez; o segundo recebe `409`.
- `claim` é explícito; `release`, timeout de 5 min, logout, expiração ou desconexão liberam o controle.
- Ao liberar, executar `releaseAll()` para não deixar teclas presas.
- O servidor ignora input de espectador, mesmo que o cliente envie o payload.

## Alternativas
- **Controle livre para todos:** simples, mas inaceitável para "sem interferência".
- **Controle por último input:** imprevisível e sujeito a disputa.
- **Fila de controladores:** útil no futuro, mas complexity desnecessária no MVP.

## Consequências
- UX previsível e segura.
- Um espectador pode pedir controle quando o controlador liberar.
- O estado do controle precisa ser consultado periodicamente (`/api/state`).
- A variante "push-to-control" (só envia input enquanto o botão está pressionado) fica como evolução.
