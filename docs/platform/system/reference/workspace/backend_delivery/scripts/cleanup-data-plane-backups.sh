#!/usr/bin/env bash
set -euo pipefail

: "${BACKUP_OUTPUT_DIR:?Set BACKUP_OUTPUT_DIR}"
readonly DRY_RUN="${DRY_RUN:-true}"
readonly DAILY_RETENTION_DAYS="${DAILY_RETENTION_DAYS:-14}"
readonly WEEKLY_RETENTION_DAYS="${WEEKLY_RETENTION_DAYS:-56}"
readonly MONTHLY_RETENTION_DAYS="${MONTHLY_RETENTION_DAYS:-365}"

if [[ "$DRY_RUN" != "true" && "$DRY_RUN" != "false" ]]; then
  printf 'DRY_RUN must be true or false.\n' >&2
  exit 2
fi

cleanup_tier() {
  local tier="$1" days="$2"
  while IFS= read -r -d '' artifact; do
    printf '[CLEANUP] %s\n' "$artifact"
    if [[ "$DRY_RUN" == "false" ]]; then
      rm -f -- "$artifact" "${artifact}.sha256"
    fi
  done < <(find "$BACKUP_OUTPUT_DIR" -type f \
    -name "delivery-data-${tier}-*.tar.gz.enc" -mtime "+${days}" -print0)
}

cleanup_tier daily "$DAILY_RETENTION_DAYS"
cleanup_tier weekly "$WEEKLY_RETENTION_DAYS"
cleanup_tier monthly "$MONTHLY_RETENTION_DAYS"

printf 'Backup cleanup completed (dry_run=%s).\n' "$DRY_RUN"
