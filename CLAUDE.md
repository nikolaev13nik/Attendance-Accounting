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
./mvnw clean verify -Pci          # what CI runs: build + tests + JaCoCo coverage check (min 85% line coverage)
./mvnw spring-boot:run            # run locally (H2 file DB at ./data/attendance-accounting)
```

Requires env var `ATTENDANCE_ACCOUNTING_JWT_SECRET` — the HMAC signing secret, **at least 32 bytes** or
the context fails to start (`SecurityConfiguration.jwtSecretKey`). Tests supply their own via
`src/test/resources/application.properties`.

The JaCoCo threshold is a real gate: `-Pci` fails the build under 85% line coverage (bundle-wide,
generated sources and the boot entrypoint excluded — see the `<excludes>` in the `ci` profile). New
production code needs tests in the same change, or CI goes red. `.github/workflows/ci.yml` then builds and
pushes a Docker image to GHCR and tags the version on `main`; it mirrors `Attendance-TimeTracking`'s
workflow step for step, so keep the two in sync rather than letting them drift.

There is no linter/formatter configured in this repo (the shared IntelliJ Google-style config lives in
`../attendance-project-common/sharedFiles/`).

## Architecture

### API is contract-first (OpenAPI → generated interface)

`src/main/resources/swagger-source/attendance-accounting-account.yaml` is the source of truth for the REST
API, including `POST /account/login` and its `LoginRequestDto`/`LoginResponseDto`. The
`openapi-generator-maven-plugin` (bound to `generate-sources`) generates the `AccountApi`
interface (`co.il.attendanceaccounting.api`) and DTOs (`co.il.attendanceaccounting.dto`) into
`target/generated-sources`. `UserAccountController` implements `AccountApi`. **To add/change an endpoint,
edit the yaml first**, then rebuild so the interface regenerates, then implement the method. Bean
validation annotations on the DTOs come from the spec (`performBeanValidation`), so input rules like
"email must be a valid email" are declared there, not in Java. The login body is `required: false` with no
required fields on purpose: an empty or partial body must reach authentication and get 401, not 400.

**Spec consumers exist outside this repo.** `Attendance-TimeTracking` keeps a manually synced copy of this
yaml to generate its outbound client, and that copy is already stale (it still has a placeholder
`GET /account/user/{idUser}`, while this repo has since published
`GET /account/tenant/{tenantId}/userId/{idUser}`). When changing this spec, update that copy too.

### Layering: strategy services over a shared context (same pattern as TimeTracking)

**Follow this pattern for new or changed endpoints — no business logic in the controller, no bypassing the
pipeline.**

- `UserAccountController` implements the generated `AccountApi`. Each method builds an `AccountContext<T>`
  (`co.il.attendanceaccounting.context`) with its Lombok builder, calls one strategy service's
  `execute(context)`, and returns the result read back off the context (`201 CREATED` for register and
  addTenant, `200` otherwise).
- Every service is `void`: `BaseService<T>` (`service.base`) declares only `execute` and
  `executeWithoutTransactional`. `AccountServiceBase<R>` fixes the template-method pipeline:

  ```
  execute(context)  [@Transactional]
    -> fetch -> validate -> executeBusiness -> persist -> mapResult
  ```

  Default `persist` saves `context.users` and `context.tenantMappings`; default `mapResult` maps
  `context.users` to `responseUserProfileDto`. Services override only the steps they need.
- One strategy per API in `service.strategy`: `RegisterUserService`, `AddTenantService`,
  `RemoveUserService`, `EditUserService`, `AddRoleService`, `RemoveRoleService`, `GetAllUsersService`,
  `GetUserService`, `LoginService`.
- Entity ↔ DTO conversion goes through MapStruct (`mapper.UserMapper`, `mapper.TenantMappingMapper`).
  `UserProfileDto` has no password field, so no response can leak it. Keep new responses going through
  the mappers.
- `UserRepository`, `TenantMappingRepository` — Spring Data repositories, used only from the services and
  the security layer.
- `User` (`co.il.attendanceaccounting.model`) → table `users`, with roles as an eager
  `@ElementCollection Set<String>` → table `user_roles`. The id is the caller-supplied `idUser` (a
  national-ID-like number), **not** generated.

Errors are `@ResponseStatus` exceptions in `co.il.attendanceaccounting.exceptions`, with message strings in
`ErrorConstants` — `UserExistsException` (409), `UserAuthenticationException` (401), `ProtectedUserException`
(403), `UserNotFoundException` (**400**, not 404). There is no `@ControllerAdvice`; add a new
`@ResponseStatus` exception instead of a handler class. Note the 400: the TimeTracking client maps
404→`NotFoundException`, so a missing user currently reaches it as a generic `InternalApiException`.

### Authentication & JWT minting

`POST /account/login` takes `{idUser, password, tenantId | companyName}` (`LoginRequestDto`) and runs
`LoginService`, whose `validate` step calls `AuthenticationService.authenticate(context)`:

1. `AuthenticationServiceImpl` authenticates id+password through the `AuthenticationManager`
   (`DaoAuthenticationProvider` + `UserDetailsServiceImpl` + BCrypt).
2. It then checks the requested `tenantId` matches the stored one — a mismatch is reported as
   `Invalid credentials` (401), same as a wrong password, so login can't be used to probe tenants.
3. `JwtService.mint` builds the token: `sub` = user id, `authorities` claim = the user's roles,
   `tenantId` claim, HS256, TTL from `attendance-accounting.security.jwt.ttl-seconds` (default 3600).
   It puts `{token, tokenType: "Bearer", expiresIn}` on the context; `LoginService.mapResult` adds `profile`.

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

Rules are `@PreAuthorize` annotations on the controller methods, as in TimeTracking:

- `removeUser`, `addRole`, `removeRole` — `ROLE_ADMINISTRATOR`.
- `editUser` (`PUT /account/user/password/{idUser}`) — an administrator or the user themselves
  (`#idUser.toString() == authentication.name`).

`SecurityConfiguration.filterChain` keeps only the URL-level rules: `POST /account/login`,
`POST /account/user`, `POST /account/tenant/add`, actuator health and the ERROR dispatch are public;
everything else must be authenticated. `AccessDeniedHandlerImpl` is kept on purpose, so a 403 body carries
message `"Forbidden"` (tests assert it). Method security runs after request binding and validation, so a
malformed request from an unauthorized caller gets 400 rather than 403.

Sessions are stateless, CSRF and CORS are disabled, and the H2 console is excluded from the filter chain.

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
- Avoid duplicating logic — reuse the mappers, `AccountServiceBase` helpers, the existing exceptions and the
  test base class instead of copy-pasting.
- Apply SOLID principles — one strategy service per API operation, single responsibility per class — and
  follow the idioms already present rather than introducing new ones without reason.
