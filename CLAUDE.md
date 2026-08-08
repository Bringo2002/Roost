# CLAUDE.md — `Roost` Engineering Constitution

This file is binding operating instructions for any AI coding agent working in this repository. Read it in full at the start of every session. Every directive below is a rule, not a suggestion.

---

## 1. Scope of This Repository

This repository (`Roost`) owns **Layer 1 — Data/Infra** and **Layer 2 — API/Business Logic** of the Roost platform. It is a Spring Boot 3.5 application running on Java 21, built with Maven, backed by PostgreSQL, and deployed on Railway.

The sibling repository `roost_app` owns Layer 3 (Flutter client). It is **entirely out of scope** for any agent session here.

**Never open, reference, import, or attempt to edit Dart or Flutter files — they do not exist in this repository.**

### API Contract Obligation to Layer 3

Layer 3 (`roost_app`) depends on the **DTO/JSON response contracts** this repo's controllers expose. The Dart data models in that repo mirror these contracts field-for-field.

- Any **breaking change** to a response shape (field renamed, removed, type changed, nested structure altered) must be called out explicitly in the PR description with the label **`⚠️ BREAKING RESPONSE CONTRACT`** so the frontend repo can react in lockstep.
- **Additive changes** (new optional fields with JSON-serializable defaults) are non-breaking and do not require frontend coordination, but must still be noted in the PR description.
- Never assume "the frontend will just handle it." If a response shape changes, Layer 3 must update.

---

## 2. Context Isolation & Stacked PR Workflow (Layers 1–2 Only)

### Layer 1 — Data/Infra

Schema migrations, JPA `@Entity` classes, Spring Data repositories.

| Concern | Location |
|---|---|
| Schema migrations | `src/main/java/com/roost/config/DatabaseSchemaMigrator.java` |
| Data seeding | `src/main/java/com/roost/config/DataSeeder.java`, `src/main/resources/data.sql` |
| Database config | `src/main/java/com/roost/config/DatabaseConfig.java` |
| JPA entities | `src/main/java/com/roost/model/` |
| Spring Data repositories | `src/main/java/com/roost/repository/` |

**Permitted read/write for a Layer 1 task:** All files listed above, plus `src/main/resources/application.properties` and `src/main/resources/application-production.properties` for datasource/JPA configuration, and `pom.xml` if a new dependency is required.

**Out of scope for a Layer 1 task:** `src/main/java/com/roost/controller/`, `src/main/java/com/roost/service/`, `src/main/java/com/roost/dto/`, `src/main/java/com/roost/security/`, `src/main/java/com/roost/exception/`. Do not touch these directories unless the task explicitly spans both layers.

### Layer 2 — API/Business Logic

Service classes, DTOs, REST controllers, security config, exception handling.

| Concern | Location |
|---|---|
| Services | `src/main/java/com/roost/service/` |
| DTOs | `src/main/java/com/roost/dto/` |
| REST controllers | `src/main/java/com/roost/controller/` |
| Security (JWT, Spring Security) | `src/main/java/com/roost/security/` |
| Exception handling | `src/main/java/com/roost/exception/` |
| Application entry point | `src/main/java/com/roost/RoostApplication.java` |

**Permitted read/write for a Layer 2 task:** All files listed above, plus `pom.xml` if a new dependency is required, and application properties files. Layer 2 may **read** entity and repository files in `model/` and `repository/` but must not modify them unless the task explicitly spans both layers.

**Out of scope for a Layer 2 task:** `DatabaseSchemaMigrator.java`, `data.sql`, entity field additions, new repository methods. These are Layer 1 changes. A Layer 2 branch must never introduce a schema change directly — that always belongs in a Layer 1 branch first.

### Dependency Rule

A Layer 2 branch must branch from a merged (or currently-open, if stacking) Layer 1 branch. Layer 2 must never introduce a schema change directly.

### Branch Topology

