# Governance

`cloud-itonami-isic-8211` is an OSS open-business blueprint for
outsourced combined office administrative service operations
coordination (ISIC Rev.5 8211 -- combined office administrative service
activities).

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a proposal for an unverified/unregistered client contract, or a supply
  order naming an unverified/unregistered vendor, can never commit.
- the OfficeAdminGovernor remains independent of the advisor.
- hard policy violations (non-`:propose` effect, content finalizing
  disclosure of a client's confidential records/data to a third party or
  a legal/financial decision on the client's behalf, an op outside the
  closed allowlist) cannot be overridden by human approval.
- every task-record log, service-operation schedule, supply-order
  coordination and confidentiality-concern flag is auditable.
- client, employee and vendor data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or
license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, audit and data-flow
review.

Certified operators can lose certification for:
- bypassing task-record, staffing, supply-order or confidentiality
  policy checks
- mishandling client, employee or vendor data
- misrepresenting certification status
- failing to respond to security or confidentiality-breach incidents
