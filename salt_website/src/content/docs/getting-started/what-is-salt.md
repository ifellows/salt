---
title: What is SALT?
description: An introduction to System Assisted Link Tracing — the sampling methodology and the software platform.
---

SALT (System Assisted Link Tracing) is a platform for running **continuous, facility-based link-tracing surveys**. It is designed to replace — or complement — the periodic Bio-Behavioral Surveys (BBS) that are the current standard for monitoring key populations (KP) in HIV response programs.

## The problem with periodic surveys

Traditional BBS surveys using Respondent-Driven Sampling (RDS) are conducted every two to five years. By the time results are analysed and published, the data may be 12–18 months old. That lag makes it difficult to detect emerging trends, measure the impact of interventions, or respond to sudden changes in risk behaviour.

Periodic surveys are also expensive. Each round requires field mobilisation, temporary sites, dedicated field teams, and significant coordination.

## How SALT is different

SALT turns the BBS from a periodic event into an ongoing process:

- **Facility-based**: Data is collected by staff already working at existing health facilities — clinics, voluntary counselling and testing centres, and other KP-serving sites. No dedicated field offices required.
- **Continuous**: There is no fixed start or end date. Participants are enrolled and interviewed as they arrive, and data accumulates over time.
- **Coupon-based**: Like RDS, each participant receives coupons to recruit members of their network. Recruitment chains are tracked, enabling estimation of population-level indicators using established RDS estimators applied to rolling time windows.
- **Audio-assisted**: The tablet app guides participants through the survey with audio playback in their preferred language (ACASI — Audio Computer-Assisted Self-Interview), reducing literacy requirements and interviewer bias.
- **Offline-capable**: Tablets store data locally (encrypted with SQLCipher) and sync to the server automatically when connectivity is available.

## The SALT software platform

The SALT platform consists of three components:

### Management server

A web application (Node.js / Express / SQLite) that administrators use to:

- Configure surveys, questions, skip logic, and eligibility criteria
- Manage participating facilities
- Monitor uploads and view real-time statistics
- Export data in wide, long, or RDS format
- Generate Quarto-based analytical reports

### Facility tablet app

An Android application installed on tablets at each participating facility. Features:

- Step-by-step interview workflow with audio playback
- Fingerprint-based duplicate detection (SecuGen HU20-A scanner)
- Offline-first data storage, encrypted at rest
- Automatic sync and upload when online
- Coupon tracking and recruitment payment management

### Analytics platform

Statistical analysis dashboards applying RDS estimators to rolling time windows of SALT data. Currently delivered as Quarto reports embedded in the management server; a standalone analytics platform is planned for a future release.

## Who is SALT for?

SALT is designed for:

- **Public health programs** running key population surveys in low- and middle-income countries
- **Researchers** studying HIV risk behaviour using link-tracing designs
- **Program managers** who need real-time data to monitor and adjust interventions

## Further reading

- [Installation](/getting-started/installation/) — stand up the server in one command
- [Management dashboard overview](/management/dashboard/) — what you see after logging in
- [Tablet setup](/tablet/setup/) — connecting a tablet to your server
