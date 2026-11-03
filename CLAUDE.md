# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## System context

This repo is the **Accounting** microservice (`co.il.attendanceaccounting`) in a system of ~5 services
behind one nginx ingress (see
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
the context fails to start (`SecurityConfiguration.jwtSecretKey`). It also requires
`ATTENDANCE_ACCOUNTING_CAPTCHA_SECRET`, the Cloudflare Turnstile secret key (Cloudflare's public test secret
`1x0000000000000000000000000000000AA` accepts every token and is what Compose and the local cluster use). Tests
supply both via `src/test/resources/application.properties`.

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
"email must be a valid email" are declared there, not in Java. The login body is required, and so are `idUser`
and `password`: a missing body or credential is 400. `tenantId` and `companyName` stay optional because the
caller sends one or the other; giving neither, or naming an unknown organization, is 401 from authentication.

**Spec consumers exist outside this repo.** `Attendance-TimeTracking` keeps a verbatim copy of this yaml
(`src/main/resources/swagger-source/attendance-accounting-account.yaml`) to generate its outbound client,
which calls `getUser` and `getAllUsers`. When changing this spec, copy it over there in the same change.

### Layering: strategy services over a shared context (same pattern as TimeTracking)

**Follow this pattern for new or changed endpoints — no business logic in the controller, no bypassing the
pipeline.**

- `UserAccountController` implements the generated `AccountApi`. Each method builds an `AccountContext<T>`
  (`co.il.attendanceaccounting.context`) with its Lombok builder, calls one strategy service's
  `execute(context)`, and returns the result read back off the context (`201 CREATED` for register and
  signUp, `200` otherwise).
- Every service is `void`: `BaseService<T>` (`service.base`) declares only `execute` and
  `executeWithoutTransactional`. `AccountServiceBase<R>` fixes the template-method pipeline:

  ```
  execute(context)  [@Transactional]
    -> fetch -> validate -> executeBusiness -> persist -> mapResult
  ```

  Default `persist` saves `context.users` and `context.tenantMappings`; default `mapResult` maps
  `context.users` to `responseUserProfileDto`. Services override only the steps they need.
- One strategy per API in `service.strategy`: `RegisterUserService`, `SignUpService`,
  `RemoveUserService`, `EditUserService`, `AddRoleService`, `RemoveRoleService`, `GetAllUsersService`,
  `GetUserService`, `LoginService`, `GetAllTenantsService`.
- Entity ↔ DTO conversion goes through MapStruct (`mapper.UserMapper`, `mapper.TenantMappingMapper`).
  `UserProfileDto` has no password field, so no response can leak it. Keep new responses going through
  the mappers.
- `UserRepository`, `TenantMappingRepository` — Spring Data repositories, used only from the services and
  the security layer.
- `User` (`co.il.attendanceaccounting.model`) → table `users`, with roles as an eager
  `@ElementCollection Set<String>` → table `user_roles (tenant_id, user_id, roles)`. The primary key is
  `(tenant_id, id_user)`, held as `@EmbeddedId UserBasicKey userBasicKey` (the same style as TimeTracking's
  `MonthStatistic`); `User.getIdUser()` / `getTenantId()` are convenience getters over that key, and
  writes go through `setUserBasicKey(new UserBasicKey(tenantId, idUser))`. `idUser` is a caller-supplied
  national-ID-like number, **not** generated, and unique **per tenant only**, so the same person can
  belong to several tenants. Always look a user up by the full key (`findById(new UserBasicKey(...))`);
  there is no lookup by id alone.

Errors are `@ResponseStatus` exceptions in `co.il.attendanceaccounting.exceptions`, with message strings in
`ErrorConstants` — `UserExistsException` (409), `UserAuthenticationException` (401), `ProtectedUserException`
(403), `UserNotFoundException` (404), `InvalidPageException` (400). There is no `@ControllerAdvice`; add a new `@ResponseStatus`
exception instead of a handler class.

### Authentication & JWT minting

`POST /account/login` takes `{idUser, password, tenantId | companyName}` (`LoginRequestDto`) and runs
`LoginService`, whose `validate` step calls `AuthenticationService.authenticate(context)`:

1. `AuthenticationServiceImpl` resolves the tenant from `tenantId` or `companyName` first.
2. It authenticates through the `AuthenticationManager` (`DaoAuthenticationProvider` +
   `UserDetailsServiceImpl` + BCrypt) with the principal `"<tenantId>:<idUser>"`
   (`UserDetailsServiceImpl.principalOf`), so the user is loaded by its full key. An unknown company, an
   id not in that company and a wrong password are all the same `Invalid credentials` (401), so login
   can't be used to probe tenants. The loaded `UserDetails` carries the plain id as its username, which
   becomes the JWT `sub`.
3. `JwtService.mint` builds the token: `sub` = user id, `authorities` claim = the user's roles,
   `tenantId` claim, HS256, TTL from `attendance-accounting.security.jwt.ttl-seconds` (default 3600).
   It puts `{token, tokenType: "Bearer", expiresIn}` on the context; `LoginService.mapResult` adds `profile`.

**Roles are stored uppercase.** Register writes `USER`/`ADMINISTRATOR`, the bootstrap `PLATFORM_ADMIN`, and
the role endpoints (`RoleServiceBase`) accept only `USER` and `ADMINISTRATOR` in any case and store them
uppercase; anything else, `PLATFORM_ADMIN` included, is 400 `RoleNotAllowedException`. Older rows may
still hold `"User"`/`"Administrator"`, so `User.hasRole`/`removeRole` compare ignoring case and
`UserDetailsServiceImpl` uppercases into authorities. The JWT carries `["USER"]` / `["ADMINISTRATOR"]`, and
the shared `JwtAuthenticationConverter` adds the `ROLE_` prefix before `hasRole(...)` checks.

The bootstrap account is protected: deleting it or changing its roles is 403 `ProtectedUserException`.
A tenant always keeps one administrator: removing `ADMINISTRATOR` from, or deleting, its last admin is
409 `LastAdministratorException` (`AccountServiceBase.ensureNotLastAdministrator`).

`AttendanceAccountingApplication` is a `CommandLineRunner` that bootstraps the platform admin
(`idUser 123456789`, password from `ATTENDANCE_ACCOUNTING_BOOTSTRAP_ADMIN_PASSWORD`, role `PLATFORM_ADMIN`
only, `tenantId = SecurityConstants.PLATFORM_TENANT_ID` = 0). If `(0, 123456789)` already exists, every
startup resets its roles to exactly that. Protection (403 on delete or role change) applies to that
key only; a user with the same id in another tenant is an ordinary user.

### Authorization

**The shared security and logging code lives in `../attendance-common-lib`** (`co.il.attendance.security`,
`co.il.attendance.logging`,
published to GitHub Packages and pulled in as a Maven dependency; bump `attendance-common-lib.version`
in the pom to take a new release). Its Spring Boot auto-configuration provides `SecurityConstants` (roles, claim
names, `PLATFORM_TENANT_ID`), `JwtClaimsResolver`, the `RoleHierarchy` and `JwtAuthenticationConverter` beans,
`TenantInterceptor`, the `AccessDeniedHandler` and the `AuthenticationEntryPoint` (logs every 401). Its logging part puts
`traceId` (Micrometer Tracing), `userId`, `tenantId` and `pathTenantId` into the MDC for every request and
writes one `ApiRequest` line per request, plus an `audit` line for platform admin requests. Both parts are
always on; there is no switch. Change that behaviour in the library, not here, so
TimeTracking gets the same change. Building needs a GitHub token with `read:packages` under server id `github`
in `~/.m2/settings.xml`; CI uses its `GITHUB_TOKEN`.

Roles are `USER`, `ADMINISTRATOR` (admin of one tenant) and `PLATFORM_ADMIN` (the bootstrap account). The
shared `RoleHierarchy` makes `PLATFORM_ADMIN > ADMINISTRATOR > USER`, so
`hasRole('ADMINISTRATOR')` also admits the platform admin. Rules are the shared method-security annotations
on the controller methods, as in TimeTracking:

- `removeUser`, `addRole`, `removeRole` — `@AdminOnly`.
- `editUser` (`PUT /account/tenant/{tenantId}/user/password/{idUser}`) — `@AdminOrSelf`: an administrator or
  the user themselves (`#idUser.toString() == authentication.name`, so the parameter must be named `idUser`).
  Users changing **their own** password must also send `currentPassword` (checked in `EditUserService.validate`,
  400 `CurrentPasswordMismatchException` when missing or wrong); an administrator resetting another user's
  password does not. "Own" means token subject = `idUser` and token tenant = path tenant.
- `getAllTenants` — `@PlatformAdminOnly`.

**Tenant isolation.** Every secured endpoint lives under `/account/tenant/{tenantId}/...`.
The shared `TenantInterceptor` (registered for `attendance.security.tenant.path-patterns=/account/tenant/**`,
with no exclusions; startup fails if the property is missing) compares that path tenant with the JWT `tenantId` claim and
throws `AccessDeniedException` (403 `"Forbidden"`) on a mismatch, or when a matched endpoint has no
`{tenantId}` in its path (fail closed). The one exception is the platform admin: `ROLE_PLATFORM_ADMIN`
**and** JWT tenant `PLATFORM_TENANT_ID` (0) skips the comparison
and may name any tenant, including 0 for its own account. The services then look users up with
`findByIdUserAndTenantId(idUser, pathTenant)` (`AccountServiceBase.findTenantUser*`), so a user of another
tenant answers exactly like a missing one: 404. New secured endpoints must take `{tenantId}` in the path
and go through those helpers, never `findById`.

**Listing organizations.** `GET /account/tenants?page=0&size=20` (`GetAllTenantsService`) returns one
`TenantPageDto` (`items`, `page`, `size`, `totalElements`, `totalPages`), ordered by tenant id, to the platform
admin only. `page` must be 0 or more and `size` 1 to 100; the limits are checked in `validate`
(400 `InvalidPageException`), not in the spec, because a spec `minimum`/`maximum` fails through the generated
`@Validated` interface as an unmapped `ConstraintViolationException`, which is a 500. `fetch` skips the query
for out-of-range values, since `PageRequest` would throw first. Only the platform admin may call it
(`@PreAuthorize` on `JwtClaimsResolver.isPlatformAdmin`; anyone else gets 403). It is the one secured endpoint
outside `/account/tenant/**`, because it spans all tenants, so the tenant interceptor does not apply to it.

**Sign-up is behind a CAPTCHA.** The GUI's sign-up page runs a Cloudflare Turnstile widget and sends the
one-time token it gets as `captchaToken` (required). `SignUpService.validate` first hands that token and the
secret key to Cloudflare's siteverify endpoint through `security.CaptchaVerifier` (a plain `RestClient`, 2 s
connect / 5 s read, `attendance-accounting.captcha.verify-url`): a token Cloudflare rejects is 400
`CaptchaRejectedException`, and Cloudflare being unreachable is 503 `CaptchaUnavailableException` (fail closed,
never let through), both before the database is touched. The secret is key `CAPTCHA_SECRET` in the
hand-made `attendance-secrets` Secret; the public site key lives in the GUI's `config.json`. Tests point
`verify-url` at `FakeTurnstile`, a WireMock server started once per JVM, whose tokens `VALID_TOKEN`,
`REJECTED_TOKEN` and `UNAVAILABLE_TOKEN` drive the three outcomes; `createSignUpRequestDto` sends the valid one.
On GKE, `POST /account/signup` is also rate-limited per client address by a separate nginx Ingress
(`ingress.signupLimit` in the platform chart's `values-gcp.yaml`), which answers 429 before this service sees
the request.

**Creating organizations and users.** A new organization is created only by `POST /account/signup`
(public, `SignUpService`), which saves the tenant and its first administrator in one transaction; the
administrator's name and email double as the tenant's contact name and email. There is deliberately no
way to create a tenant without an administrator, because such a tenant could be claimed by whoever
registers into it first. `POST /account/tenant/{tenantId}/user` (`RegisterUserService`) requires a token:
the caller must be that tenant's ADMINISTRATOR or the platform admin (`JwtClaimsResolver`), checked
before the unknown-tenant 400 and the taken-id 409. The first user of a tenant with no users becomes its
ADMINISTRATOR, which only the platform admin can now trigger.

`SecurityConfiguration.filterChain` keeps only the URL-level rules: `POST /account/login`,
`POST /account/signup`, actuator health and the ERROR dispatch are public;
everything else must be authenticated. It wires the shared `AccessDeniedHandler`, which answers every 403 with
JSON message `"Forbidden"` (tests assert it). Method security runs after request binding and validation, so a
malformed request from an unauthorized caller gets 400 rather than 403.

Sessions are stateless and CSRF and CORS are disabled. The H2 console is off.

### Logging

Every log line carries the MDC from the shared library (`traceId`, `userId`, `tenantId`, `pathTenantId`)
plus `targetUserId`, the user a strategy acts on. Locally it is text,
`[traceId] [user=… tenant=… target=…]` (`logging.pattern.correlation` in `application.properties`); in
Kubernetes the Helm chart sets `LOGGING_STRUCTURED_FORMAT_CONSOLE` (`logFormat` in `values.yaml`,
`logstash`) and renames `level` to `severity` so GKE reads the level, which makes every MDC key a JSON field.
`AccountServiceBase.executeWithoutTransactional` writes one line per strategy run: `completed` at INFO for every
strategy, reads included; `rejected: <reason>` at WARN for a
4xx `@ResponseStatus` exception; `failed` with the stack trace at ERROR for anything else. The target comes
from `userBasicKey`, or from the request's `idUser` where `targetUserId(...)` is overridden (register,
sign-up, login). Never log a password, token or email; `PipelineLoggingTest` checks the login case.

