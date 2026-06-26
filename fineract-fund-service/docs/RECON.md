<!--
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements. See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership. The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License. You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied. See the License for the
 specific language governing permissions and limitations
 under the License.
-->

# Fineract — Fund bounded-context carve-out: Recon & Spec

Target repo: **Cognition-Partner-Workshops/fineract** (Java 21, Spring Boot 3.5, Gradle 8.14.3 wrapper, multi-module).
Goal: extract the **Fund** bounded context out of the monolith into a new standalone, gateway-routed
`fineract-fund-service` Gradle module with its **own database** (database-per-service), validated by an
auth-enforced parity/E2E smoke test through a new `fineract-gateway` module. **The monolith is read-only —
never modify monolith Fund code.** (Adding the new modules to `settings.gradle`/root `build.gradle` lists and
`fineract-war` is allowed wiring, not a monolith feature change.)

This is delivered as a **stacked chain of per-task PRs** (T0→T1→T2→T3→T4→T5), each branched off the previous
task's branch, merged in dependency order.

---

## 1. The Fund contract in the monolith (mirror this EXACTLY)

### Entity / table — `org.apache.fineract.portfolio.fund.domain.Fund` (in `fineract-core`)
```java
@Entity
@Table(name = "m_fund", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name"}, name = "fund_name_org"),
    @UniqueConstraint(columnNames = {"external_id"}, name = "fund_externalid_org") })
public class Fund extends AbstractPersistableCustom<Long> {
    @Column(name = "name")                       private String name;        // see length note below
    @Column(name = "external_id", length = 100)  private String externalId;
}
```
- PK: `id` BIGINT auto-increment (monolith uses `AbstractPersistableCustom<Long>` → column `id`).
- `name`: required, **max length 100** (enforced by validator), unique.
- `external_id`: optional, **max length 100**, unique.
- Physical `m_fund` table in the monolith (Liquibase `0001_initial_schema.xml`, port verbatim):
  `id BIGINT auto-increment PK`, `name VARCHAR(255)` unique, `external_id VARCHAR(100)` unique.
  (Note: the column `name` is physically `VARCHAR(255)`; the **validator** caps input at 100 chars. Mirror both:
  column 255, validation max 100.)

### DTOs
- `FundData` (read model): `Long id`, `String name`, `String externalId`. Static factory `instance(id,name,externalId)`.
- `FundRequest` (write model): `String name`, `String externalId`. (Lombok `@Data @NoArgsConstructor`.)

### API resource — `FundsApiResource` (`fineract-provider`), JAX-RS `@Path("/v1/funds")`
| Method | Path | Behavior |
|--------|------|----------|
| GET | `/v1/funds` | list all funds, **ordered by name** → `List<FundData>` |
| POST | `/v1/funds` | create fund from `FundRequest` → returns created id |
| GET | `/v1/funds/{fundId}` | retrieve one → `FundData`; **404** `FundNotFoundException` if missing |
| PUT | `/v1/funds/{fundId}` | update name/externalId → returns changes |

Read endpoints call `context.authenticatedUser().validateHasReadPermission("FUND")`. Writes go through the
Maker-Checker command bus (`PortfolioCommandSourceWritePlatformService` / `CommandWrapperBuilder.createFund()/updateFund()`).

### Validation — `FundCommandFromApiJsonDeserializer`
- Supported params: `name`, `externalId` (reject unknown params).
- Create: `name` notBlank + max 100; `externalId` max 100.
- Update: same checks, only when each param is present.
- Errors → `PlatformApiDataValidationException` (`validation.msg.validation.errors.exist`).
- Duplicate `name` → `error.msg.fund.duplicate.name`; duplicate `externalId` → `error.msg.fund.duplicate.externalId`
  (data-integrity → HTTP 409/403 family in monolith).

### Cross-context coupling — NONE
The `Fund` entity imports **zero** other portfolio/accounting contexts. Inbound: only Loan/LoanProduct hold an
optional `fund_id` (they are NOT part of this carve-out and must not be touched). So there are **no cross-context
navigation properties to drop** — the only thing to carve AROUND is the shared infrastructure below.

---

## 2. Shared infrastructure to carve AROUND (do NOT drag into the service)

These are forbidden dependencies for the new service — the service must be self-contained:
- **Command/Maker-Checker bus**: `org.apache.fineract.commands.*` (`PortfolioCommandSourceWritePlatformService`,
  `CommandWrapper`, `CommandWrapperBuilder`, `CommandProcessingService`). Replace with a direct service call.
- **Monolith shared modules as code deps**: do NOT add `fineract-provider`, `fineract-core`, `fineract-command`,
  or any `fineract-*` domain module to the service's `build.gradle`. (`AbstractPersistableCustom`, `JsonCommand`,
  `PlatformSecurityContext`, `FromJsonHelper`, `ThreadLocalContextUtil`, monolith `@Cacheable` tenant keys, etc.
  must NOT be referenced — re-implement the thin slice with plain Spring/JPA.)
- **Shared DB**: the service must NOT use the monolith schema/datasource. It owns its own database.
- **Other bounded contexts**: no references to `portfolio.loan*`, `portfolio.client*`, `portfolio.group*`,
  `portfolio.charge*`, `portfolio.tax*`, `accounting.*`, `useradministration.*`, `organisation.*`.