```
Roost (this repo)                              roost_app (sibling)
===================                            ===================

main
  │
  ├─ feat/x-layer1-schema
  │    │
  │    └─ feat/x-layer2-api ──(API contract)──► feat/x-layer3-client
  │
  └─ feat/y-layer1-schema
       │
       └─ feat/y-layer2-api ──(API contract)──► feat/y-layer3-client
```

**Branch naming scheme for this repo:**

```
feat/<feature-name>-layer1-schema     # DB migration, entity, repository
feat/<feature-name>-layer2-api        # Service, DTO, controller
fix/<bug-name>-layer1-schema
fix/<bug-name>-layer2-api
refactor/<scope>-layer1-schema
refactor/<scope>-layer2-api
```

This repo's tooling manages only its own branches. The conceptual arrow to `roost_app`'s Layer 3 branch is a coordination note — this repo never creates or pushes to branches in the sibling.

---

## 3. Git Tooling for Stacked Branches

Use **Graphite's `gt` CLI** for managing stacked PRs against GitHub.

```bash
# Initialize Graphite in the repo (run once)
gt repo init

# Create the Layer 1 branch
gt branch create feat/x-layer1-schema

# Stack the Layer 2 branch on top of Layer 1
gt branch create feat/x-layer2-api

# Sync the stack after the lower layer changes (rebase all stacked branches)
gt stack restack

# Submit the entire stack as linked PRs to GitHub
gt stack submit
```

> <VERIFY: Confirm `gt repo init`, `gt branch create`, `gt stack restack`, and `gt stack submit` match the current Graphite CLI version installed in this environment. Graphite may have renamed or restructured subcommands since the CLI's last major release.>

---

## 4. System Command Reference — Spring Boot

All commands assume the working directory is the repository root. This project uses the Maven wrapper (`./mvnw`).

### Run Development Server

```bash
./mvnw spring-boot:run
```

With a specific profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=production
```

### Build

```bash
# Clean build, skip tests (for quick compilation check)
./mvnw clean package -DskipTests

# Clean build with tests
./mvnw clean package
```

### Testing

```bash
# Run the full test suite
./mvnw test

# Run a single test class
./mvnw test -Dtest=RoostApplicationTests

# Run a single test method
./mvnw test -Dtest=RoostApplicationTests#contextLoads
```

### Schema Migrations

This project uses a custom `CommandLineRunner`-based migrator (`DatabaseSchemaMigrator.java`) rather than Flyway or Liquibase. Migrations are applied automatically on application startup.

```bash
# To apply pending migrations, simply start the application:
./mvnw spring-boot:run

# There is no standalone migration command — migrations run as part of boot.
```

When adding a new column or constraint:
1. Add the `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` statement to `DatabaseSchemaMigrator.run()`.
2. Add the corresponding field to the JPA `@Entity` class in `src/main/java/com/roost/model/`.
3. If the column has a non-null primitive type, add an `enforceNotNull()` call to backfill existing rows.

### Dependency Checks

```bash
# Check for outdated dependencies
./mvnw versions:display-dependency-updates

