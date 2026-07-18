#!/bin/sh
set -eu

database_url=${DB_URL:-${DATABASE_URL:-}}

case "$database_url" in
  postgresql://*)
    database_host_path=${database_url#postgresql://}
    # Split at the final @ so an @ inside a password cannot become part of the host.
    database_host_path=${database_host_path##*@}
    export SPRING_DATASOURCE_URL="jdbc:postgresql://${database_host_path}"
    ;;
  jdbc:postgresql://*)
    export SPRING_DATASOURCE_URL="$database_url"
    ;;
esac

exec java -jar /app/app.jar
