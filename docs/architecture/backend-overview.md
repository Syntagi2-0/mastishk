# Syntagi Lite Backend Overview

## Architecture

Syntagi Lite is a Java 21 Spring Boot modular monolith backed by PostgreSQL. Modules
share one process and database while keeping controllers, services, repositories,
entities, and DTOs in explicit package boundaries. Flyway owns schema evolution and
Hibernate runs in `validate` mode. Relationships remain lazy by default.

## Module responsibilities

- **auth** authenticates users and creates owner/business membership atomically.
- **business** owns business profile and timezone configuration.
- **staff** owns business memberships and OWNER/STAFF authorization.
- **servicecatalog** owns services, modes, operating schedules, and public discovery.
- **customer** owns tenant-scoped customer identity keyed by business and mobile.
- **appointment** owns slot generation, booking, cancellation, and appointment state.
- **queue** owns sessions, tokens, ordering, concurrency locks, and the scheduler.
- **notification** records customer events and browser-delivery state.
- **dashboard** provides read-only, tenant-scoped operational projections.
- **common** provides security, API envelopes, exceptions, auditing, and configuration.

## Request flow

Authenticated requests pass through the JWT filter. The token supplies user and
business identifiers, but every service reloads and validates the active membership,
user, and business. Repository queries include the business boundary. Public requests
resolve the active business through the opaque public queue code and return dedicated
public DTOs.

Mutating controllers delegate to transactional services. Domain entities enforce state
transitions. Expected business failures are converted to stable API errors by the global
exception handler; unexpected failures are logged server-side without returning stack
traces.

## Queue lifecycle

1. The scheduler reads active services and schedules.
2. Queue-session provisioning creates at most one session per service/business date.
3. Confirmed appointments are converted to idempotent `A001`-style tokens.
4. Walk-ins lock the session, reuse/create the customer, and allocate `W001` tokens.
5. NEXT locks the session, completes the current token, then calls the next eligible token.
6. Eligible appointment tokens sort by scheduled time; future appointments are excluded,
   allowing walk-ins to continue.
7. Skip, cancellation, completion, and no-show transitions update linked records and
   notifications in the same transaction.

The queue session is the concurrency boundary. Pessimistic locking serializes token
counters and NEXT calls. Unique constraints protect session/date and token ordering.

## Appointment lifecycle

1. OWNER generates slots from active schedules.
2. Public availability exposes only available slots with remaining capacity.
3. Booking pessimistically locks a slot, validates capacity, reuses or creates the
   customer, creates a CONFIRMED appointment, increments `booked_count`, and records a
   notification atomically.
4. Queue-session creation generates the appointment token; booking itself does not.
5. Cancellation releases capacity and cancels a WAITING, CALLED, or SKIPPED linked token when
   allowed by the endpoint. Cancelling a current token also clears the queue-session pointer.
6. Queue completion/no-show synchronizes the appointment state exactly once.

## Security and ownership

OWNER can manage business configuration, staff, services, schedules, and slots. OWNER
and STAFF can operate queues and read business appointments, notifications, customers,
and dashboards. Public access is restricted to explicitly listed customer endpoints.

Tenant isolation is enforced in service/repository predicates rather than trusting IDs
from JWT claims or request paths. Cross-business resource access returns a clear forbidden
error. Public DTOs omit customer identity and internal database identifiers.

## Database ownership and retention

Each tenant-owned record ultimately references `businesses.id`. Business membership,
customers, services, appointments, queue records, and notifications cannot be reassigned
through APIs. Business, staff, service, and schedule deletion is soft: status or active
flags preserve operational history. Foreign keys use restrictive deletion for business
data. Applied Flyway migrations are immutable; additions use a new version.

Optimistic `version` columns protect normal updates. Pessimistic locks are reserved for
slot capacity, queue counters, queue advancement, appointment cancellation, and delivery
state transitions.

## Performance and operations

Repository queries use entity graphs or fetch joins for required to-one projections.
Paged response mapping bulk-loads linked appointment tokens. Dashboard queries load one
business day and aggregate in memory, which is appropriate for MVP queue sizes. Composite
indexes cover tenant/date/status ordering; trigram indexes support business search APIs.

Production configuration uses bounded connection pools, graceful shutdown, response
compression, proxy header support, health probes, bounded pagination, schema validation,
and safe error responses. No asynchronous broker or external notification provider is
part of the MVP.

## Future microservice split

Natural extraction boundaries are Identity/Business, Catalog, Appointment, Queue, and
Notification. Before extraction, replace direct module coordinators with transactional
outbox events, assign schema ownership, add idempotent consumers, and define externally
versioned contracts. Queue and Appointment should remain strongly consistent until an
explicit saga and reconciliation design exists. Dashboard can later move to read models
fed from those events.
