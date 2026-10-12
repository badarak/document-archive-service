# Development Guidelines

## Output / Communication

Keep terminal responses concise.

Do not dump large amounts of text into the terminal.

For small changes:
- Explain the problem briefly.
- Explain the solution briefly.
- List modified files.
- List tests executed.

For large analyses:
- Write the detailed analysis into `docs/`.
- Prefer Markdown files.
- Do not print the entire report in the terminal.

Recommended response format:

1. Summary
2. Changes
3. Tests
4. Remaining issues

Maximum default terminal response:
- 30 lines for normal tasks
- 50 lines for architecture or debugging tasks

If the analysis is larger than this, create a Markdown report.

## Stack

- Kotlin sur JVM 21
- Spring Boot 4
- Maven
- PostgreSQL
- Liquibase
- JUnit 5
- MockK

## Architecture

Use hexagonal architecture.

Dependency direction:

domain <- application <- infrastructure

The domain must remain framework independent.

The domain must not depend on:

- Spring
- Spring Data
- JPA
- Hibernate
- PostgreSQL

## Domain

Business rules belong in the domain.

Avoid putting business logic in:

- controllers
- repositories
- configuration classes

## Infrastructure

Infrastructure contains:

- REST controllers
- JPA entities
- Spring Data repositories
- external API clients
- messaging adapters

## Tests

Every new use case must have tests.

Use:

- JUnit 5
- Mockk

Prefer unit tests for business logic.

## Code quality

Follow:

- SOLID
- Clean Code
- DRY
- KISS

Avoid premature abstractions.

## Git

Never commit or push without explicit authorization.

## Validation

After modifications:

1. run tests
2. run Maven verification
3. inspect git diff
4. summarize changes