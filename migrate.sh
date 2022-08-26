#!/bin/bash

REGEX="JDBC_DATABASE_URL=(.+)"

for line in $(heroku run printenv)
do
  if [[ $line =~ $REGEX ]]
  then
    DB_URL="${BASH_REMATCH[1]}"
  fi
done

export FLYWAY_URL="$DB_URL"

flyway migrate