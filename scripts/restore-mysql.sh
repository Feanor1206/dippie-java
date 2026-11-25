#!/usr/bin/env bash
# Khôi phục cơ sở dữ liệu MySQL của Dippie từ tệp .sql.gz hoặc .sql qua docker compose exec.
# Cách dùng:
#   ./scripts/restore-mysql.sh <đường-dẫn-tệp-sao-lưu.sql.gz> [--yes]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

COMPOSE_FILE="${COMPOSE_FILE:-$REPO_ROOT/infrastructure/docker-compose.yml}"
ENV_FILE="${ENV_FILE:-$REPO_ROOT/infrastructure/.env}"

BACKUP_FILE=""
AUTO_YES="false"

for arg in "$@"; do
    case "$arg" in
        --yes|-y)
            AUTO_YES="true"
            ;;
        -h|--help)
            echo "Cách dùng: $0 <tệp-sao-lưu.sql.gz|.sql> [--yes]"
            exit 0
            ;;
        *)
            if [ -z "$BACKUP_FILE" ]; then
                BACKUP_FILE="$arg"
            else
                echo "Lỗi: Tham số không hợp lệ: $arg" >&2
                exit 1
            fi
            ;;
    esac
done

if [ -z "$BACKUP_FILE" ]; then
    echo "Cách dùng: $0 <tệp-sao-lưu.sql.gz|.sql> [--yes]" >&2
    exit 1
fi

if [ ! -f "$BACKUP_FILE" ] || [ ! -r "$BACKUP_FILE" ]; then
    echo "Lỗi: Không thể đọc tệp sao lưu: $BACKUP_FILE" >&2
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

if [[ "$BACKUP_FILE" == *.gz ]]; then
    gzip -t "$BACKUP_FILE"
fi

if [ "$AUTO_YES" != "true" ]; then
    echo "CẢNH BÁO: Thao tác này sẽ ghi đè dữ liệu hiện tại trong schema '$MYSQL_DB'."
    echo "Tệp khôi phục: $BACKUP_FILE"
    read -r -p "Nhập YES để xác nhận khôi phục: " answer
    if [ "$answer" != "YES" ]; then
        echo "Đã hủy khôi phục."
        exit 1
    fi
fi

echo "[dippie-restore] Đang khôi phục schema '$MYSQL_DB' từ '$BACKUP_FILE'..."

if [[ "$BACKUP_FILE" == *.gz ]]; then
    gzip -dc "$BACKUP_FILE" | docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
        sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --default-character-set=utf8mb4 "$1"' _ "$MYSQL_DB"
else
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
        sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --default-character-set=utf8mb4 "$1"' _ "$MYSQL_DB" \
        < "$BACKUP_FILE"
fi

echo "[dippie-restore] Khôi phục hoàn tất. Hãy kiểm tra /api/health và khởi động lại app nếu cần nạp lại bộ nhớ đệm."
