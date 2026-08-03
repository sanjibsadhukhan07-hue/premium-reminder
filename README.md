# Premium Reminder

Java 17 / Spring Boot 3 app that stores customer policy details and automatically sends
email + SMS reminders starting 1 month before the premium due date, repeating daily until
the premium is marked paid. Includes an Admin portal (manage customers, view logs, trigger
reminders manually) and a Customer portal (view own policy status).

## Stack
- Java 17, Spring Boot 3.3 (Web, Security, Data JPA, Mail, Validation, Thymeleaf)
- PostgreSQL (H2 driver also bundled for quick local testing without Docker)
- MSG91 Flow API for SMS
- Spring Mail (SMTP) for email — works with Gmail app passwords, SendGrid SMTP relay, etc.
- Docker / docker-compose for local & production packaging

## ⚠️ Before you build
This project has **not been compiled** — it was written and manually reviewed in an
environment without internet access to Maven Central, so a `mvn clean package` has not
actually been run against it. Please run that first and fix any compile errors it
surfaces (should be minor, if any) before deploying.

## 1. Run locally (fastest way — Docker Compose)
```bash
docker compose up --build
```
This starts Postgres + the app on **http://localhost:8080**. Default admin login:
- username: `admin`
- password: `changeme123` (change this via `ADMIN_PASSWORD` env var, or change the seeded user's password in DB after first login)

Email/SMS credentials are blank in `docker-compose.yml` by default, so notifications will
log an error in the reminder log until you fill in `MAIL_USERNAME`, `MAIL_PASSWORD`, and the
`MSG91_*` variables.

## 2. Run locally without Docker (uses H2 in-memory DB)
```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.datasource.url=jdbc:h2:mem:testdb --spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
```
(Or just point `DB_URL` at a local Postgres you already have running.)

## 3. Required external accounts
| Purpose | Provider | What you need |
|---|---|---|
| Email | Gmail (or any SMTP) | An [app password](https://myaccount.google.com/apppasswords) if using Gmail |
| SMS | MSG91 | Account + auth key + an approved **DLT-registered sender ID** + a message **Flow/template** (mandatory in India for transactional SMS) — set up at https://control.msg91.com |

Set these as environment variables: `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`,
`MSG91_AUTH_KEY`, `MSG91_FLOW_ID`, `MSG91_SENDER_ID`.

Your MSG91 flow should define variables `##name##`, `##duedate##`, `##amount##`, `##policy##`
(or update `SmsService.java` / `NotificationService.java` to match whatever variables your
flow actually uses).

## 4. How the reminder logic works
- Each `Customer` has `nextDueDate`, `reminderWindowDays` (default 30 = "1 month"), and `paid`.
- Every day at 09:00 server time (`app.scheduler.cron`, configurable), the scheduler finds all
  active, unpaid customers whose due date is within `reminderWindowDays`, and sends them an
  email + SMS (once per day, tracked via `lastReminderSentDate`).
- When the admin clicks **Mark Paid**, `nextDueDate` rolls forward by `renewalCycleDays`
  (default 365) and the reminder cycle resets for next year.
- An admin can hit **Run reminders now** on the dashboard to trigger the batch on demand
  (handy for testing without waiting for the cron).

## 5. Deploying to Render (free tier)
1. Push this project to a GitHub repo.
2. In Render, choose **New > Blueprint**, point it at the repo — it will read `render.yaml`
   and provision a free Postgres database plus a Docker web service automatically.
3. **Important:** Render's Postgres `connectionString` is a `postgres://...` URI, but Spring's
   JDBC driver needs `jdbc:postgresql://...`. After the blueprint deploys, open the web
   service's environment variables and either:
   - manually rewrite `DB_URL` to prefix `jdbc:` (`jdbc:postgresql://user:pass@host:port/db`), or
   - use Render's individual DB host/port/name/user/password fields to build the JDBC URL yourself.
4. Fill in the `sync: false` environment variables in the Render dashboard (mail + MSG91
   credentials, admin username/password) — these aren't committed to git for security.
5. Render's free web services sleep after inactivity, which will also pause the cron
   scheduler. For a reminder app that must run daily without fail, consider Render's paid
   "Starter" tier or an external cron-ping service to keep it awake, especially once you're
   relying on it for real customers at ~2k traffic.

## 6. Deploying to your own VPS
```bash
docker compose -f docker-compose.yml up -d --build
```
Put this behind Nginx/Caddy for HTTPS and a real domain.

## 7. Known limitations / next steps
- CSRF protection is disabled for simplicity (MVP tradeoff) — re-enable
  (`http.csrf(...)` in `SecurityConfig`) and add CSRF tokens to the Thymeleaf forms before
  handling real customer data in production.
- `ddl-auto: update` is used for schema management — fine for getting started, but switch to
  Flyway/Liquibase migrations before this holds real customer data.
- No password-reset flow for customer portal logins yet — admin currently hands out a
  one-time temporary password when creating a customer's login.
- No rate limiting / pagination on the admin customer list — add if the customer base grows
  well past a few hundred.
