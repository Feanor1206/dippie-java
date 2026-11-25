#!/usr/bin/env bash
# Sao lưu MySQL của Dippie qua docker compose exec, nén gzip và giữ N bản gần nhất.
# Cách dùng:
#   ./scripts/backup-mysql.sh [THƯ_MỤC_SAO_LƯU] [SỐ_BẢN_GIỮ_LẠI]
# Ví dụ:
#   ./scripts/backup-mysql.sh ./backups 14

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

BACKUP_DIR="${1:-${BACKUP_DIR:-$REPO_ROOT/backups}}"
KEEP_COUNT="${2:-${KEEP_COUNT:-14}}"
COMPOSE_FILE="${COMPOSE_FILE:-$REPO_ROOT/infrastructure/docker-compose.yml}"
ENV_FILE="${ENV_FILE:-$REPO_ROOT/infrastructure/.env}"

if ! [[ "$KEEP_COUNT" =~ ^[1-9][0-9]*$ ]]; then
    echo "Lỗi: Số bản sao lưu giữ lại phải là số nguyên dương (nhận được: '$KEEP_COUNT')." >&2
    exit 1
fi

if [ ! -f "$COMPOSE_FILE" ]; then
    echo "Lỗi: Không tìm thấy tệp Compose: $COMPOSE_FILE" >&2
    exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
    echo "Lỗi: Không tìm thấy tệp môi trường: $ENV_FILE" >&2
    exit 1
fi

# Đọc biến từ tệp .env mà không source trực tiếp để tránh chạy nhầm lệnh shell
read_env_var() {
    local key="$1"
    local default_val="$2"
    local line val
    line="$(grep -E "^[[:space:]]*${key}=" "$ENV_FILE" | tail -n 1 || true)"
    if [ -z "$line" ]; then
        printf '%s' "$default_val"
        return
    fi
    val="${line#*=}"
    val="${val%$'\r'}"
    val="${val#\"}"
    val="${val%\"}"
    val="${val#\'}"
    val="${val%\'}"
    printf '%s' "${val:-$default_val}"
}

MYSQL_DB="$(read_env_var "MYSQL_DB" "movie_rcm_db")"
mkdir -p "$BACKUP_DIR"

TIMESTAMP="$(date -u +%Y%m%d-%H%M%S)"
OUT_FILE="$BACKUP_DIR/dippie-mysql-${TIMESTAMP}.sql.gz"
TMP_FILE="${OUT_FILE}.tmp"

cleanup() {
    rm -f "$TMP_FILE"
}
trap cleanup EXIT

echo "[dippie-backup] Bắt đầu sao lưu schema '$MYSQL_DB'..."

# Chạy mysqldump trong container mysql, truyền mật khẩu qua MYSQL_PWD để không lộ trong danh sách tiến trình
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
    sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump \
        --user=root \
        --default-character-set=utf8mb4 \
        --single-transaction \
        --quick \
        --routines \
        --triggers \
        --events \
        --no-tablespaces \
        --set-gtid-purged=OFF \
        "$1"' _ "$MYSQL_DB" \
    | gzip -9 > "$TMP_FILE"

# Kiểm tra tệp nén hợp lệ trước khi đổi sang tên chính thức
gzip -t "$TMP_FILE"
if [ ! -s "$TMP_FILE" ]; then
    echo "Lỗi: Tệp sao lưu rỗng." >&2
    exit 1
fi

chmod 600 "$TMP_FILE"
mv "$TMP_FILE" "$OUT_FILE"
trap - EXIT

echo "[dippie-backup] Đã tạo bản sao lưu: $OUT_FILE"
if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$OUT_FILE"
elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$OUT_FILE"
fi

# Xóa bớt bản cũ, chỉ giữ lại KEEP_COUNT bản mới nhất
shopt -s nullglob
existing=("$BACKUP_DIR"/dippie-mysql-*.sql.gz)
if [ "${#existing[@]}" -gt "$KEEP_COUNT" ]; then
    mapfile -t sorted < <(printf '%s\n' "${existing[@]}" | LC_ALL=C sort -r)
    for ((i = KEEP_COUNT; i < ${#sorted[@]}; i++)); do
        echo "[dippie-backup] Xóa bản sao lưu cũ: $(basename "${sorted[$i]}")"
        rm -f "${sorted[$i]}"
    done
fi
