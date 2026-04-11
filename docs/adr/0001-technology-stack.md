# ADR-0001: Technology Stack

**Status**: Accepted
**Date**: 2026-04-10
**Deciders**: Engineering

## Context

Webizon must support 60,000 concurrent viewers per session with sub-250ms chat delivery, while being cost-effective for a pay-per-use SaaS. The stack must be buildable by a Java-fluent team and sustainable long-term.

## Decision

- **Backend API**: Java 21 + Spring Boot 3.2 with virtual threads (Project Loom) enabled
- **Real-time**: Centrifugo v5 as a standalone WebSocket server
- **Primary DB**: PostgreSQL 16 + PgBouncer
- **Analytics DB**: ClickHouse
- **Cache / pub-sub**: Redis 7 (Cluster mode in production)
- **Message bus**: Apache Kafka
- **Object storage**: S3-compatible (MinIO in dev)
- **Auth**: Keycloak 24
- **Frontend**: Nuxt 4 + Vue 3 + TypeScript strict mode

## Consequences

**Positive**
- The WebSocket fan-out bottleneck is fully offloaded to Centrifugo, which is purpose-built for it (1M+ connections per node).
- Spring Boot 3 + virtual threads lets us keep blocking-style code while getting non-blocking throughput on I/O.
- Team already knows Java, Spring, PostgreSQL — fast onboarding.
- ClickHouse handles the 100k events/sec analytics firehose that PostgreSQL cannot.
- Each component is independently scalable (stateless API, Centrifugo cluster, read-replica DB).

**Negative**
- More moving parts than a pure Spring + PostgreSQL monolith. Operational burden is higher.
- Centrifugo is Go; we must learn its config and operational characteristics.
- ClickHouse is a new tech for the team.
- Two databases (PG + ClickHouse) means two sources of truth for certain metrics; reconciliation required.

## Alternatives Considered

### Alternative 1: Pure Java/Spring with in-memory STOMP broker
- **Rejected**: Cannot scale beyond ~1000 concurrent WebSocket connections per node without horizontal pain. Multi-node requires an external broker anyway.

### Alternative 2: Go (Fiber / Echo) for the API, Centrifugo for real-time
- **Rejected**: Team lacks Go expertise. Time-to-market matters more than the 5–10x memory efficiency at this stage. Can revisit later.

### Alternative 3: Node.js + Socket.IO with Redis adapter
- **Rejected**: Node.js is single-threaded (cluster mode adds complexity). Team expertise is weaker. Type safety is lower (TypeScript helps but not to the same degree as Java).

### Alternative 4: RabbitMQ as STOMP broker relay (from Spring)
- **Rejected**: RabbitMQ's STOMP support is adequate but not purpose-built for fan-out at 60k/session. Centrifugo beats it on throughput, memory, and operational simplicity for this exact use case. RabbitMQ remains an option for general messaging if Kafka proves excessive.

## References
- Centrifugo benchmarks: https://centrifugal.dev/docs/getting-started/benchmarks
- Project Loom announcement: https://openjdk.org/jeps/444
- ClickHouse comparison with alternatives: https://clickhouse.com/docs/en/faq/general/why-clickhouse-is-so-fast
