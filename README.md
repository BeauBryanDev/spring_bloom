# Spring-Bloom

Spring-Bloom is a wholesale flower export platform built for a fictional
Colombian flower export business. The project explores what a real,
professionally structured Spring Boot application looks like when a
computer vision agent is placed at the center of the sales process instead
of bolted on as an add-on feature.

Customers interact with **Florabelle**, an AI sales agent embedded in the
site chat. A customer can describe what they need or upload a photo of a
flower; Florabelle identifies the species with a trained vision model,
checks it against the live catalog, and drafts an accurate quotation
without a human in the loop.

This is a personal, non-commercial project.

## What it does

- Identifies flower species from a photo using a YOLOv11s-seg model trained
  on a filtered 90-species dataset.
- I do not handle segementation mask overlay in Java bakcend, this is painfully 
  difficult and my Agent do not need the seg mask ,just only know what flower is it
  then search for it on database catalog,  provide prices as customer services.
- Classifies each species into a commercial availability bucket: in stock,
  incoming restock, import on request, or not sold.
- Drives a conversational sales flow through Florabelle, an AI agent built
  with Spring AI, that turns a customer request into a structured
  quotation.
- Prices individual stems, bouquets, and garlands through dedicated pricing
  strategies, including bundle discounts.
- Persists quotations and orders with price and name snapshots, so a later
  catalog change never alters a historical document.
- Provides an admin dashboard, protected by session-based authentication,
  for managing the catalog, stock, and orders.

## Architecture

The backend follows a hexagonal (ports and adapters) architecture with
SOLID principles.

- **Domain** — Business rules with no framework dependency: flower
  species, pricing strategies, discount rules.
- **Ports** — Interfaces the domain defines to talk to the outside world,
  such as the catalog repository and the vision classifier.
- **Adapters** — Concrete implementations of those ports: the JPA/Postgres
  persistence adapter, the ONNX Runtime vision adapter, the REST
  controllers.
- **Application services** — Use cases that orchestrate ports and domain
  logic, for example turning a classified photo and a customer message
  into a draft quotation.

Dependencies always point inward: adapters depend on the domain through
its ports, never the other way around.

## Tech stack

- Java, Spring Boot
- Spring AI, for the Florabelle conversational agent
- ONNX Runtime, for serving the trained vision model
- PostgreSQL, with Flyway migrations
- Thymeleaf, for the storefront frontend

## Project status

Actively in development. The vision model, catalog domain, and pricing
logic are implemented. The public storefront and the chat integration are
in progress.

## Disclaimer

Spring-Bloom is a portfolio and learning project. It does not represent a
real business, and it is not intended for commercial use, this is just for learning
Agentic AI  in Java with a real clean code architecture. 
 
