# Running it on a server

The whole stack on one small server, behind HTTPS, released by GitHub Actions every time `main` changes. It is the same `compose.yml` a laptop runs, with a production layer over it ([`compose.prod.yml`](../compose.prod.yml)).

```
                      ┌──────────────────────── the server ────────────────────────┐
 browser ── 443 ──▶  Caddy ──▶ web ──▶ api ──▶ PostgreSQL, Redis, Kafka ──▶ notifications ──▶ mail service
                      │   ├──▶ api (Swagger UI)        Prometheus ◀── api, notifications   │
                      │   ├──▶ Grafana                                                     │
                      │   └──▶ Jaeger                                                      │
                      └────────────────────────────────────────────────────────────────────┘
```

## What the production layer changes

| | On a laptop | On a server |
| --- | --- | --- |
| Images | Built from the sources | Pulled from `ghcr.io`, tagged with the commit they were built from |
| Reachable from outside | Every service, on its own port | Ports 80 and 443 of Caddy, nothing else |
| HTTPS | No | Yes; Caddy gets and renews the certificates |
| Session cookie | Sent over HTTP | `Secure`: HTTPS only |
| Secrets | Development defaults | No defaults: the stack refuses to start without them |
| Tickets | Caught by Mailpit | Sent to real mailboxes over authenticated, encrypted SMTP |
| Kafka's log, metrics | Gone with the container | On volumes, so a release loses nothing |
| Memory | Whatever the JVMs take | Heaps are capped: the stack fits in 4 GB |

Four names are served, each with its own certificate:

| Name | What answers |
| --- | --- |
| `SITE_HOST` | The cinema: the web client |
| `API_HOST` | The API on its own, Swagger UI at the root |
| `GRAFANA_HOST` | The dashboard; anyone may look, changing it needs the admin password |
| `TRACES_HOST` | Jaeger: a booking from the click to the email |

PostgreSQL, Redis, Kafka, Prometheus and the management ports of both services have no way in from outside.

## How a release happens

1. A pull request is merged into `main` and [CI](../.github/workflows/ci.yml) passes on it.
2. [Deploy](../.github/workflows/deploy.yml) builds the three images and pushes them to `ghcr.io/antoniosifov/popcorn-palace-{api,notifications,web}`, tagged with the commit and `latest`.
3. It connects to the server over SSH with a key that can do one thing there: run [`deploy.sh`](deploy.sh) with a commit.
4. `deploy.sh` checks out that commit (for the compose files and the configuration next to them), pulls its images and runs `docker compose up --wait`. Only what changed is restarted; if something does not become healthy, the release fails and the workflow says so.
5. The workflow asks the site for `/api/health`.

Going back is the same thing with an older commit: `deploy/deploy.sh <commit>` on the server.

Without a server (no `DEPLOY_HOST` variable in the repository) the workflow publishes the images and stops there.

## Setting a server up

Needs: a server with Ubuntu 24.04, 4 GB of memory and a public address (x86-64: the images are built for it); a domain; an account with a mail service that speaks SMTP.

**1. DNS.** Point the four names at the server: an `A` record for `popcorn.example.com` and one for `*.popcorn.example.com`.

**2. The server.** As root:

```bash
git clone https://github.com/antoniosifov/popcorn-palace /opt/popcorn-palace
/opt/popcorn-palace/deploy/bootstrap.sh
```

[`bootstrap.sh`](bootstrap.sh) installs Docker from Docker's own repository, limits container logs, adds 2 GB of swap, closes every port but SSH, 80 and 443, creates the `deploy` account that releases are made from, and writes `/opt/popcorn-palace/.env` from [`.env.example`](.env.example) with freshly made secrets (the database password, the key that signs sessions, the administrator's and Grafana's passwords).

**3. Settings.** Fill in the rest of `.env` on the server: the four names, `ADMIN_EMAIL`, the mail service (`MAIL_PASSWORD`, `MAIL_FROM`) and, for a programme of films that are in cinemas now, `TMDB_API_KEY`.

**4. The images.** The first run of the Deploy workflow creates three packages in the registry. They start out private: make each one public (GitHub > the package > Package settings > Change visibility), or log the server in with a token that may read them.

**5. The first release.** `sudo -u deploy -H /opt/popcorn-palace/deploy/deploy.sh` on the server. Caddy asks for the certificates when it starts, which takes a few seconds once DNS answers.

**6. Releases from GitHub.** Make a key pair for the workflow (`ssh-keygen -t ed25519 -f deploy-key -N ""`), and:

| Where | What |
| --- | --- |
| `/home/deploy/.ssh/authorized_keys` on the server | The public half, limited to releasing: `command="/opt/popcorn-palace/deploy/deploy.sh",no-port-forwarding,no-agent-forwarding,no-X11-forwarding,no-pty ssh-ed25519 AAAA... deploy` |
| Repository secret `DEPLOY_SSH_KEY` | The private half |
| Repository secret `DEPLOY_KNOWN_HOSTS` | The output of `ssh-keyscan -t ed25519 <server>`, so the workflow talks to this server and no other |
| Repository variable `DEPLOY_HOST` | The address of the server |
| Repository variable `SITE_HOST` | The name of the site, for the check after a release |

## Mail

Tickets are sent through a mail service over SMTP on port 587: the service logs in, and refuses to send anything, its password included, unless the connection has been encrypted first (`MAIL_SECURE` in [the notifications service](../notifications/README.md#run-locally)). The values in `.env.example` are those of [Resend](https://resend.com); any service that speaks SMTP will do. The sender's domain has to be verified with the mail service, or mailboxes will treat the tickets as spam.

Registration on the site is open and addresses are not confirmed, so a ticket goes to whatever address a visitor typed. What keeps that from being abused is the limit on registrations per visitor (`AUTH_RATE_LIMIT`) and the daily quota of the mail service. Sending from a subdomain kept for this (`mail.example.com`) keeps the reputation of the main domain out of it.

## Looking after it

| | |
| --- | --- |
| What is running | `docker compose ps` in `/opt/popcorn-palace` |
| Logs | `docker compose logs -f api` (one JSON object per line) |
| Which release | `git rev-parse HEAD` in `/opt/popcorn-palace`; images carry the same commit as their tag |
| Starting over with an empty cinema | `docker compose down -v`, then release again. Certificates are lost with the volumes and requested anew |

What this does not have, because one server showing a project does not need it: backups of the database, a second server, and alerts when something is down.
