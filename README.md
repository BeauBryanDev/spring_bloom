# Spring-Bloom

Spring-Bloom is a wholesale flower export platform built for a fictional
Colombian flower export business. The project explores what a real,
professionally structured Spring Boot application looks like when a
computer vision agent is placed at the center of the sales process instead
of bolted on as an add-on feature.

Customers interact with **Florabelle**, an AI sales agent embedded in the
site chat. A customer can describe what they need or upload a photo of a
flower; Florabelle identifies the species with a trained vision model,
checks it against the live catalog, answers policy questions by retrieving
the shop's own documents, and drafts an accurate, persisted quotation
without a human in the loop.

This is a personal, non-commercial project.

## What it does

- **Identifies flower species from a photo** using a YOLOv11s-seg model
  trained on a filtered 90-species dataset, served through ONNX Runtime.
  Box and class-score decoding, letterboxing and class-aware NMS are all
  implemented directly in Java; segmentation masks are intentionally
  ignored, since the agent only needs a species, not a pixel mask.
- **Classifies each species into a commercial availability bucket**: in
  stock, incoming restock, import on request, or not sold — and prices it
  live from the database, never from the vision model.
- **Drives a full conversational sales flow** through Florabelle, an AI
  agent built with Spring AI on Claude Haiku, with six tools: availability
  search, two quotation tools, complaint filing, photo identification, and
  retrieval over the shop's own policy documents.
- **Answers policy and botany questions with RAG**: four source PDFs
  (the corporate manual, two market reports, a botany textbook) are chunked
  and embedded into pgvector, with a similarity floor so an unanswerable
  question gets an honest "I don't know" instead of an invented policy.
- **Prices individual stems, bouquets, and garlands** through dedicated
  pricing strategies, with bundle discounts derived from stem count by a
  single shop-wide policy — never invented by the model.
- **Persists quotations with price and name snapshots**, so a later catalog
  change never alters a historical document, and generates human-readable
  `COT-`/`ORD-` document numbers with atomic collision retry.
- **Takes and reviews customer complaints**, tied to an order when one is
  known, closed only by shop staff, never by the agent.
- **Runs a real storefront**: catalog with filters, a product page per
  species, a server-side cart (never a browser-held price), and a contact
  page — all Thymeleaf, no SPA framework.
- **Provides an admin dashboard**, protected by real session-based login
  (BCrypt against a seeded `store_owner` row), for a sales/stock overview,
  stock editing, read-only order viewing, and complaint review.

## Architecture

The backend follows a hexagonal (ports and adapters) architecture with
SOLID principles.

- **Domain** — Business rules with no framework dependency: flower
  species, pricing strategies, discount rules, quotations, conversations,
  complaints.
- **Ports** — Interfaces the domain defines to talk to the outside world:
  repositories, the vision classifier, the chat agent, the knowledge
  search.
- **Adapters** — Concrete implementations of those ports: the JPA/Postgres
  persistence adapter, the ONNX Runtime vision adapter, the Spring AI chat
  adapter, the pgvector knowledge adapter, the REST and Thymeleaf
  controllers.
- **Application services** — Use cases that orchestrate ports and domain
  logic, for example turning a classified photo and a customer message
  into a draft quotation.

Dependencies always point inward: adapters depend on the domain through
its ports, never the other way around.

## Tech stack

- Java 21, Spring Boot 3.5
- Spring AI 1.1.1, Claude Haiku 4.5 for the Florabelle conversational agent
- ONNX Runtime, for serving the trained YOLOv11s-seg vision model
- PostgreSQL with the `pgvector` extension, Flyway migrations
- OpenAI `text-embedding-3-small`, for RAG embeddings only
- Thymeleaf, for the storefront and admin frontend
- Spring Security, for admin session login

## Running it locally

```bash
docker compose up -d                          # Postgres (pgvector/pgvector:pg16) on host port 5438
./run.sh                                      # loads .env, then runs the app
```

`.env` (gitignored) must supply `DB_USER`, `DB_PASSWORD`, `DB_NAME`,
`ANTHROPIC_API_KEY`, and `EMBEDDINGS_MODEL_KEY`. `./run.sh` is the
supported entry point — it sources `.env` before starting Maven, since
Spring Boot itself never reads that file.

The database is seeded by hand after the app's first boot (so Flyway has
created the schema):

```bash
docker cp seed_db.sql florabelle-postgres:/tmp/seed_db.sql
docker exec florabelle-postgres sh -c \
  'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /tmp/seed_db.sql'
```

The trained vision weights (`vision_model.onnx`, ~39MB) are gitignored and
must be obtained separately; the vision path and its tests are skipped
without them, everything else runs normally.

## Testing

```bash
set -a; . ./.env; set +a; ./mvnw test    # full suite against the real database
```

A bare `./mvnw test` (no `.env` loaded) silently skips every test that
touches Postgres or the vision model. No test ever calls the chat model
itself — that path is verified in live conversation, not CI, since it
costs money and is not deterministic.

## Project status

The full customer-facing backend works end to end: a photo becomes a
species, a species becomes a price, a request becomes a persisted
quotation, and a policy question is answered from the shop's own documents
rather than invented. The admin dashboard is built. What remains is the
**order half** of the domain — a quotation never yet becomes a real order,
stock is never decremented on a sale, and checkout (Stripe, test mode
only) is designed but not implemented.

## Disclaimer

Spring-Bloom is a portfolio and learning project. It does not represent a
real business, and it is not intended for commercial use — this is just
for learning agentic AI in Java with a real, clean-code architecture.
