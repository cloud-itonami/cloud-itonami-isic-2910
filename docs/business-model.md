# Business Model: Manufacture of Motor Vehicles

## Classification
- Repository: `cloud-itonami-isic-2910`
- ISIC Rev.5: `2910` — manufacture of motor vehicles — vehicle assembly, end-of-line quality screening and Certificate-of-Conformity issuance
- Social impact: vehicle-safety, supply-resilience, industrial-jobs

## Customer
- independent motor-vehicle manufacturers and contract assemblers needing auditable type-approval and production records
- contract plants assembling vehicles or major sub-assemblies for multiple OEMs
- plant operators needing verifiable build and end-of-line history for produced vehicles
- market regulators needing verifiable type-approval and conformity-of-production evidence
- programs that cannot accept closed, unauditable manufacturing-execution platforms

## Offer
- type-approval/homologation rules and jurisdiction-scope version management
- robotics-assisted assembly, finishing and end-of-line inspection records
- vehicle emissions-deviation and end-of-line chain-of-custody history
- Certificate-of-Conformity drafts and disclosure records
- role-based access and immutable audit ledger
- CSV/EDN audit package export for inspectors

## Revenue
- self-host setup fee
- managed hosting subscription per plant / assembly line
- support retainer with SLA
- assembly/end-of-line robot integration and maintenance

| Package | Customer | Price shape |
|---|---|---|
| Self-host | any qualified motor-vehicle plant | AGPL-3.0-or-later, free |
| Managed Starter | one vehicle plant (1 site, 1 line, 10–20 QA/homologation seats; typically 50–300 employees — an independent or contract assembler, not a major OEM) | ¥60,000/月 flat |
| Support retainer / robot integration | as scoped | quoted separately, not part of the Starter tier |

**Market-anchored (2026-08-10)**: benchmarked against 7 real products and
service providers. **Only 3 of the 7 publish a price, and none of those 3 is
an automotive product** — which is the single most important finding of this
survey and the reason confidence here is *medium*, not high:

> **Automotive MES and type-approval tooling disclose nothing. Not one
> automotive-specific product publishes a figure — Siemens Opcenter, Rockwell
> Plex and iBASEt Solumina/42Q are all custom enterprise quote only. And the
> homologation layer is not even a product market: type approval is sold as a
> *service* engagement by TÜV SÜD, UTAC, Ricardo and AEC, so no price list
> exists to benchmark against in the first place.** The band below is
> therefore assembled from *general-purpose* quality/inspection tooling a
> plant could bend to this job, not from observed automotive prices. The true
> comparison set is unobserved.

Published anchors, converted at ~¥150/$ for the assumed plant size above:

| Product | Published price (as printed) | ≈ JPY/月 at assumed size |
|---|---|---|
| [QC-One Lite](http://www.uis-inf.co.jp/products/qc-one/) (ユーアイエス、クラウド版) | 月額5万円 | ¥50,000 |
| [SafetyCulture](https://safetyculture.com/pricing/) (end-of-line inspection checklists) | Premium "$24 / seat / month" annual ("$29 / seat / month" monthly); Free ≤10 users; Enterprise custom | ¥72,000 ($24 × 20 seats) |
| [Tulip](https://tulip.co/pricing/) (deployed on automotive lines) | Essentials "$100/mo per interface billed annually", Professional "$250/mo per interface", 10-interface minimum | ¥150,000 (Essentials × 10 interfaces) |

**Not disclosed**: [Siemens Opcenter](https://www.siemens.com/en-us/products/opcenter/),
Rockwell Plex MES, iBASEt Solumina, 42Q — all custom quote;
[TÜV SÜD](https://www.tuvsud.com/en/industries/automotive/vehicle-type-approval-and-homologation),
UTAC, Ricardo, AEC — service engagements, no published rate.

The observable band is **¥50,000–¥150,000/月**. **¥60,000/月 sits in its lower
third.** It is placed above this repo's electronics sibling
(`cloud-itonami-isic-2620`, ¥35,000/月) for two evidenced reasons: the artifact
issued here is a Certificate of Conformity / type-approval record, a heavier
regulatory instrument than an EMC self-declaration; and the robotics layer is
not symbolic but a real git-coordinate dependency on
`kami-engine-vehicle-designer`, with the Automotive Governor independently
re-deriving `:sim-decel-g` / `:sim-crush-distance-m` from a genuinely
time-stepped crash simulation — more computation and more engineering
substance than any other manufacturing actor in this fleet carries. It is not
placed higher, because the actor touches no plant control system, emits only
unsigned draft records, and does not replace a CAE environment. Note also what
the buyer's real alternatives are: a six-to-seven-figure enterprise MES quote,
or a spreadsheet. The gap between those is where this tier lives.

Because no automotive comparator disclosed a price, **treat ¥60,000 as a
first, revisable placement rather than a market-validated one**, and revise it
against the first real quotes an operator obtains.

**Subscribe (2026-08-10)**: a live Stripe Payment Link for the Managed
Starter tier (¥60,000/月 flat) is available now —
[**subscribe to Managed Starter**](https://buy.stripe.com/9B6dRa9aD3963va6TUeEo0l).
This is a no-code Stripe-hosted checkout on Gftd Japan 株式会社's live
account; nothing in this repo's actor code changed, and managed-tenant setup
is manual fulfillment today with no automated onboarding. **No plant has
claimed or subscribed to this tier yet — this is a working checkout with zero
paid tenants, not a claim of existing revenue.**

## Trust Controls
- out-of-spec vehicles are blocked; a Certificate of Conformity is mandatory for release paths; vehicle history is immutable
- a robot action the governor refuses is never dispatched to hardware
- every dispatch, hold, approval and disclosure path is auditable
- sensitive design and production data stays outside Git
- a fabricated type-approval-rules citation, incomplete evidence, an
  out-of-spec vehicle emissions deviation, or an unresolved end-of-line
  defect -- each forces a hold, not an override
- Certificate-of-Conformity issuance is logged and escalated, and
  cannot be finalized twice for the same vehicle
