#!/bin/sh
set -a; . ./.env; set +a
exec ./mvnw spring-boot:run "$@"

