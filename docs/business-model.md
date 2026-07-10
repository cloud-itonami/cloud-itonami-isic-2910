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
