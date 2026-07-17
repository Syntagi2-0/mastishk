# Syntagi Lite Backend API

## Conventions

All endpoints use JSON and return the standard `ApiResponse` envelope. Errors use the
standard error response with a stable code, customer-safe message, request path, and
timestamp. Authenticated endpoints require `Authorization: Bearer <JWT>`.

Pagination uses `page` (zero based) and `size`. The default size is 20 and the server
caps a page at 100 records. Page metadata is returned under `data.page`, with records
under `data.content`. Dates use ISO-8601. Business-day operations use the
configured business timezone.

## Authentication and business

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/register-owner` | Public | Create owner, business, and membership atomically |
| POST | `/api/auth/login` | Public | Authenticate and issue JWT |
| GET | `/api/auth/me` | OWNER, STAFF | Current authenticated identity |
| GET | `/api/business/me` | OWNER, STAFF | Business profile |
| PUT | `/api/business/me` | OWNER | Update business profile |
| POST | `/api/staff` | OWNER | Create staff membership |
| GET | `/api/staff` | OWNER | List staff |
| GET | `/api/staff/me` | OWNER, STAFF | Current membership |
| PUT | `/api/staff/{businessUserId}/status` | OWNER | Activate/deactivate staff |

Business, membership, service, and schedule removal is represented by status/active
flags. No API physically deletes those records.

## Services and schedules

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/services` | OWNER | Create a service |
| GET | `/api/services` | OWNER, STAFF | Existing ordered service list |
| GET | `/api/services/search` | OWNER, STAFF | Search/filter/paginate services |
| GET | `/api/services/{serviceId}` | OWNER, STAFF | Service details |
| PUT | `/api/services/{serviceId}` | OWNER | Update a service |
| PATCH | `/api/services/{serviceId}/status` | OWNER | Activate/deactivate a service |
| POST | `/api/services/{serviceId}/schedules` | OWNER | Create schedule |
| GET | `/api/services/{serviceId}/schedules` | OWNER, STAFF | List schedules |
| PUT | `/api/services/{serviceId}/schedules/{scheduleId}` | OWNER | Update schedule |
| PATCH | `/api/services/{serviceId}/schedules/{scheduleId}/status` | OWNER | Activate/deactivate schedule |

## Queue

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/public/businesses/{code}/walk-in` | Public | Join an open walk-in queue |
| GET | `/api/public/queue/{tokenDisplay}` | Public | Safe token tracking projection |
| GET | `/api/queue/current` | OWNER, STAFF | Current token for a service |
| GET | `/api/queue/waiting` | OWNER, STAFF | Ordered waiting tokens |
| POST | `/api/queue/next` | OWNER, STAFF | Complete current and call next eligible token |
| POST | `/api/queue/current/skip` | OWNER, STAFF | Skip current and advance |
| POST | `/api/queue/current/recall` | OWNER, STAFF | Return current token for re-announcement |

Queue sessions are created by the in-process scheduler from active schedules. Session
creation is idempotent. Walk-in creation and queue advancement lock the queue session.
Appointment tokens are not eligible before their scheduled time. Notifications are
stored in the same transaction as lifecycle changes.

## Appointments

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/appointment-slots/generate` | OWNER | Generate slots from schedules |
| GET | `/api/appointment-slots` | OWNER, STAFF | Filter slots |
| PATCH | `/api/appointment-slots/{slotId}/status` | OWNER | Update slot availability |
| GET | `/api/public/businesses/{code}/services/{serviceId}/slots` | Public | Available capacity only |
| POST | `/api/public/businesses/{code}/appointments` | Public | Book appointment |
| GET | `/api/public/appointments/{reference}` | Public + mobile | Lookup appointment |
| POST | `/api/public/appointments/{reference}/cancel` | Public + mobile | Cancel appointment |
| GET | `/api/appointments/today` | OWNER, STAFF | Today's appointments |
| GET | `/api/appointments` | OWNER, STAFF | Search/filter/paginate appointments |
| GET | `/api/appointments/{appointmentId}` | OWNER, STAFF | Tenant-scoped details |
| POST | `/api/appointments/{appointmentId}/cancel` | OWNER, STAFF | Business cancellation |

Booking locks the slot pessimistically, reuses a customer by business and mobile,
creates the confirmed appointment, and increments capacity in one transaction. Queue
tokens are generated later when the queue session is created.

## Notifications and customers

| Method | Path | Access | Purpose |
|---|---|---|---|
| GET | `/api/public/notifications` | Public + customer context | Customer-safe notifications |
| GET | `/api/notifications` | OWNER, STAFF | Search/filter/paginate notifications |
| GET | `/api/notifications/pending-count` | OWNER, STAFF | Pending count |
| POST | `/api/notifications/{notificationId}/mark-sent` | OWNER, STAFF | Browser delivery acknowledgement |
| GET | `/api/customers` | OWNER, STAFF | Tenant-scoped customer search |

Notification deduplication is enforced by a deterministic event key and a partial
unique database index. WhatsApp support produces links only; no provider is called.

## Dashboard and public QR discovery

| Method | Path | Access | Purpose |
|---|---|---|---|
| GET | `/api/dashboard` | OWNER, STAFF | Business, queue, appointment, service and staff summary |
| GET | `/api/dashboard/today-queue` | OWNER, STAFF | Current/waiting/skipped/completed groups |
| GET | `/api/dashboard/today-appointments` | OWNER, STAFF | Appointments grouped by service and time |
| GET | `/api/public/businesses/{publicQueueCode}` | Public | QR landing information |
| GET | `/api/public/businesses/{publicQueueCode}/services` | Public | Available services |

Public projections never include customer names, phone numbers, database IDs, internal
business configuration, or failure details unless the contract specifically requires a
public service identifier for booking.

## OpenAPI

Swagger UI is available at `/swagger-ui.html`. Documents are available at:

- `/v3/api-docs`
- `/v3/api-docs/authentication`
- `/v3/api-docs/business`
- `/v3/api-docs/public`

The bearer JWT scheme is named `bearerAuth`.
OpenAPI endpoints are enabled in local and dev environments and disabled by the production
profile.
