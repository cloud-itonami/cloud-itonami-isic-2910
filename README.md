# cloud-itonami-isic-2910

Open Business Blueprint for **ISIC Rev.5 2910**: manufacture of motor
vehicles -- vehicle assembly, end-of-line quality screening and
Certificate-of-Conformity issuance for a community motor-vehicle
plant.

This repository publishes a motor-vehicle-manufacturing actor --
vehicle intake, per-jurisdiction type-approval/homologation rules
verification, end-of-line-defect screening, robot vehicle-dispatch
and Certificate-of-Conformity finalization -- as an OSS business
that any qualified motor-vehicle plant can fork, deploy, run, improve
and sell, so a plant keeps its own construction and type-approval
history instead of renting a closed MES / quality SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **Automotive Advisor ⊣
Automotive Governor**.

## Scope note: manufacturing, not vehicle operation

This repository is scoped to **building** motor vehicles (assembly,
end-of-line quality, type-approval evidence). It is not a fleet-
operator or dealership vertical (registration, insurance, resale).
Distinct from:

- `cloud-itonami-isic-2410` — basic iron and steel **manufacturing**
- `cloud-itonami-isic-2811` — engines and turbines **manufacturing**
- `cloud-itonami-isic-3011` — ships and floating structures **manufacturing**
- `cloud-itonami-isic-4730` — automotive-fuel-retail (forecourt operator)

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (assembly, fit-up,
finishing, end-of-line scan) operate under an actor that proposes
actions and an independent **Automotive Governor** that gates them.
The governor never issues a Certificate of Conformity itself;
`:high`/`:safety-critical` actions (`:actuation/dispatch-vehicle`,
`:actuation/issue-conformity-certificate`) require human sign-off.

## Core contract

```text
vehicle intake + type-approval rules verify + end-of-line quality screen
  -> Automotive Advisor proposal
  -> Automotive Governor (HARD holds un-overridable)
  -> phase gate (actuation always escalates)
  -> human approval for high stakes
  -> append-only ledger + draft records
```

## Actuation honesty

Dispatching an assembly/finishing robot and issuing a Certificate of
Conformity produce **unsigned draft records and ledger facts only**.
This actor does not talk to real plant control systems or
type-approval portals. Signature and hardware dispatch are the
motor-vehicle plant's own acts.

## Ops

| Op | Effect |
|---|---|
| `:vehicle/intake` | normalize vehicle directory patch (phase 3 may auto-commit when clean) |
| `:type-approval-rules/verify` | per-jurisdiction Certificate-of-Conformity evidence checklist (always human) |
| `:end-of-line-quality/screen` | end-of-line defect screen (HARD hold if unresolved) |
| `:actuation/dispatch-vehicle` | draft vehicle-dispatch record (always human) |
| `:actuation/issue-conformity-certificate` | draft Certificate-of-Conformity record (always human) |

## Social / regulatory hand-off

```clojure
(require '[automotive.store :as store]
         '[automotive.export :as export])

(def db (store/seed-db))
(export/audit-package db)           ;; EDN maps for type-approval/flag hand-off
(export/package->csv-bundle db)     ;; CSV bundle (vehicles/ledger/dispatches/conformity-certificates)
```

Operator console (static sample): `docs/samples/operator-console.html`.

## Develop

```bash
clojure -M:dev:test
clojure -M:lint
clojure -M:dev:run
```

## License

AGPL-3.0-or-later — see `LICENSE`.

## Operator console (Pages)

After enabling GitHub Pages (Settings → Pages → GitHub Actions), the
static console is at:

https://cloud-itonami.github.io/cloud-itonami-isic-2910/

Local: open `docs/index.html` or `docs/samples/operator-console.html`.

## Export audit package (CLI)

```bash
clojure -M:dev:export
# or: clojure -M:dev:export /tmp/audit-2910
```

Writes CSV files under `out/audit-package/` (or the given directory).
