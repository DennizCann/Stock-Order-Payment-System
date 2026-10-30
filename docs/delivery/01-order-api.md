# Delivery 01: Order API and stock consistency

## Business need
The central sales application needs one reliable order workflow before marketplace,
storefront, branch/POS, and cargo simulators can connect to it.

## Scope
- Place an order as the authenticated user.
- List and read only that user's orders.
- Reserve stock atomically; serialize concurrent updates to the same product.
- Return validation, missing-product, and insufficient-stock errors consistently.

## Acceptance criteria
1. USER and ADMIN accounts can submit an order and receive HTTP 201 plus its location.
2. The account identity comes from authentication, never from a client-supplied user id.
3. A user cannot retrieve another user's order.
4. Invalid or unavailable items do not leave a partial order or reservation.
5. Two concurrent requests for the last available unit cannot both succeed.

## Delivery workflow
The task is recorded here; in a team it could live in GitHub Issues, Jira, or another
tracker. Work happens on a feature branch. Implementation and relevant automated
checks are followed by a diff review. A pull request then presents the change for
review and CI before merging. A Git push shares code; application deployment is a
separate step.

## Product direction
The main application will provide the detailed central operations panel and backend.
Separate lightweight simulators represent Trendyol, Hepsiburada, n11, the company's
own storefront, physical branch sales, and a cargo provider. They are demo systems,
not integrations with those companies' real services. Future slices cover channel
stock synchronization, shipment creation/labels/tracking, and the panel.

## Current boundary
This delivery exposes the existing order domain. Payment, cancellation, marketplace
credentials, shipping, and the panel belong to later slices. User-created orders
are the initial route; administrative cross-channel views will be added separately.
