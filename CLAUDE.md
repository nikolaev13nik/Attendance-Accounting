# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## System context

This repo is the **Accounting** microservice (`co.il.attendanceaccounting`) in a system of ~5 services
behind an API Gateway / service discovery layer (see
`../attendance-project-common/docs/TimeTracker_docs.md` for the overall design). It owns **user accounts,
roles and authentication**, and it is the only service that *issues* JWTs — every sibling service
(notably `../Attendance-TimeTracking`) only validates them. Attendance records, working hours, statistics
and any Kafka messaging belong to those other services: keep time-tracking or reporting logic out of this
repo (commit `c5cc565` deliberately removed such logic from here).

Because this service mints the tokens the rest of the system trusts, changes to the JWT claims
(`sub`, `authorities`, `tenantId`) or to the signing algorithm are **breaking changes for every other
service** — `Attendance-TimeTracking`'s `SecurityConfiguration`/`TenantInterceptor` read exactly these
claims. Coordinate any such change across repos.

## Commands

```bash
./mvnw clean install              # build (also regenerates the OpenAPI interface/DTOs, see below)
./mvnw test                       # run all tests
./mvnw test -Dtest=UserAccountControllerTest                      # run one test class
./mvnw test -Dtest=UserAccountControllerTest#registerNewUserTest  # single test method
./mvnw spring-boot:run            # run locally (H2 file DB at ./data/attendance-accounting)
```

Requires env var `ATTENDANCE_ACCOUNTING_JWT_SECRET` — the HMAC signing secret, **at least 32 bytes** or
the context fails to start (`SecurityConfiguration.jwtSecretKey`). Tests supply their own via
`src/test/resources/application.properties`.

There is no linter/formatter, no CI workflow, no Dockerfile and no coverage gate in this repo (the shared
IntelliJ Google-style config lives in `../attendance-project-common/sharedFiles/`). `Attendance-TimeTracking`
has all four — copy from there if asked to add them, rather than inventing a different setup.

## Architecture

### API is contract-first (OpenAPI → generated interface) — except `/account/login`

`src/main/resources/swagger-source/attendance-accounting-account.yaml` is the source of truth for the REST
API. The `openapi-generator-maven-plugin` (bound to `generate-sources`) generates the `AccountApi`
interface (`co.il.attendanceaccounting.api`) and DTOs (`co.il.attendanceaccounting.dto`) into
`target/generated-sources`. `UserAccountController` implements `AccountApi`. **To add/change an endpoint,
edit the yaml first**, then rebuild so the interface regenerates, then implement the method. Bean
validation annotations on the DTOs come from the spec (`performBeanValidation`), so input rules like
"email must be a valid email" are declared there, not in Java.

`POST /account/login` is the one exception: it is hand-written as a `@PostMapping` on the controller and
is **not in the spec**. If you touch the login contract, keep in mind nothing regenerates it and no
client codegen sees it.

**Spec consumers exist outside this repo.** `Attendance-TimeTracking` keeps a manually synced copy of this
yaml to generate its outbound client, and that copy is already stale (it still has a placeholder
`GET /account/user/{idUser}`, while this repo has since published
`GET /account/tenant/{tenantId}/userId/{idUser}`). When changing this spec, update that copy too.

### Layering

