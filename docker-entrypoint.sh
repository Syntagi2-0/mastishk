#!/bin/sh
set -eu

case "${DATABASE_URL:-}" in
  postgresql://*)
    database_host_path=${DATABASE_URL#postgresql://}
    # Split at the final @ so an @ inside a password cannot become part of the host.
    database_host_path=${database_host_path##*@}
    export SPRING_DATASOURCE_URL="jdbc:postgresql://${database_host_path}"
    ;;
  jdbc:postgresql://*)
    export SPRING_DATASOURCE_URL="$DATABASE_URL"
    ;;
esac

exec java -jar /app/app.jar
