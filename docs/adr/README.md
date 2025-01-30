# Architecture Decision Records (ADRs)

This folder contains the architectural decisions made for this project. We use ADRs to document the *why* behind our
technical choices so that future contributors have historical context.

For more information on this standard, see [adr.github.io](https://adr.github.io/).

## How to add a new ADR

1. Copy the template from `0000-template.md`.
2. Name your file with the next available sequential number and a descriptive title (e.g.,
   `0022-use-redis-for-caching.md`).
3. Fill out the context, decision, and consequences.
4. Submit it as part of your Pull Request.

*Note: ADRs are immutable. If a past decision is being changed, do not delete the old ADR. Create a new one and mark the
old one as "Superseded".*

---

## Decision Log

| ID   | Title                                                                                                    | Status   | Date       |
|:-----|:---------------------------------------------------------------------------------------------------------|:---------|:-----------|
| 0001 | [Adopt Domain-Driven Design (DDD)](0001-adopt-domain-driven-design.md)                                   | Accepted | 2024-12-17 |
| 0002 | [Use UUIDv7 for Entity Primary Keys](0002-use-uuidv7-for-entity-primary-keys.md)                         | Accepted | 2024-12-17 |
| 0003 | [Encrypted Cursors with the Binary Header Pattern](0003-encrypted-cursors-with-binary-header-pattern.md) | Accepted | 2024-12-17 |
| 0004 | [Use JdbcTemplate for Batch Processing](0004-use-jdbctemplate.md)                                        | Accepted | 2024-12-17 |
