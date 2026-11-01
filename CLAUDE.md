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
  `GetUserService`, `LoginService`.
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
(403), `UserNotFoundException` (404). There is no `@ControllerAdvice`; add a new `@ResponseStatus`
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
every service adds the `ROLE_` prefix in its `JwtAuthenticationConverter` before `hasRole(...)` checks.

The bootstrap account is protected: deleting it or changing its roles is 403 `ProtectedUserException`.
A tenant always keeps one administrator: removing `ADMINISTRATOR` from, or deleting, its last admin is
409 `LastAdministratorException` (`AccountServiceBase.ensureNotLastAdministrator`).

`AttendanceAccountingApplication` is a `CommandLineRunner` that bootstraps the platform admin
(`idUser 123456789`, password from `ATTENDANCE_ACCOUNTING_BOOTSTRAP_ADMIN_PASSWORD`, role `PLATFORM_ADMIN`
only, `tenantId = SecurityConstants.PLATFORM_TENANT_ID` = 0). If `(0, 123456789)` already exists, every
startup resets its roles to exactly that. Protection (403 on delete or role change) applies to that
key only; a user with the same id in another tenant is an ordinary user.

### Authorization

Roles are `USER`, `ADMINISTRATOR` (admin of one tenant) and `PLATFORM_ADMIN` (the bootstrap account). A
`RoleHierarchy` bean in `SecurityConfiguration` makes `PLATFORM_ADMIN > ADMINISTRATOR > USER`, so
`hasRole('ADMINISTRATOR')` also admits the platform admin. Rules are `@PreAuthorize` annotations on the
controller methods, as in TimeTracking:

- `removeUser`, `addRole`, `removeRole` — `ROLE_ADMINISTRATOR`.
- `editUser` (`PUT /account/tenant/{tenantId}/user/password/{idUser}`) — an administrator or the user themselves
  (`#idUser.toString() == authentication.name`).

**Tenant isolation.** Every secured endpoint lives under `/account/tenant/{tenantId}/...`.
`security.TenantInterceptor` (registered in `security.WebConfig` for all of `/account/tenant/**`, with no
exclusions, mirroring TimeTracking) compares that path tenant with the JWT `tenantId` claim and
throws `AccessDeniedException` (403 `"Forbidden"`) on a mismatch, or when a matched endpoint has no
`{tenantId}` in its path (fail closed). The one exception is the platform admin: `ROLE_PLATFORM_ADMIN`
**and** JWT tenant `PLATFORM_TENANT_ID` (0) skips the comparison
and may name any tenant, including 0 for its own account. The services then look users up with
`findByIdUserAndTenantId(idUser, pathTenant)` (`AccountServiceBase.findTenantUser*`), so a user of another
tenant answers exactly like a missing one: 404. New secured endpoints must take `{tenantId}` in the path
and go through those helpers, never `findById`.

**Creating organizations and users.** A new organization is created only by `POST /account/signup`
(public, `SignUpService`), which saves the tenant and its first administrator in one transaction; the
administrator's name and email double as the tenant's contact name and email. There is deliberately no
way to create a tenant without an administrator, because such a tenant could be claimed by whoever
registers into it first. `POST /account/tenant/{tenantId}/user` (`RegisterUserService`) requires a token:
the caller must be that tenant's ADMINISTRATOR or the platform admin (`security.JwtClaimsResolver`), checked
before the unknown-tenant 400 and the taken-id 409. The first user of a tenant with no users becomes its
ADMINISTRATOR, which only the platform admin can now trigger.

`SecurityConfiguration.filterChain` keeps only the URL-level rules: `POST /account/login`,
`POST /account/signup`, actuator health and the ERROR dispatch are public;
everything else must be authenticated. `AccessDeniedHandlerImpl` is kept on purpose, so a 403 body carries
message `"Forbidden"` (tests assert it). Method security runs after request binding and validation, so a
malformed request from an unauthorized caller gets 400 rather than 403.

Sessions are stateless, CSRF and CORS are disabled, and the H2 console is excluded from the filter chain.

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

- `POST /account/signup` is public and unthrottled, so anyone can create organizations in bulk; rate limiting
  is not in place yet.
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
