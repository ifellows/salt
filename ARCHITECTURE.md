# SALT Architecture

SALT (System Assisted Link Tracing) is a three-tier system for link-tracing
surveys. Two tiers are built today; the third is planned.

## Components

### 1. Android application — field data collection

A facility-based tablet app used by staff to enroll and interview participants.

- Multi-language surveys with audio playback (ACASI) for low-literacy populations
- Fingerprint-based duplicate-enrollment detection
- Offline-first: works without connectivity, syncs in the background
- Encrypted local storage (SQLCipher)
- Staff-validation workflows, coupon generation and tracking, participant payments

Built with Kotlin, Jetpack Compose, and Room. See the
[Android tablet setup guide](salt_android/README.md).

### 2. Management server — configuration and monitoring

A web application and API that is the hub of a SALT deployment.

- Survey builder: questions, options, skip logic and validation (JEXL), audio
- Facility, user, and lab-test management
- Real-time sync with tablets and upload monitoring
- The administrative dashboard — up-to-the-minute diagnostics and results
- Data export in wide, long, and RDS formats

Built with Node.js, Express, SQLite, and EJS; report rendering uses R + Quarto.
See the [management server guide](salt_management/README.md) and
[Docker deployment](salt_management/README-DOCKER.md).

### 3. Analytics platform — planned

Deeper statistical analysis on top of the collected data: RDS estimators,
rolling 6/12/24-month estimates, and stakeholder-facing dashboards.

## How the pieces connect

1. An administrator configures a survey and facilities on the **management server**.
2. **Tablets** register to a facility with an API key and sync the active survey.
3. Staff collect data on tablets — offline-capable — and the app uploads
   completed surveys back to the server.
4. The server aggregates everything; administrators monitor progress from the
   dashboard and export data for analysis.

## Security & privacy

- Encrypted data at rest on tablets (SQLCipher)
- Role-based access control on the server; bcrypt-hashed passwords
- Fingerprint templates never leave the tablet
- Facility API-key authentication for all tablet ↔ server traffic
- HTTPS in production (the installer provisions a Let's Encrypt certificate)
