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
