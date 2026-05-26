# SALT (System Assisted Link Tracing)

### 📖 Full documentation: **[surveysalt.com](https://surveysalt.com)**

---

SALT is a platform for running **any link-tracing survey design**. This includes
Respondent-Driven Sampling (RDS), but also designs where recruitment chains are
short (BBS-lite, starfish sampling), where staff are facility-based with limited
training, and/or where sampling is continuous with no set end date.

SALT takes the guesswork out of sampling: staff and participants are walked
through the survey process step by step, controlled by a central administrative
dashboard that provides up-to-the-minute diagnostics and results.

## Quick start

Stand up a complete, production-ready SALT server on a fresh machine. You need:

- A fresh Ubuntu server (a cloud VM is fine) with root SSH access.
- A domain name whose DNS **A record is already pointed at the server's IP**.

From the server, run:

```bash
curl -fsSL https://raw.githubusercontent.com/ifellows/salt/main/salt_management/install.sh \
  | sudo bash -s -- your-domain.example.org admin@example.org
```

That single command installs Docker, builds and starts the SALT server, opens
the firewall, and provisions HTTPS with a Let's Encrypt certificate. When it
finishes, browse to `https://your-domain.example.org` and sign in as
`admin` / `admin123` — then change that password immediately.

Already have a host with Docker, or need a non-default setup (your own TLS
termination, a prebuilt image, or local testing)? See
[Docker deployment](salt_management/README-DOCKER.md).

---

## Documentation

| Document | What it covers |
|----------|----------------|
| [Architecture overview](ARCHITECTURE.md) | The SALT components and how they fit together |
| [Docker deployment](salt_management/README-DOCKER.md) | Running the server on an existing host — options, upgrades, backups, reverse proxy |
| [Management server guide](salt_management/README.md) | Configuring surveys, facilities, users, lab tests, and data export |
| [Android tablet setup](salt_android/README.md) | Installing and configuring the field data-collection app |
| [SALT methodology](SALT.pdf) | The sampling design and statistical approach behind SALT |
