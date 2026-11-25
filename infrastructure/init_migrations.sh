#!/bin/bash
# Chạy lần lượt các tệp migration trong database/migrations khi khởi tạo volume MySQL mới.
(
    set -euo pipefail
    shopt -s nullglob

    files=(/opt/dippie/database-migrations/[0-9]*.sql)
    if [ "${#files[@]}" -eq 0 ]; then
        exit 0
    fi

    mapfile -t sorted < <(printf '%s\n' "${files[@]}" | LC_ALL=C sort)
    for file in "${sorted[@]}"; do
        echo "[dippie-init] Áp dụng migration: $(basename "$file")"
        MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket --user=root --batch \
            --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" < "$file"
    done
)
