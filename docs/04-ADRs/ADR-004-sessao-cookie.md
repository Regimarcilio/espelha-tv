# ADR-004 — Sessão por cookie em vez de token na URL

**Status:** aceito  
**Data:** 2026-10-23

## Contexto
O cliente precisa autenticar o stream MJPEG em um `<img>`, que não permite cabeçalhos customizados. Colocar o token de acesso na URL (`/stream?token=...`) é simples, mas vaza em logs, histórico e `Referer`. Também não há como fazer logout real.

## Decisão
- `POST /api/login` valida o token e cria uma **sessão opaca** (32 bytes aleatórios).
- O ID vai em cookie `HttpOnly`, `SameSite=Strict`, `Path=/`, com TTL.
- `/stream` e APIs leem o cookie.
- POSTs autenticados exigem `X-EspelhaTV: 1` para mitigar CSRF.
- Logout remove a sessão e expira o cookie.

## Alternativas
- **Token na query string:** simples, porém vaza e não expira.
- **Basic Auth:** funciona no `<img>`, mas expõe credenciais a cada requisição e não tem logout.
- **JWT:** desnecessário para uma única instância local; revogação é mais difícil.

## Consequências
- Sem token em URL/log.
- Sessão revogável e com TTL.
- Sem HTTPS no MVP, o cookie trafega em claro na LAN; usar apenas em rede confiável.
- O cliente precisa de `credentials: 'same-origin'` nos `fetch`.
