# Trendyol quantity publication

This delivery connects only the stock-update API for existing approved Trendyol listings. It does not create products, import real orders, confirm shipment, or synchronize other real marketplaces. The five local channel simulators remain separate demo tools.

## Panel

Open **Satış Kanalları → Satışa açılacak stok**. An administrator or a worker granted `CHANNELS_VIEW` can inspect product mappings and publication history. `CHANNELS_MANAGE` additionally permits editing and publishing.

Map an internal product to its actual Trendyol barcode. Set its maximum quantity, safety stock and enabled state. The publication quantity is:

`enabled and product active ? min(maximum, max(0, central available - safety stock)) : 0`

Central available already excludes order reservations. Branch and warehouse stock is not included. These are caps on a shared pool, not dedicated stock reservations for each marketplace. Turning a mapping off produces a zero publication; saving an option alone does not contact the provider. Unmapped products are never published. Existing barcode changes require a separate reconciliation workflow, so they are currently blocked rather than leaving an old listing stocked accidentally.

## Connection configuration

Set these through the deployment environment, never in Git or a shared screenshot:

- `TRENDYOL_ENABLED=true` explicitly enables the adapter; the default is false.
- `TRENDYOL_ENVIRONMENT=STAGE` (default) or `PROD`.
- `TRENDYOL_SELLER_ID`, `TRENDYOL_API_KEY`, `TRENDYOL_API_SECRET` from that environment's authorized seller account.

Only Trendyol's fixed official HTTPS hosts are used. Redirects are refused. Credentials never appear in panel responses or publication records. Test access may require provider-side account/IP authorization. The panel clearly labels production before an explicit publish confirmation.

No real credentials were supplied during development. HTTP payloads, headers and outcomes were verified against controlled contract fixtures. An authorized test-seller trial and live-provider acceptance are still required before business use.

## Durable outcomes and recovery

An explicit publication persists its UUID, connection identity, actor and quantity snapshot before HTTP. At most one unresolved publication is allowed. The adapter sends quantity only, for at most 1000 changed barcodes and at most 20,000 units each. A submitted batch is **not** a confirmed success: the worker checks every expected barcode's quantity and individual status. The worker automatically resumes pending result checks after a process restart.

Before claiming a queued POST, the worker compares every saved barcode and quantity with the current local calculation. If reservations, stock or policy changes made the queued snapshot stale, the job fails without contacting the provider and requires a fresh publication. This prevents an old queue entry being blindly sent after downtime; it does not provide an atomic transaction across the database and the marketplace.

A lost POST response, interrupted sending claim, mismatched result or changed connection becomes **UNCERTAIN**. The POST is not replayed automatically. Check the seller panel first, then use **Mağazada kontrol ettim** with a reconciliation note. Confirmed unchanged quantities are omitted; identical recent payloads are held for the provider's 15-minute duplicate-request window. Partial failures are not used as confirmed stock snapshots. Confirmation is historical evidence, not a current read of the provider's stock after later sales.

Automatic stock publication is deliberately not enabled while real order import is incomplete: a remote order must be accounted for before republishing stock from the local shared pool. The scheduled worker only completes explicitly requested jobs. When real Trendyol mode is enabled, the old simulator import/sync actions skip Trendyol to prevent mixing simulated orders with the real store.

## Official references checked 21 November 2026

- [Authentication and mandatory User-Agent](https://developers.trendyol.com/docs/2-authorization)
- [Production and stage access](https://developers.trendyol.com/docs/3-canl%C4%B1-test-ortam-bilgileri)
- [Stock update endpoint, limits and repeated-body rule](https://developers.trendyol.com/docs/stok-ve-fiyat-g%C3%BCncelleme-updatepriceandinventory-1)
- [Per-item asynchronous result checking](https://developers.trendyol.com/docs/toplu-i%CC%87%C5%9Flem-kontrol%C3%BC-getbatchrequestresult-1)