Forbidden symbols (zero references allowed in `fineract-fund-service`):
`PortfolioCommandSourceWritePlatformService`, `CommandWrapper`, `CommandWrapperBuilder`, `JsonCommand`,
`AbstractPersistableCustom`, `PlatformSecurityContext`, `ThreadLocalContextUtil`, and any `import org.apache.fineract.{commands,portfolio.loan*,portfolio.client*,portfolio.group*,portfolio.charge*,portfolio.tax*,accounting,useradministration,organisation}`.

---

## 3. Target architecture (inside the fineract repo)

### Module: `fineract-fund-service/` — standalone Spring Boot application
- Package base: `org.apache.fineract.fundservice` (fresh package to avoid clashing with monolith `portfolio.fund`).
- Bootable: `@SpringBootApplication` main class `FundServiceApplication`; `bootRun`/`bootJar` enabled.
- Stack: Spring Boot Web (MVC) + Spring Data JPA (Hibernate) + Spring Security + Liquibase + springdoc-openapi.
  DB driver: PostgreSQL (matches the compose DBs used elsewhere in the repo).
- REST contract mirrors the monolith (Spring MVC `@RestController`, base path `/v1/funds`):
  - `GET /v1/funds`, `POST /v1/funds`, `GET /v1/funds/{fundId}`, `PUT /v1/funds/{fundId}`.
  - 404 when fund missing; 400 on validation failure (name blank / >100, externalId >100, unknown field);
    409 on duplicate name/externalId.
- Entity `Fund` → `m_fund` with the columns/constraints from §1 (own JPA entity, plain `@Id @GeneratedValue`).
- Security: **HTTP Basic**, every `/v1/**` endpoint requires authentication → **401 when unauthenticated**.
  `/actuator/health` and the swagger/openapi docs endpoints are permitted without auth (health must return 200).
  Use a single configured in-memory user for the smoke test (e.g. username `mifos`, password from a property/env;
  default `password` for local/compose) — do NOT pull in `useradministration`.
- Own database: dedicated datasource (`fineract_fund` DB) + a Liquibase changelog that creates `m_fund`
  (name VARCHAR(100), external_id VARCHAR(100), unique indexes `fund_name_org` and `fund_externalid_org`).
- Default service port: **8090**.

### Module: `fineract-gateway/` — standalone Spring Cloud Gateway application
- Package base: `org.apache.fineract.gateway`; main class `GatewayApplication`.
- Routes the monolith-style external path to the fund service (parity URL):
  - `/fineract-provider/api/v1/funds/**` → fund-service `http://<fund-service-host>:8090/v1/funds/**`
    (RewritePath strips the `/fineract-provider/api` prefix). Make the downstream URI configurable via property
    `fund-service.uri` (default `http://localhost:8090` for local, `http://fund-service:8090` for compose).
- Gateway listens on port **8080**; exposes `/actuator/health` (200).
- Auth is enforced by the downstream service (gateway just forwards the `Authorization` header), so a request
  through the gateway without credentials must surface the service's **401**.

### Gradle wiring (do this so modules build but stay self-contained)
- Add `include ':fineract-fund-service'` and `include ':fineract-gateway'` to `settings.gradle`.
- Do **NOT** add the new modules to the `fineractJavaProjects` / `fineractPublishProjects` allowlists in root
  `build.gradle` (those apply the heavy provider/static-weaving/eclipselink config). Each new module gets its own
  self-contained `build.gradle` applying `org.springframework.boot` + `io.spring.dependency-management` and its
  own deps.
- `allprojects {}` still applies to every module: **spotless (Google Java Format)**, **Apache license header**
  (license + RAT plugins), errorprone. So: every source/resource file needs the Apache license header, and you
  must run `./gradlew :fineract-fund-service:spotlessApply :fineract-gateway:spotlessApply` before committing.
  `static-weaving.gradle` is applied to all subprojects via `subprojects {}` — ensure it's a no-op for these
  modules (they use Hibernate, not EclipseLink weaving); if it interferes, guard/skip it for these modules.
- Build/verify per-module to avoid building the whole monolith:
  `./gradlew :fineract-fund-service:build :fineract-gateway:build` (use `--offline` if network-restricted; the
  Gradle 8.14.3 wrapper is committed).

### Database-per-service & compose / E2E
- Provide a compose file (e.g. `fineract-fund-service/docker-compose-fund-service.yml`) that stands up:
  `fund-db` (postgres, DB `fineract_fund`), `fund-service` (8090, runs Liquibase on boot), `gateway` (8080).
- E2E/parity smoke test (the **primary validation gate** — there is no GitHub Actions CI in this fork) asserts,
  **through the gateway (8080)**:
  1. `GET /actuator/health` → 200 (gateway up).
  2. `GET /fineract-provider/api/v1/funds` **without** auth → **401** (route forwards + auth enforced).
  3. (bonus) with valid Basic auth → 200 and CRUD round-trip (POST then GET by id) matches the contract.

---

## 4. CI status
No GitHub Actions workflows exist in this fork (only `.github/dependabot.yml` + PR template; forked workflows
were intentionally removed). Therefore the **E2E smoke test in T5 is the primary validation gate.** T5 may add a
minimal, path-scoped GitHub Actions workflow that ONLY builds the two new modules and runs the compose smoke
test — but it must not re-enable the heavy upstream Apache CI.

## 5. Branch / PR chain (stacked, dependency order)
- T0 branch off `main`. T1 off T0. T2 off T1. T3 off T2. T4 off T3. T5 off T4.
- Each PR targets its predecessor's branch. Merge order: T0 → T1 → T2 → T3 → T4 → T5 → main.

## 6. PR hygiene (org rule)
Do NOT put any user-identifying info (no emails) in PR descriptions/commits. A session link is auto-appended.
