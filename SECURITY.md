# Security Policy

This repository is a fork of [Apache Fineract](https://github.com/apache/fineract), the open-source
core banking platform. Because Fineract handles financial and personal data (clients, loans,
savings, general ledger), please treat any suspected vulnerability as sensitive.

## Supported Versions

Security fixes are applied only to the default branch of this repository.

| Version / branch                         | Supported          |
| ---------------------------------------- | ------------------ |
| `main` (latest)                          | :white_check_mark: |
| Any other branch, tag or older snapshot  | :x:                |

## Reporting a Vulnerability

**Do not open a public issue, pull request or discussion for security problems.**

Report vulnerabilities privately through GitHub Private Vulnerability Reporting:

1. Go to the repository's **Security** tab.
2. Click **Report a vulnerability** (or open
   <https://github.com/Cognition-Partner-Workshops/fineract/security/advisories/new>).
3. Describe the issue, affected module(s) (e.g. `fineract-provider`, `fineract-loan`,
   `fineract-core`), affected API endpoints, reproduction steps, and potential impact.

Please do not include real customer or tenant data in your report.

### What to expect

- Acknowledgement within **3 business days**.
- An initial assessment and severity rating within **10 business days**.
- Fixes are developed in a private security advisory fork and released on `main`;
  the advisory is published (with credit, if desired) once a fix is available.

## Upstream Apache Fineract

If the vulnerability also affects upstream Apache Fineract, it should additionally be reported to the
Apache Software Foundation security team following <https://www.apache.org/security/>
(security@apache.org). Do not disclose it publicly until the ASF has released a fix.
