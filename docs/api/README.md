# API contract — GraphQL schema (SDL)

This gateway is a **GraphQL** service (not REST), so its public API contract is the
GraphQL **Schema Definition Language (SDL)**, not an OpenAPI document.

## `schema.graphqls` — the canonical, served contract

[`schema.graphqls`](schema.graphqls) is the **fully-resolved schema as the running
gateway serves it** — i.e. exactly what a client sees via introspection or the SDL
printer endpoint. It therefore includes:

- the 7 `Query` entry points + 1 `Mutation` that front the 9 downstream services,
- every object type / enum / Relay connection, with descriptions preserved, and
- the GraphQL built-in directives (`@defer`, `@deprecated`, `@include`, `@skip`,
  `@specifiedBy`, `@oneOf`, `@experimental_disableErrorPropagation`) that graphql-java
  adds to the runtime schema.

This is the resolved contract, which is a superset of the hand-written source schema in
[`gateway-bootstrap/src/main/resources/graphql/schema.graphqls`](../../gateway-bootstrap/src/main/resources/graphql/schema.graphqls)
(the source has no built-in directives and a different type ordering). The source file
remains the thing you edit; this file is the generated, authoritative client-facing view.

## How it is generated (zero external infrastructure)

The `demo` Spring profile boots the gateway with in-memory stub adapters and JWT auth
disabled, so no downstream service, database, or broker is required:

```bash
./gradlew :gateway-bootstrap:bootRun --args='--spring.profiles.active=demo --server.port=8080'
# then, once it serves:
curl -fsS http://localhost:8080/graphql/schema > docs/api/schema.graphqls
```

The SDL is exposed because `application.yml` sets
`spring.graphql.schema.printer.enabled: true`, which serves the resolved SDL at
`GET /graphql/schema`. The output is deterministic (graphql-java sorts types and fields
alphabetically), so re-generating yields byte-identical bytes.

## Drift gate (CI)

The [`schema-contract` job in `.github/workflows/ci.yml`](../../.github/workflows/ci.yml)
boots the gateway in the same zero-infra `demo` mode, re-fetches `/graphql/schema`, and
runs `git diff --exit-code docs/api/schema.graphqls`. If a schema change is not
reflected in this committed file, CI fails — keeping the published contract honest.
