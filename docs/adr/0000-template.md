# Title of the Decision

*   **Status:** [Proposed | Accepted | Deprecated | Superseded]
*   **Date:** [YYYY-MM-DD]

## Context
*Describe the context and problem statement in a few sentences. What is the current situation? What is the technical or business problem you are trying to solve?*

*Example: "Our Spring Boot application processes 10,000 events per minute. Using JPA's `.saveAll()` is generating individual `UPDATE` statements, causing database connection pool exhaustion and slow execution times."*

## Decision
*State the architecture or technical decision clearly. What are you actually going to do?*

*Example: "We will bypass Spring Data JPA for bulk event updates and instead use Spring's `JdbcTemplate` with a `BatchPreparedStatementSetter` to execute bulk updates."*

## Consequences

*What happens after this decision is implemented? Include both positive benefits and negative trade-offs or technical debt.*

### Positive
*   *Benefit 1 (e.g., Memory consumption will drop significantly)*
*   *Benefit 2 (e.g., Database write times are reduced by 90%)*

### Negative / Risks
*   *Trade-off 1 (e.g., We lose Hibernate's automatic dirty checking for these updates)*
*   *Trade-off 2 (e.g., Developers must manually write native SQL strings, increasing the risk of syntax errors)*

## Alternatives Considered (Optional)
*List the other options you looked at and briefly explain why they were rejected.*

*   **[Option A]:** *(e.g., Hibernate Batching via application.properties)* - Rejected because it still required loading all 10,000 entities into memory, triggering the Garbage Collector.
*   **[Option B]:** *(e.g., Moving the logic to a Postgres Stored Procedure)* - Rejected because we want to keep business logic inside the Java layer.