Thin and conventional — controller → service interface + `Impl` → Spring Data repository → entity. There
is no strategy/pipeline machinery here (that's TimeTracking's pattern; don't port it over):

- `UserAccountController` — implements the generated `AccountApi`, plus the hand-written login endpoint.
  No business logic.
- `UserAccountService` / `UserAccountServiceImpl` — registration, profile edit, role add/remove, lookups.
  Password hashing (BCrypt, `AccountConfiguration`) happens here; `userToUserProfileDto` is the single
  entity→DTO conversion point, and it is what keeps `password` out of every response. Keep new responses
  going through it.
- `UserRepository` — `JpaRepository<User, Integer>` with `findByTenantId` and `findByIdUserAndTenantId`.
- `User` (`co.il.attendanceaccounting.model`) → table `users`, with roles as an eager
  `@ElementCollection Set<String>` → table `user_roles`. The id is the caller-supplied `idUser` (a
  national-ID-like number), **not** generated.

Errors are `@ResponseStatus` exceptions in `co.il.attendanceaccounting.exceptions` — `UserExistsException`
(409), `UserAuthenticationException` (401), `UserNotFoundException` (**400**, not 404). There is no
`@ControllerAdvice`; add a new `@ResponseStatus` exception instead of a handler class. Note the 400: the
TimeTracking client maps 404→`NotFoundException`, so a missing user currently reaches it as a generic
`InternalApiException`.

### Authentication & JWT minting

`POST /account/login` takes `{idUser, password, tenantId}` (`LoginRequestDto`, a record) and:

1. `AuthenticationServiceImpl` authenticates id+password through the `AuthenticationManager`
   (`DaoAuthenticationProvider` + `UserDetailsServiceImpl` + BCrypt).
2. It then checks the requested `tenantId` matches the stored one — a mismatch is reported as
   `Invalid credentials` (401), same as a wrong password, so login can't be used to probe tenants.
3. `JwtService.mint` builds the token: `sub` = user id, `authorities` claim = the user's roles,
   `tenantId` claim, HS256, TTL from `attendance-accounting.security.jwt.ttl-seconds` (default 3600).
   The response is `{token, tokenType: "Bearer", expiresIn, profile}`.

**Role-string casing is load-bearing.** Roles are stored as free-form strings (`register` assigns
`"User"`; the seed/bootstrap use `"Administrator"`). `UserDetailsServiceImpl` uppercases them into
authorities, so the JWT carries `["USER"]` / `["ADMINISTRATOR"]`, and every service (here and downstream)
adds the `ROLE_` prefix in its `JwtAuthenticationConverter` before `hasRole(...)` checks. Don't "fix" the
casing on one side only.

`AttendanceAccountingApplication` is a `CommandLineRunner` that bootstraps a super-admin
(`idUser 123456789`, password `admin`, all roles, `tenantId = SecurityConstants.SYSTEM_TENANT_ID` = 0) if
it doesn't already exist — convenient locally, but it is a hardcoded default credential; don't propagate
it into anything production-facing.

### Authorization

All rules live in `SecurityConfiguration.filterChain` (URL-based), not in annotations on the controller:

- `POST /account/login` and `POST /account/user` (self-registration) — public.
- `DELETE /account/user/{idUser}`, `POST|DELETE /account/user/{idUser}/role/{role}` — `ROLE_ADMINISTRATOR`.
- `PUT /account/user/password/{idUser}` — the user themselves (`@customWebSecurity.checkAuthorityChangePassword`)
  or an administrator.
- Everything else — merely authenticated.

Sessions are stateless, CSRF and CORS are disabled, and the H2 console is excluded from the filter chain.
`@EnableMethodSecurity` is on, so `@PreAuthorize` would work if a rule is easier to express there — but
follow the URL-matcher style already used unless there's a reason not to.

### Persistence

- Local/dev runs on a file-based H2 database (`spring.jpa.hibernate.ddl-auto=update`); tests run on
  in-memory H2 with `ddl-auto=none` and schema/seed applied by Flyway migrations in
  `src/test/resources/db.migration` (`V1__schema.sql` creates `users` + `user_roles`,
  `V2__users_setup.sql` seeds three users across tenants 100 and 200 with BCrypt passwords).
- The `postgresql` dependency is present for production use but no active Postgres datasource is
  configured in this repo. Migrations exist **only under test resources** — there is no production
  migration set, so a schema change means updating the test migrations and relying on `ddl-auto=update`
  elsewhere.

### Known gaps (don't assume these exist)

- **No tenant isolation on reads.** Unlike TimeTracking (which has a `TenantInterceptor`), any
  authenticated user here can call `GET /account/users` with no `tenantId` and receive every tenant's
  users, or read another tenant's user by id. The existing tests document this behavior rather than
  forbid it — treat it as an open gap, not an invariant.
- `UserAccountService.changePassword` is declared and implemented but is not reachable from any endpoint
  (password changes go through `editUser`).
- No Kafka/Spring Cloud Stream, no outbound clients to other services, no scheduled jobs here.

## Testing conventions

**New tests must follow this existing pattern — full end-to-end flow through a real running server, not
component/unit-style tests.** Do not write `@WebMvcTest`/`MockMvc`/mocked-service tests for controller
behavior; the convention is `@SpringBootTest(webEnvironment = RANDOM_PORT)` hitting the real HTTP endpoint
over `RestTemplate`, through the real security filter chain and a real DB.

Tests extend `BaseApiControllerTest` (`src/test/java/co/il/attendanceaccounting/controller`). Its `send(...)`
helper **logs in for real** to obtain a JWT (`POST /account/login` with the seeded credentials, tenant
resolved by `tenantIdFor`) and sends it as a bearer token, so authorization is exercised end to end — pass
`null` for the user/password to send an anonymous request. Reuse the seeded constants (`ADMIN_ID`,
`USER_ID`, `OTHER_USER_ID` and their passwords/tenants) and the `createUserRegisterDto` /
`createUserEditDto` builders rather than re-deriving fixtures; the seed comes from `V2__users_setup.sql`,
and every test that mutates data is annotated `@FlywayTest` to reset it.

Each test carries a `@DisplayName` naming the HTTP contract it pins (`"DELETE /account/user/{id} as
non-admin returns 403 and keeps the user"`), asserts both the response **and** the resulting DB state, and
uses `"Reason: ..."` assertion messages. Keep all three. New endpoints get positive, negative and
authorization cases in the same style.

## General coding principles

- Keep the service thin and single-purpose: accounts, roles, tokens. Business logic belonging to other
  domains goes to the service that owns it.
- Avoid duplicating logic — reuse `userToUserProfileDto`, the existing exceptions and the test base class
  instead of copy-pasting.
- Apply SOLID principles and standard Java/OOP practice (interface + `Impl` per service, single
  responsibility per class) — follow the idioms already present rather than introducing new ones without
  reason.
