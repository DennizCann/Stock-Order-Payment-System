# Local multi-channel demo

Java 21 and Maven are required. The main demo uses a file-backed H2 database under
`target/demo-db`; the regular application still supports PostgreSQL and Flyway.
The simulator applications intentionally keep their own data in memory.

Build from the repository root:

```powershell
mvn verify
mvn -f simulators/pom.xml verify
./scripts/Start-Demo.ps1
```

Wait for the applications to start, then open http://127.0.0.1:8080.
Login with the local demo account `admin / admin123`.

| Process | Port |
|---|---|
| Central operations backend and panel | 8080 |
| Trendyol demo | 8081 |
| Hepsiburada demo | 8082 |
| n11 demo | 8083 |
| Company storefront demo | 8084 |
| Physical branch sales demo | 8085 |

Each channel has its own Maven project, executable JAR, port, in-memory inventory,
and order list. A shared channel-core library avoids duplicating the demo behavior.
These are deliberately simplified local simulations, not copies of real provider
APIs and not connected to the real companies. They are bound to loopback.

## Walkthrough
1. Add a product and create its physical stock in the central panel.
2. Send the stock snapshot to the channels.
3. Open any channel panel and create a demo sale.
4. In the central panel, import pending channel orders.
5. Send updated stock snapshots to all channels. Available stock reflects reservations.
6. Repeat the import: acknowledged orders disappear from the pending feed. If an
   acknowledgement is lost, central idempotency prevents a second reservation.

Stop all six processes using `./scripts/Stop-Demo.ps1`. It checks recorded process
identities before stopping them. Stop the demo before deleting or cleaning `target`.

For a repeatable HTTP walkthrough, run `./scripts/Verify-Demo.ps1` after startup.
It creates one sample product, starts one sale in each channel, imports them,
checks stock consistency, and replays one order to check duplicate handling.
The sample data is retained so it can be inspected in the panel.

## Explicit limitations of this delivery
- Synchronization is started manually; automatic polling and durable retry tracking
  are future work. A channel failure is reported and can be retried.
- This is one merchant's system, not a multi-tenant SaaS. Imported orders belong to
  the integration operator; administrator operations views aggregate all channels.
- Imported line prices currently come from the central catalog. Provider-specific
  price, discount, customer, and SKU mapping adapters are future work.
- Branch sales currently enter the same reservation flow as online orders; final
  point-of-sale completion and warehouse-specific inventory are future work.
- Cargo simulation, shipment tracking, cancellation, payment completion and returns
  have not been implemented in this delivery.
- Local simulator state resets on restart. The central demo database persists until
  its file is removed. These applications should not be exposed publicly.

## Engineering workflow
The work is developed in a feature branch. Local tests and a reviewable change
summary precede a GitHub pull request. Review and merge happen before deployment.
This personal portfolio is developed with AI assistance; the owner reviews and
studies the changes. No team collaboration history is fabricated.