# Check for outdated plugin versions
./mvnw versions:display-plugin-updates
```

---

## 5. Engineering Standards at 20M+ Scale — Backend

### DTO-Only Responses (Current State + Migration Rule)

**Current state:** Several controllers (notably `PropertyController`) return raw JPA `@Entity` objects directly. This is a known debt — it leaks internal schema details, Hibernate proxy behavior, and `@Transient` fields into the API contract.

**Rule going forward:** Every new endpoint must return a DTO, not an entity. When modifying an existing endpoint that currently returns an entity, introduce a DTO in `src/main/java/com/roost/dto/` and map through it. Do not add new fields to an entity with the expectation that Jackson serialization of the entity IS the API contract — route through a DTO so the response shape is explicitly controlled.

### Mandatory Pagination on List Endpoints

**Current state:** List endpoints (`getAllProperties`, `getNearby`, `filterProperties`) return unbounded `List<Property>`. This is a known debt at 20M+ scale.

**Rule going forward:**

- Every new list endpoint must use `Pageable` and return `Page<T>` or `Slice<T>`.
- Default page size: **20 items**. Maximum page size: **100 items**. Enforce the max in the controller or via a `PageableDefault`/`PageableHandlerMethodArgumentResolver` configuration.
- When modifying an existing unbounded list endpoint, add pagination. Do not introduce new unbounded list endpoints.

### N+1 Query Prevention

- Before merging any PR that introduces or modifies a repository query returning an entity with `@ManyToOne`, `@OneToMany`, `@ManyToMany`, or `@ElementCollection` relationships, verify that the query does not cause N+1 selects. This project's `Property` entity has `@ManyToOne owner` and `@ElementCollection imageUrls` — both are N+1 risks on list queries.
- Use `@Query` with explicit `JOIN FETCH` or `@EntityGraph` annotations for any query that returns multiple entities with eager-loaded relationships. Do not rely on derived query methods (e.g. `findByStatus`) for queries involving joined entities at list scale — they do not control fetch strategy.
- Enable `spring.jpa.show-sql=true` (already set in `application.properties`) during development. If a single request produces more than **2 SQL statements** for a list query (one for the primary entity, one for the collection), investigate and fix.

### Connection Pool Sizing

HikariCP is the default connection pool. Configure per environment:

```properties
# <PLACEHOLDER: Set per environment based on expected concurrent load>
# Rule of thumb: pool size = (2 * CPU cores) + number of effective spindle disks
# For Railway's managed PostgreSQL, start with 10 and adjust based on metrics.
spring.datasource.hikari.maximum-pool-size=<POOL_SIZE>
spring.datasource.hikari.minimum-idle=<MIN_IDLE>
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

### JVM Garbage Collection

Target: GC pause budget of **< 10 ms** p99 for API request serving.

```
# <PLACEHOLDER: Choose collector and flags per deployment>
# At 20M+ user scale with latency-sensitive API traffic:
#   - ZGC (-XX:+UseZGC -XX:+ZGenerational) for sub-millisecond pauses
#   - OR G1GC (-XX:+UseG1GC -XX:MaxGCPauseMillis=10) for balanced throughput
# Set in Dockerfile ENTRYPOINT or Railway environment variables.
```

The current `Dockerfile` and `Procfile` do not specify a collector — Java 21 defaults to G1GC. Add explicit flags when deploying at scale.

### API Versioning Rule

- **Additive-only changes** (new optional fields in a response DTO) are non-breaking and land on the existing endpoint.
- **Breaking changes** (field removed, renamed, type changed, nested structure restructured) must follow this process:
  1. Create a new versioned endpoint (e.g. `/api/v2/properties`) or a new DTO version.
  2. Keep the old endpoint functional for at least one release cycle.
  3. Mark the PR with **`⚠️ BREAKING RESPONSE CONTRACT`** in the description.
  4. Coordinate with `roost_app` before merging — never land a breaking change on `main` without a corresponding frontend PR ready.
- Never silently change a response shape and assume the client will adapt.

### Observability (Target State)

**Current state:** No Spring Boot Actuator or OpenTelemetry dependencies exist in `pom.xml`.

**Rule going forward:**

- When adding Actuator: expose `/actuator/health`, `/actuator/info`, and `/actuator/metrics` at minimum. Protect all other Actuator endpoints behind `ADMIN` authority.
- When adding tracing: instrument all `@Service` methods with OpenTelemetry spans. Include the operation name, entity ID, and user ID (if authenticated) as span attributes.
- Every new `@Service` method must be traceable — do not introduce service logic that is invisible to the observability stack.

### Request Validation

