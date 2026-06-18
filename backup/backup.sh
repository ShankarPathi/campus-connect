#!/bin/sh
# Nightly MongoDB backup with 7-day retention (Story 10.4, architecture §14 / NFR-5).
# Runs inside a mongo:8.0 container (has mongodump + GNU date). Sleeps until the next 02:30,
# dumps a gzipped archive to /backups, prunes archives older than 7 days, repeats.
#
# IMPORTANT: /backups lives on the single VM — copy it OFF-BOX (rsync/scp/object store) for a real
# backup (no PITR/HA on free tier). See DEPLOY.md. Restore:
#   mongorestore --uri "mongodb://mongodb:27017/campusconnect" --gzip --archive=/backups/<file> --drop
set -eu

MONGO_URI="${MONGO_URI:-mongodb://mongodb:27017/campusconnect}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"

mkdir -p "$BACKUP_DIR"
echo "[backup] started; nightly at 02:30, ${RETENTION_DAYS}-day retention, dir=$BACKUP_DIR"

while true; do
  now=$(date +%s)
  target=$(date -d 'today 02:30' +%s 2>/dev/null || echo 0)
  if [ "$target" -le "$now" ]; then
    target=$(date -d 'tomorrow 02:30' +%s)
  fi
  sleep "$((target - now))"

  ts=$(date +%Y-%m-%d_%H%M)
  archive="$BACKUP_DIR/cc-$ts.archive.gz"
  echo "[backup] $(date -Iseconds) dumping -> $archive"
  if mongodump --uri "$MONGO_URI" --archive="$archive" --gzip; then
    echo "[backup] ok; pruning archives older than ${RETENTION_DAYS}d"
    find "$BACKUP_DIR" -name 'cc-*.archive.gz' -mtime "+$RETENTION_DAYS" -delete
  else
    echo "[backup] ERROR: mongodump failed" >&2
  fi
done
