# Company operations product

This document tracks the requested end state. A working demo is not evidence that a production requirement is complete.

## Required behavior

- Owner signs in with an administrator account; production has no public or seeded administrator password.
- Owner defines named roles and permissions, invites employees, changes employee identity/role/location/status, and applies personal permission additions/exclusions. Changes are enforced by the server.
- Authorized employees can create, edit and remove products and their reusable name/value tags; product quantities remain consistent with physical stock at each location.
- Owner and delegated employees can create, rename, archive and restore branches/warehouses without losing historical records.
- Inventory supports receipts, issues and internal transfers; single and multi-product operations; custom operation names, including display-stock workflows. Source and destination locations are explicit. Transfers do not change the product total.
- Marketplace adapters follow official provider APIs, import orders automatically with duplicate protection, and publish the sellable quantity configured by authorized users. Live operation requires the seller's access credentials and provider approval where applicable.
- Each of the five navigation sections is a landing page; forms and lists open through its task links. Restricted users see only their permitted tasks.
- Company deployment includes migration, backup/restore instructions, reliable order lifecycle and synchronization recovery, and meaningful access/concurrency/inventory tests.

## Starting evidence (21 November 2026)

- Existing employee permission editing, product tags, quantity accounting, manual stock movements, and five-section navigation are committed as a recoverable baseline.
- Baseline: 79 JUnit/MockMvc tests pass. Navigation also has browser checks with administrator and restricted-user fixtures.
- Real marketplace adapters, transfer/batch movements, account profile editing, location editing, delegated location management and automatic synchronization remain incomplete at this starting point.

## Usage rule

Read the account's five-hour usage window before work and after each delivery stage. Below 20% remaining, finish verification, essential fixes and GitHub saving before starting another feature. Do not consume reset credits automatically. Account quota is not a wall-clock timer.

## Delivery log

Updates below must describe verified behavior and distinguish live-provider testing from fixtures/simulators. Do not mark the complete product delivered until every required behavior has corresponding evidence.

### Account and location lifecycle

- Employees now have editable full name, username, role, branch/warehouse assignment and enabled state. Personal permission exceptions are preserved within the new scope. Stale edits return a conflict.
- JWTs include immutable account identity and a session version. Renaming, moving or disabling an account invalidates previous sessions; reusing the old username or re-enabling the account does not revive old tokens.
- Locations support branch/warehouse kind, rename, archive and restore. Archive requires empty stock and no assigned accounts. An archived location cannot receive stock or new employee assignments.
- Location viewing and management can be delegated independently. New privileges are not granted to existing workers automatically. Unused roles can be removed; assigned roles require reassignment first.
- Verification: all 84 JUnit/MockMvc tests passed. Actual HTTP/browser checks against an isolated H2 database covered account editing, status changes, warehouse create/rename/archive/restore and a delegated location manager. Flyway migrations through V15 applied with schema validation.
- Outstanding: transfer/batch movement workflows, stock classifications, live marketplace adapters and stock-allocation settings, automatic synchronization/recovery, full order lifecycle, production deployment and recovery verification.

### Batch inventory operations and internal transfers

- Receipts, issues and internal transfers accept one or multiple products. A failed line rolls back the entire batch. Transfers preserve total physical quantity and respect stock reserved by orders.
- Client request IDs are durable: retrying an identical request returns its receipt, including after a lost response. Reusing the ID for a different request returns a conflict. History groups transfer legs and preserves product, location, actor and movement-name snapshots.
- Authorized managers create, rename, archive and restore movement names tied to the three operation kinds. Transfer and movement-name management are separate permissions. Branch employees remain restricted to their own location.
- Verification: all 91 JUnit/MockMvc tests passed. Browser checks covered multi-product transfers, rollback, corrected retry, a committed request whose response was lost, history filtering and layouts at 1440/900/390 pixels. Flyway migrations through V16 applied with schema validation on an isolated database.
- Outstanding: real display/non-saleable stock classifications, live marketplace adapters and stock-allocation settings, automatic synchronization/recovery, full order lifecycle, production deployment and recovery verification. Custom movement names alone do not implement stock classifications.

### Configured sellable stock and first provider adapter

- Added a permission-controlled channel stock page. Existing products map to Trendyol barcodes with an enabled flag, maximum quantity and safety stock. Only central available stock is considered; branch/warehouse inventory is not silently counted.
- Added a quantity-only Trendyol adapter using current official HTTPS endpoints, Basic authentication and mandatory User-Agent. It is disabled by default. Credentials come from deployment settings and never appear in API responses or publication history.
- Explicit publications persist their request ID, actor, connection identity, item snapshot and asynchronous batch outcome. Pending result checks recover after restart. Lost submission responses remain uncertain until reconciled; automatic duplicate POSTs are avoided. Every item must be confirmed before success is shown.
- Verification: all 102 JUnit/MockMvc tests passed, including provider HTTP contracts, reservation/cap/safety calculations, personal/delegated access, request replay and interrupted-job recovery. Browser checks covered settings persistence, preview, disabled real connection, delegated read-only UI and 1440/900/390-pixel layouts. Publication-history rendering used fixtures; no real marketplace account was contacted. Flyway V17 applied with schema validation.
- Setup and limitations: [Trendyol stock adapter](TRENDYOL_STOCK.md). Real seller acceptance, real order import and lifecycle, automatic stock publication, additional provider adapters, dedicated channel allocation and display-stock classifications remain incomplete. Do not treat this as a production-ready marketplace integration.

### Local order settlement and deployment rehearsal

- Orders can be locally marked shipped or cancelled by administrators and explicitly delegated operations managers. Shipment consumes reserved physical stock once; cancellation releases reservations without reducing physical totals. Stale/conflicting transitions are rejected and successful changes have actor/time history.
- Own-order access remains separate from all-channel operations access. Read-only operations workers see history without shipment/cancellation controls. Local actions do not change the marketplace's order, cargo, refund or payment state.
- Verification: the full 107-test suite and packaged build passed. The 24 new management, batch inventory, channel stock and order lifecycle integration cases also passed against an isolated PostgreSQL 18 database with Flyway V1–V18 and schema validation. Browser checks covered shipment, cancellation, replay, reservations, history and delegated read-only navigation.
- A PostgreSQL custom-format backup was restored into a separate database; important table counts and 18 successful migration records matched. The packaged application also started with the production profile on a fresh isolated PostgreSQL database: explicit owner bootstrap worked, demo accounts were absent, product/stock writes worked and the real provider remained disabled. This is a technical rehearsal, not company production acceptance.
- [Deployment/recovery notes](COMPANY_RUNBOOK.md) record startup, owner bootstrap, backup/restore and local order limits. Remaining work includes live seller acceptance and real order import/status reconciliation; automatic synchronization; other providers; returns, partial shipment and payment lifecycle; display/non-saleable stock; owner recovery; infrastructure, monitoring and company acceptance.