- Validate all `@RequestBody` inputs. Use Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`, `@Size`, etc.) on DTO fields rather than manual null checks scattered through controller methods.
- For existing controller methods that do manual validation (e.g. `if (user == null) return 401`), do not remove them — but new endpoints must use Bean Validation.

---

## 6. Testing Standards — Backend

### TDD Is Mandatory

Do not write implementation code without a preceding failing test. The cycle is: **Red → Green → Refactor.**

**Current state:** The test suite has a single file (`RoostApplicationTests.java`) with a context-load test. This is known debt. Every new feature must include proper tests going forward.

### Minimum Coverage Requirements

| Layer | Required tests |
|---|---|
| **Layer 1 — Repositories** | Integration tests using `@DataJpaTest` with an embedded or containerized PostgreSQL (Testcontainers). Test all custom `@Query` methods. Test `DatabaseSchemaMigrator` migration logic against a real database — never mock JDBC for migration tests. |
| **Layer 1 — Entities** | Unit tests for any non-trivial entity logic (computed fields, validation methods). |
| **Layer 2 — Services** | Unit tests with mocked repositories (Mockito `@Mock` + `@InjectMocks`). Test business logic paths: success, validation failures, authorization failures, edge cases. Test every branch in methods like `recomputeVerification`, `reportProperty` (threshold crossing), `submitCommunityCheck`. |
| **Layer 2 — Controllers** | Integration tests using `@SpringBootTest` + `MockMvc` for every controller endpoint. Test: correct HTTP status codes, response body structure, authorization enforcement (authenticated vs. unauthenticated, role-based access), and error responses. |
| **Layer 2 — Security** | Test that unauthenticated requests to protected endpoints return 401. Test that non-ADMIN users cannot access `/api/admin/**`. Test that JWT expiration and malformation are handled correctly. |

### Test File Placement

Mirror the main source structure under `src/test/java/com/roost/`:

```
src/main/java/com/roost/service/PropertyService.java
  → src/test/java/com/roost/service/PropertyServiceTest.java

src/main/java/com/roost/controller/PropertyController.java
  → src/test/java/com/roost/controller/PropertyControllerIntegrationTest.java

src/main/java/com/roost/repository/PropertyRepository.java
  → src/test/java/com/roost/repository/PropertyRepositoryTest.java
```

### Running Tests Before Committing

Run the full suite before every commit:

```bash
./mvnw test
```

Must pass with zero failures. Do not use `-DskipTests` in any commit-related workflow.

---

## 7. AI Behavioral Policy

### Code Output Rules

When producing code for this repository:

- No introductory pleasantries, no apologies, no meta-commentary about what you are about to do.
- Output direct, fully-typed, production-ready file diffs or new files only.
- Every Java file must compile cleanly with `./mvnw compile`.
- Every new public method must include a Javadoc comment.
- Never output pseudocode, partial implementations, or `// TODO: implement` stubs without immediately following them with the real implementation.

### Layer Boundary Enforcement

- Never silently introduce a Layer 1 schema change (new column, new entity, migration statement) while working a Layer 2 task. If the Layer 2 work requires a schema change, **stop and state explicitly**: "This task requires a Layer 1 change first. Create a `feat/<name>-layer1-schema` branch before proceeding."
- Never modify `DatabaseSchemaMigrator.java` from a Layer 2 branch.

### When to Stop and Ask

This behavioral policy governs code-output format specifically. It does **not** suppress the agent's judgment about:

- **Asking a clarifying question** when a task is genuinely ambiguous (e.g. unclear whether a new field belongs on the entity or only on a DTO, or which role should have access to a new endpoint).
- **Flagging a frontend dependency** when a response shape change will affect the sibling `roost_app` repository. State this clearly: "This change alters the response contract for `<endpoint>`. The `roost_app` Dart model `<ModelName>` must be updated in lockstep."
- **Refusing scope creep** when a task targets one layer but would require cascading changes into the other layer not mentioned in the task.
