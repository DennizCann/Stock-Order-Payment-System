# Delivery 02: Central operations and five external sales simulators

## Business outcome
A merchant can enter products/stock, distribute stock to five independent local
sales applications, generate channel sales, import them to one operations panel,
and synchronize the resulting available inventory.

## Implemented
- User-owned order API and administrator aggregate view.
- Transactional reservations with pessimistic row locking, ordered lock acquisition,
  and versioned snapshots to avoid overwriting newer channel stock with old data.
- Source/external-order-id uniqueness, normalized SKU quantity fingerprints, replay
  detection and conflict responses. Concurrent duplicate delivery is handled after
  transaction rollback so a failed transaction is never reused.
- Separate Maven projects/processes for Trendyol, Hepsiburada, n11, storefront and
  branch simulators. Shared code is intentional; these are simplified demo APIs.
- Pending local sales reduce cached channel availability. Central acknowledgement
  atomically applies a post-import stock snapshot. Lost acknowledgements can retry.
- Manual import/synchronization with per-channel results and bounded HTTP timeouts.
- Startup, process-identity-checked shutdown and HTTP verification scripts.
- A GitHub Actions workflow and pull-request template are prepared locally.

## Verification performed
- Backend: 61 tests passed against the existing H2 test configuration.
- Simulator core: 2 tests passed (stale snapshot rejection and acknowledgement stock).
- Critical API, concurrent reservation and duplicate-import cases: 10 tests passed
  against a separate PostgreSQL 18 database with Flyway migrations and schema validation.
- Real HTTP walkthrough with six JVM processes passed: 10 physical units, five
  separate one-unit channel sales, five central reservations, available stock five
  everywhere, and no additional reservation on replay.
- The running panel's HTTP/API path was exercised. Automated visual browser review
  was unavailable; visual owner review is still required.
- GitHub CI has not run; no push or pull request was created in this delivery.

## Next slice
Cargo simulation and shipment lifecycle remain to be implemented. Other boundaries
and demo assumptions are listed in [DEMO.md](../DEMO.md).
