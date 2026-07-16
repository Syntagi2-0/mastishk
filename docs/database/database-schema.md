# Syntagi Lite database schema

## Scope and conventions

This schema supports the Syntagi Lite modular monolith and its one-location-per-business MVP.
All identifiers are PostgreSQL UUIDs generated with `gen_random_uuid()`. Timestamps use
`timestamptz`, business dates use `date`, and local operating values use `time`. Enum-like values
are `varchar` columns protected by `CHECK` constraints rather than PostgreSQL enum types.

All tables contain `created_at`, `updated_at`, and an optimistic-lock `version`. The database
defaults both audit timestamps to `now()` on insert. JPA auditing updates `updated_at`; there are no
database update triggers.

## Tables

### `users`

Authenticated operator accounts. Key columns are `email`, `password_hash`, `status`, and
`last_login_at`. Email uniqueness is case-insensitive through a unique index on `lower(email)`.
Status is `ACTIVE`, `INACTIVE`, or `LOCKED`.

### `businesses`

The business/location tenant boundary. Key public identifiers are the case-insensitively unique
`slug` and unique `public_queue_code`. `business_type` is deliberately generic and unconstrained.
Status is `ACTIVE`, `INACTIVE`, or `SUSPENDED`.

### `business_users`

Associates operators with businesses. It references `businesses` and `users`, and a
`(business_id, user_id)` pair is unique. Role is `OWNER` or `STAFF`; status is `ACTIVE` or
`INACTIVE`. No global role is stored on `users`; authorization is evaluated in business context.

### `customers`

Account-free customer records scoped to a business. It references `businesses`. Mobile number is
unique within a business through `(business_id, mobile)` but can be reused at another business.

### `business_services`

Services offered by a business. It references `businesses`. `service_mode` is `WALK_IN`,
`APPOINTMENT`, or `BOTH`. Service code and case-insensitive service name are unique within a
business. Optional duration values must be positive.

### `service_schedules`

Reusable local operating windows for a service. It references `business_services`. ISO weekday is
restricted to 1–7, the end time must follow the start time, and queue lead time is 0–1440 minutes.
`(business_service_id, day_of_week, operating_start_time)` is unique, allowing multiple future
windows on one day. It is a configuration child and may cascade with its service only when no
operational history references it.

### `appointment_slots`

Capacity buckets for a service and local date/time. It references `businesses` and
`business_services`. Capacity is positive, booked count stays between zero and capacity, and the
end time follows the start time. A service cannot have two slots with the same date and start time.
Status is `AVAILABLE`, `BLOCKED`, or `CLOSED`.

### `appointments`

Customer bookings. It references `businesses`, `business_services`, `customers`, and optionally
`appointment_slots`. `booking_reference` is globally unique. Status is `CONFIRMED`, `CANCELLED`,
`COMPLETED`, or `NO_SHOW`. An appointment does not store a token number; its optional token is
owned by `queue_tokens.appointment_id`.

### `queue_sessions`

The service queue for one business date. It references `businesses`, `business_services`, and
optionally `service_schedules`. `(business_service_id, business_date)` is unique, making automatic
creation idempotent. Separate non-negative counters allocate appointment, walk-in, and
staff-created display sequences. Status is `OPEN` or `CLOSED`; a closed session requires
`closed_at`, while an open one cannot have it.

`current_token_id` is added after `queue_tokens` exists to safely resolve the circular relationship.
The foreign key guarantees the token exists, but the application must additionally guarantee that
the token belongs to the same session.

### `queue_tokens`

Immutable queue entries. It references `queue_sessions`, `businesses`, `business_services`,
`customers`, and optionally `appointments`. Source is `WALK_IN`, `APPOINTMENT`, or
`STAFF_CREATED`; status is `WAITING`, `CALLED`, `COMPLETED`, `SKIPPED`, `CANCELLED`, or
`NO_SHOW`.

An appointment can have at most one token through a partial unique index on non-null
`appointment_id`. Both `(queue_session_id, token_display)` and
`(queue_session_id, queue_order)` are unique. Token numbers are positive and priority is
non-negative.

### `notifications`

Outbound browser or WhatsApp-link messages. It references a business and can reference a customer,
appointment, or queue token. Channel is `BROWSER` or `WHATSAPP_LINK`; status is `PENDING`, `SENT`,
`FAILED`, or `SKIPPED`. `notification_type` remains an extensible `varchar` because the product
brief does not define a closed database value set.

## Queue session and appointment-token lifecycle

When a service is first needed for a business date, the application creates its `queue_sessions`
row using the matching active schedule. The unique service/date constraint arbitrates concurrent
creation attempts. In the same workflow, confirmed appointments for the service and date are read
in scheduled order and receive fixed `APPOINTMENT` queue-token rows. The appointment counter is
used to format labels such as `A001` in Java. The partial unique appointment index makes retries
idempotent. Booking an appointment alone never creates a token.

Walk-ins and staff-created arrivals receive tokens when they join. Their source-specific counters
produce `W001` and `S001` labels. Once stored, `token_display` is never updated.

## `NEXT` database behaviour

`NEXT` is one transaction:

1. Lock the open queue-session row (`SELECT ... FOR UPDATE`) to serialize receptionist actions.
2. If `current_token_id` identifies a called token, lock it and change it to `COMPLETED`, setting
   `completed_at`.
3. Select the next eligible `WAITING` token by business eligibility rules, then by
   `priority DESC, queue_order ASC`, and lock it.
4. Change that token to `CALLED`, set `called_at`, and assign its ID to
   `queue_sessions.current_token_id`.
5. Commit all changes together. The first `NEXT` skips step 2 because there is no current token.

Eligibility can account for appointment `scheduled_time`; it must not be inferred from the display
number.

## Why `token_display` and `queue_order` are separate

`token_display` is a permanent customer-facing identifier. Appointment, walk-in, and staff-created
sequences can therefore coexist without renumbering. `queue_order` is the stable internal tie-breaker
used with priority and schedule eligibility. Keeping them separate permits operational reordering
without changing the label already shown to a customer.

## Concurrency and integrity requirements

- Allocate all counters while holding a row lock on the queue session; update the counter and insert
  its token in one transaction.
- Treat unique-constraint conflicts during session creation or appointment token generation as
  idempotent races and reload the winning row.
- Lock an appointment-slot row when incrementing `booked_count`; the check constraint is the final
  guard against overbooking.
- Use optimistic `version` checks for ordinary edits and pessimistic locking for `NEXT`, counter
  allocation, and capacity allocation.
- The service layer must verify that every referenced business, service, customer, slot, appointment,
  session, and token shares the same `business_id`. Simple foreign keys only prove existence.
- The service layer must enforce that `current_token_id` belongs to its queue session.
- Operational history uses restrictive deletes. Normal lifecycle changes deactivate or transition
  records instead of physically deleting them.

## Future ownership boundaries

| Future owner | Tables |
|---|---|
| Auth/identity | `users` |
| Business/access | `businesses`, `business_users` |
| Customer | `customers` |
| Service catalogue | `business_services`, `service_schedules` |
| Appointment | `appointment_slots`, `appointments` |
| Queue | `queue_sessions`, `queue_tokens` |
| Notification | `notifications` |
| Dashboard | No source tables; consumes module-owned read models/events |

These are modular ownership boundaries inside one database today. Foreign-key dependencies are
explicit so they can later be replaced by identifiers, APIs, and events during a deliberate
microservice extraction.
