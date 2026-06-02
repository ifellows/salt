---
title: AI Report Builder (MCP)
description: Connect your own AI assistant (Claude) to build and render Quarto reports conversationally, using your own subscription.
---

The AI Report Builder lets an administrator connect their **own AI assistant**, claude.ai
(web), Claude Desktop, or Claude Code, to the SALT server and build [Quarto reports](/management/reports/)
by chatting. The assistant can read the survey **data dictionary** and **aggregate
summaries**, write the report's R/Quarto, and **render it on the server**, iterating until it
looks right. The finished report appears in **Reports → History** like any other.

It works over the **Model Context Protocol (MCP)**. Your AI subscription powers the work, so
SALT never sees an API key, and **no individual participant records ever leave the server**:
the assistant only receives the data dictionary, aggregate frequencies/summaries, the report
code, and render status.

## Enabling it (administrator / ops)

The feature is **on by default**. The main thing to set is the server's public URL so AI clients
can connect (and to disable it, set `MCP_ENABLED=false`):

```bash
MCP_PUBLIC_URL=https://your-salt-host.example.org   # the public HTTPS URL of this server
# MCP_ENABLED=false                                 # only if you want to turn it off
```

The server must be reachable over **HTTPS at a public URL** so the AI client can connect.
Sessions expire automatically: an access token lasts 30 minutes (refreshed silently) and every
session is **capped at 6 hours**, after which you sign in again.

The management dashboard's **Reports** tab has an **AI Report Builder** button that shows your
exact connector URL and these connection steps.

## Connecting from claude.ai

1. In claude.ai, open **Settings → Connectors → Add custom connector**.
2. Enter the connector URL: `https://your-salt-host.example.org/mcp`.
3. Click **Connect**. A SALT sign-in page opens; log in with your administrator account and
   click **Allow**.
4. Start a chat and say **"Help me build a SALT report"** (see below).

## Connecting from ChatGPT

1. In ChatGPT, open **Settings → Connectors** (this requires a plan that supports custom
   connectors / developer mode).
2. Add a connector / MCP server and enter the connector URL: `https://your-salt-host.example.org/mcp`.
3. Authorize by signing in with your SALT administrator account.

## Connecting from Claude Code / Claude Desktop

Claude Code:

```bash
claude mcp add --transport http salt https://your-salt-host.example.org/mcp
```

Run a tool or start a chat; Claude Code opens a browser for the same SALT sign-in. Claude
Desktop uses its **Settings → Connectors** dialog with the same `/mcp` URL.

## Building a report

Once connected, in a new chat just say:

> **Help me build a SALT report**

The assistant takes it from there: it reads the survey's data dictionary and summaries, writes
the report, renders it, and refines it as you describe what you want (for example, *"add HIV
prevalence by age band 18 to 25, 25 to 40, 40+"*). Finished reports appear under
**Reports → History**.

## What the assistant can do

| Capability | Notes |
| --- | --- |
| List surveys, read the data dictionary | One row per export variable (survey, rapid tests, labs) |
| See aggregate summaries | `table()`/`summary()` per variable, never raw rows |
| Read example report templates | The same templates as the Reports editor |
| Create, update, and render reports | Rendered HTML/PDF/DOCX appear in Reports → History |

The assistant reviews a markdown preview of each render to check its work, but cannot retrieve
individual responses. Reports it produces are ordinary reports: you can edit, schedule, and
download them from the [Reports](/management/reports/) tab.

## Security notes

- Connecting requires an **administrator** login; MCP access grants the same report
  capabilities an administrator already has.
- Tokens are stored hashed and can be revoked; every session ends within 6 hours.
- Rendering executes report code on the server, exactly as the existing Reports engine does.
