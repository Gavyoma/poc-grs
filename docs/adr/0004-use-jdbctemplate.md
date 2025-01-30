# ADR 0003: Use JdbcTemplate for Batch Processing

* **Status:** Accepted
* **Date:** 2024-12-17

## Context

A scheduled job processes high-volume event batches. Initially, using Spring Data JPA (saveAll ()) caused significant
performance degradation due to:

- Memory/GC Overhead: Hibernate's Persistence Context (First-Level Cache) tracks all managed entities, leading to high
  heap consumption and frequent Garbage Collection.
- Inefficient I/O: Hibernate often defaults to individual INSERT/UPDATE statements over the network instead of utilizing
  JDBC batching (frequently caused by missing configurations or GenerationType.IDENTITY strategies).

## Decision

We will use Spring's `JdbcTemplate` instead of Spring Data JPA for bulk updates.

## Consequences

### Positive

- **Enhanced Throughput**: jdbcTemplate.batchUpdate leverages JDBC batching to bundle thousands of operations into a
  single network call, significantly reducing I/O latency.
- **Memory Efficiency:** Bypassing the JPA Persistence Context eliminates entity state tracking. Pushing data directly
  to the batch update keeps the memory footprint flat and prevents Garbage Collection (GC) spikes, regardless of batch
  size.

### Negative / Risks

- Loss of JPA Abstractions: Bypasses automatic dirty-checking, optimistic locking (@Version), and lifecycle callbacks
  (@PreUpdate/@PrePersist). Auditing and versioning must be handled manually within the SQL statements.
- Maintenance Overhead: Native SQL queries in Java lack compile-time safety and require manual synchronization with
  database schema changes.
- Domain Model Bypass: Batching directly to the database bypasses our DDD Aggregate Roots and domain invariants. We
  accept this pragmatic deviation for this specific job because the high-volume throughput requirements supersede strict
  domain encapsulation.

## Alternatives Considered

- Hibernate Batching (hibernate.jdbc.batch_size): Rejected. While it optimizes network I/O, it still requires hydrating
  and tracking all entities in the First-Level Cache, which fails to resolve the memory and GC bottlenecks.