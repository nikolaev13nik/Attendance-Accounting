# Attendance Accounting

The identity service of the [Attendance Platform](https://github.com/nikolaev13nik/attendance-project-common):
organizations (tenants), their users and roles, login, and JWT issuing. Every other service trusts the
tokens it signs.

## API

Defined contract-first in
[`attendance-accounting-account.yaml`](src/main/resources/swagger-source/attendance-accounting-account.yaml);
the controller implements the generated interface.

| Method | Path | Who |
| --- | --- | --- |
| `POST` | `/account/signup` | anyone (captcha) — creates a company and its first administrator |
| `POST` | `/account/login` | anyone — returns a JWT |
| `POST` | `/account/tenant/{tenantId}/user` | administrator of the tenant |
| `GET` | `/account/tenant/{tenantId}/users` | authenticated user of the tenant |
| `GET` | `/account/tenant/{tenantId}/userId/{idUser}` | authenticated user of the tenant |
| `PUT` | `/account/tenant/{tenantId}/user/password/{idUser}` | administrator, or the user themself |
| `DELETE` | `/account/tenant/{tenantId}/user/{idUser}` | administrator |
| `POST` / `DELETE` | `/account/tenant/{tenantId}/user/{idUser}/role/{role}` | administrator |
| `GET` | `/account/tenants?page&size` | platform administrator only |

## How it works

- **Tenant scoping.** `{tenantId}` in the path must match the token's `tenantId` claim; the shared
  `TenantInterceptor` from [attendance-common-lib](https://github.com/nikolaev13nik/attendance-common-lib)
  enforces it. User ids are unique per company (primary key `(tenant_id, id_user)`).
- **Tokens.** HS256 JWTs with the user id as subject, the roles as `authorities` and the `tenantId`. Roles
  are `USER`, `ADMINISTRATOR` and `PLATFORM_ADMIN`; only `USER` and `ADMINISTRATOR` can be granted through
  the API, and the last administrator of a company cannot be removed.
- **Bootstrap.** On start the service makes sure the platform administrator exists in tenant 0, with its
  password taken from `ATTENDANCE_ACCOUNTING_BOOTSTRAP_ADMIN_PASSWORD`.
- **Sign-up.** Verifies a Cloudflare Turnstile token server-side, then creates the company and its
  administrator in one transaction.
- **Pipeline.** Each endpoint is a strategy service (`service/strategy`) running
  `fetch → validate → executeBusiness → persist → mapResult` over an `AccountContext`;
  `AccountServiceBase` owns the transaction and the log line for each call.
- **Schema.** Flyway migration `V1__schema.sql`; Hibernate only validates it.

## Run and test

```bash
./mvnw test                  # unit + API tests on H2
./mvnw clean verify -Pci     # what CI runs: tests + JaCoCo, at least 85% line coverage
```

The build needs a GitHub token with `read:packages` under server id `github` in `~/.m2/settings.xml` to
download `attendance-common-lib`. To run the whole system, use the Docker Compose stack in
[attendance-project-common](https://github.com/nikolaev13nik/attendance-project-common).

| Variable | Purpose |
| --- | --- |
| `ATTENDANCE_ACCOUNTING_JWT_SECRET` | HS256 signing key, at least 32 bytes; shared with TimeTracking |
| `ATTENDANCE_ACCOUNTING_CAPTCHA_SECRET` | Cloudflare Turnstile secret key |
| `ATTENDANCE_ACCOUNTING_BOOTSTRAP_ADMIN_PASSWORD` | password of the platform administrator |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | PostgreSQL connection |

## Deployment

CI (GitHub Actions) builds and tests, pushes the image to GitHub Container Registry, tags the version and
writes it into [`helm/accounting`](helm/accounting), which is deployed to Kubernetes.
