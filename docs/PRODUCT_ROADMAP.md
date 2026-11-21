# Company operations product

This document tracks the requested end state. A working demo is not evidence that a production requirement is complete.

## Current session priority

Finish the owner-first installation, flexible employee/permission, optional branch/warehouse, product/tag and stock workflows before expanding marketplace integrations. The owner must be able to start with no employees and no explicitly created branch. Basic product creation requires name, SKU and initial quantity; price is optional, and user-defined name/value tags remain reusable and editable. Existing data and history must survive these changes.

Open core audit findings: technical centre/branch account types currently restrict which permission combinations can be selected; product-view-only responses still expose global quantity; stock screens retain absolute-quantity adjustment workflows. These require capability-based authorization, field-level stock visibility and safer movement-oriented stock navigation before the core session is considered complete.

### Product and stock architecture decision

Product holds identity and descriptive data; each tag is a name/value child owned by its product. Physical balances remain separate stock records because one product can exist at the centre and multiple optional branches/warehouses, with different reservations and movement histories. The product's displayed total is a transactionally maintained summary of those physical balances, not a second independently editable stock amount. Creating a product with an initial quantity creates a centre-stock operation atomically. Later quantity changes go through authorized stock operations, not the metadata editor. Transfers preserve the total, and order reservations do not change physical totals. Metadata revision is separate from stock revision so a receipt does not invalidate an otherwise current name/tag edit.

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

### Recovery review correction

- A queued publication is now revalidated before HTTP submission. If its saved barcode or quantity no longer matches current policy and available stock, it fails without contacting the provider. A fresh preview/publication is required rather than sending an outdated snapshot after downtime.
- Verification: all eight channel-stock integration tests passed, including the new reservation-change and disabled-listing recovery case. The earlier full 107-test build remains the broad verification baseline; this correction adds one regression test and was checked with the affected integration suite.

### Core session: simple product lifecycle

- Basic product creation now accepts name, SKU and centre quantity without a price or explicit branch. Optional tags remain editable name/value children and reusable suggestions. Price is optional and appears under additional details; unpriced products cannot create orders.
- Metadata updates require the displayed product revision; stock changes do not invalidate metadata edits. A stale metadata form cannot overwrite another user's changes. Quantity changes are explicitly rejected in the metadata update request.
- Initial physical stock requires stock-management authority in addition to product-creation authority. Product-only creators can open zero-stock records. Archived products can be shown and restored without erasing tags, quantities or historical references.
- Verification: all 115 tests passed. Seven new cases cover the minimal form, no implicit branch creation, stock permission separation, trim/price validation, stale edits, archive/restore, unpriced-order protection and concurrent stock/metadata changes. Actual browser checks covered the same form and restricted-user controls at 1440/900/390 pixels. Flyway through V19 applied with schema validation on an isolated database.

### Core session: owner and account lifecycle

- Fresh development installations create the owner account only; sample worker accounts are confined to explicit demo/test profiles. Existing accounts are preserved. A fresh production installation without owner bootstrap settings now fails clearly instead of starting with no usable account.
- Every authenticated account, including branch workers, can edit its own display name and change its username or password through the header's account dialog. Username changes require the current password; password changes invalidate every previous session for that account.
- Owners can issue a one-use employee password link from the employee profile. Old links and sessions are invalidated permanently, including after the new password is activated. Disabled accounts and owner targets cannot be reset through this employee action. Same-tab activation links now switch out of the old session correctly.
- Verification: all 122 tests passed. The fresh-owner test applied V1–V19 with schema validation and created a product without creating any branch or employee. Six additional account cases covered self-service identity/password, branch access, stale forms, reset links and permanent session revocation. Actual browser tests used independent owner/worker contexts and exercised identity/password updates, invitations, same-tab reset activation and responsive dialogs.

### Core session: capability-based employee roles

- Custom roles and personal additions/exclusions can combine product, location, global-stock and own-branch actions independently of legacy account presets. Default grants are unchanged. Own-branch actions require an assigned active location; global read authority alone never grants writes at another location.
- Product responses redact total quantity for catalogue-only accounts. A mixed-role employee can use the own-branch workspace and catalogue in the same session. Role editing checks existing unassigned employees before adding branch-dependent permissions.
- V20 preserves the original owner/branch-worker constraints while allowing optional branch assignment for ordinary custom-role accounts. The actual migrated schema caught this restriction beyond the create-drop test schema; a regression now covers it. Invitation choices survive background list refreshes.
- Verification: the full 127-test suite passed before the migration correction; the five flexible-permission integration cases plus the migrated owner/optional-branch regression passed afterward. Actual browser checks covered custom-role creation, branch requirement, personal exclusion, catalogue quantity redaction, own-write/global-read separation and responsive role actions.