### Persistence

- **Flyway owns the schema everywhere.** `src/main/resources/db/migration` holds the versioned scripts
  (`V1__schema.sql`: tables, keys and indexes), applied at startup on the local H2 file database, Compose
  and Kubernetes alike, and recorded in `flyway_schema_history`. Hibernate runs with `ddl-auto=validate`: it
  never changes the schema, and the service refuses to start when an entity and its table disagree. **Every
  entity change ships with a new `V<n>__<what>.sql` in the same commit**; never edit a script that has
  already been applied, add a new one instead. The scripts must run on Postgres; tests run them on H2 in
  `MODE=PostgreSQL`, which catches most but not all differences.
- Tests load `classpath:db/migration,classpath:db/testdata`: the real production schema first, then the
  test-only seed rows in `src/test/resources/db/testdata`, one `V1xx__<table>_setup.sql` per table. They are
  numbered from 100 so they run after every production script and never collide with a future one, in
  foreign-key order. `@FlywayTest` cleans and re-migrates before a test.
- An existing database that Flyway did not create (any database from before 1e) has tables but no
  `flyway_schema_history`, so startup fails with "non-empty schema without schema history table". Drop the
  service's tables once (or delete the local `./data` H2 files) and let Flyway rebuild them.
- Local/dev runs on a file-based H2 database (`./data/attendance-accounting`); Compose and Kubernetes
  override the datasource to Postgres schema `accounting`. The test seed
  (`V101__att_ac_tenant_mapping_setup.sql`, `V102__users_setup.sql`, `V103__user_roles_setup.sql`) holds
  tenants 100/200/300 and users 1–3 with BCrypt passwords.

### Known gaps (don't assume these exist)

- Sign-up is rate-limited only on GKE (the ingress); Compose and the local cluster have the CAPTCHA alone.
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
`createUserEditDto` builders rather than re-deriving fixtures; the seed comes from `db/testdata/V1xx__<table>_setup.sql`,
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
