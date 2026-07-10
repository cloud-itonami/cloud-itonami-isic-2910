# ADR-0001: Automotive Advisor ⊣ Automotive Governor architecture

- Status: Accepted (2026-07-10)
- Repository: `cloud-itonami-isic-2910` (ISIC Rev.5 `2910`)

## Context

Motor-vehicle manufacturing (assembly, end-of-line quality inspection,
type-approval/homologation, Certificate of Conformity issuance) needs
the same governed-actor pattern as the rest of the cloud-itonami
fleet: an untrusted advisor proposes; an independent governor may
HOLD; high-stakes actuation never auto-commits.

This vertical is the third classic heavy-industry manufacturing
vertical after `cloud-itonami-isic-2410` (basic iron and steel) and
`cloud-itonami-isic-2811` (engines and turbines), and the fourth
manufacturing-sector full actor overall after `cloud-itonami-isic-3030`
(aerospace).

## Decision

1. Namespaces live under `automotive.*` with the standard
   facts / registry / store / governor / phase / advisor / operation / sim
   shape.
2. Entity is a **vehicle**, not an aircraft assembly, hull block or
   steel heat.
3. Dual actuation on the same entity:
   - `:actuation/dispatch-vehicle` (robot assembly/finishing dispatch draft)
   - `:actuation/issue-conformity-certificate` (Certificate-of-Conformity draft)
4. Double-actuation guards use dedicated booleans
   (`:vehicle-dispatched?`, `:conformity-certified?`), never a status
   lifecycle (ADR-2607071320 / 6492 lesson).
5. `vehicle-emissions-out-of-range?` continues the fleet two-sided
   range check family (after testlab / conservation / water /
   aerospace / steelworks / turbine), applied here to a vehicle's own
   measured emissions deviation against its own recorded
   type-approval spec bounds.
6. End-of-line defect unresolved is evaluated unconditionally so
   `:end-of-line-quality/screen` itself can HARD-hold (parksafety
   ADR-2607071922 Decision 5 discipline).
7. Spec-basis catalog seeds JPN (MLIT type designation) / USA (NHTSA
   FMVSS self-certification) / GBR (DVSA / UNECE WVTA GB adoption) /
   DEU (KBA / EU Whole Vehicle Type Approval) only; missing
   jurisdictions are uncovered, never fabricated.

## Consequences

(+) Motor-vehicle manufacturing gains a forkable OSS operating stack
with auditable governor holds.
(+) Reuses langgraph + store dual-backend parity without new physics.
(−) No physical plant digital-twin tick in this repo (follow-up domain
data, e.g. giemon-factory style layout, is out of scope here).
(−) Type-approval-authority coverage is a starting catalog, not
exhaustive.

## Related

- Superproject fleet ADR for this promotion (motor-vehicle-2910-coverage)
- Sibling architecture: `cloud-itonami-isic-2410` docs/adr/0001,
  `cloud-itonami-isic-2811` docs/adr/0001
