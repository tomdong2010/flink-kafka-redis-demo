#!/usr/bin/env bash
# End-to-end check of a running stack (docker compose up -d --build): waits until the Flink job
# is RUNNING, has completed a checkpoint, and results reach the dashboard API through Redis.
set -euo pipefail

DASHBOARD=${DASHBOARD:-http://localhost:8088}
FLINK=${FLINK:-http://localhost:8081}
TIMEOUT=${TIMEOUT:-240}

deadline=$((SECONDS + TIMEOUT))
check() { # name, command
  local name=$1; shift
  until "$@" >/dev/null 2>&1; do
    if ((SECONDS > deadline)); then
      echo "FAIL: $name (after ${TIMEOUT}s)" >&2
      return 1
    fi
    sleep 3
  done
  echo "ok: $name"
}

job_running()    { curl -fsS "$FLINK/jobs/overview" | grep -q '"state":"RUNNING"'; }
checkpointed()   { id=$(curl -fsS "$FLINK/jobs" | sed -n 's/.*"id":"\([0-9a-f]*\)".*/\1/p' | head -1)
                   curl -fsS "$FLINK/jobs/$id/checkpoints" | grep -Eq '"completed":[1-9]'; }
has_trending()   { curl -fsS "$DASHBOARD/api/trending" | grep -q '"rank":1'; }
has_revenue()    { curl -fsS "$DASHBOARD/api/gmv" | grep -q '"start"'; }
page_served()    { curl -fsS "$DASHBOARD/" | grep -q 'Trending products'; }

check "Flink job is RUNNING" job_running
check "a checkpoint completed" checkpointed
check "trending products reached Redis" has_trending
check "GMV windows reached Redis" has_revenue
check "dashboard page is served" page_served
echo "Top products right now:"
curl -fsS "$DASHBOARD/api/trending" | tr '{' '\n' | grep '"rank"' | head -3 | sed 's/^/  /'
