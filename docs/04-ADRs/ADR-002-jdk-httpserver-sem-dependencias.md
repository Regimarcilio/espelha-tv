# ADR-002 — `com.sun.net.httpserver` e zero dependências de runtime

**Status:** aceito  
**Data:** 2026-10-23

## Contexto
O projeto é didático e precisa rodar com o mínimo de instalação: um JDK 21 e o JAR. Dependências externas (Spring, Javalin, Netty) aumentam o download, a superfície de ataque e a complexidade de build. O volume de requisições é baixo (poucos clientes, LAN) e o stream é HTTP puro.

## Decisão
Implementar o servidor com o **`com.sun.net.httpserver.HttpServer` do JDK**, virtual threads para concorrência e **JUnit 5 apenas para testes**. Não usar WebSocket no MVP; input via `POST` form-urlencoded.

## Alternativas
- **Javalin/Jetty:** API mais ergonômica e WebSocket pronto; custo de dependências.
- **Spring Boot:** produtivo, mas pesado para o escopo.
- **Netty:** poderoso, porém complexo para um MVP.

## Consequências
- Build simples e JAR executável sem classpath externo.
- Precisa implementar parsing de formulário, cookies e roteamento à mão (poucas linhas).
- `HttpServer` é suficiente para MJPEG e APIs; se o projeto crescer, migrar para Javalin/Jetty é localizado no `HttpApi`.
