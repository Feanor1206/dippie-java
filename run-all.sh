#!/usr/bin/env bash
set -euo pipefail

# Script chạy nhanh Dippie ở chế độ demo trên macOS và Linux.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="$ROOT_DIR/.tools"
RUN_DIR="$TOOLS_DIR/run"
TMP_DIR="$TOOLS_DIR/tmp"
PID_FILE="$RUN_DIR/pids.env"
BACKEND_LOG="$RUN_DIR/backend.log"
DESKTOP_LOG="$RUN_DIR/desktop.log"
DEFAULT_DATA_DIR="$ROOT_DIR/target/dippie-java"
CATALOG_NAMES=(jadoo larva ori doraemon trang_quynh peppa_pig)
NODE_FALLBACK_VERSION="v22.15.0"

ACTION="start"
NO_DESKTOP=0
NO_BROWSER=0
PREFERRED_PORT=0
CUSTOM_DATA_DIR=""

print_help() {
  cat <<'EOF'
Cách dùng: bash run-all.sh [tùy chọn]

Tùy chọn:
  --stop            Dừng máy chủ backend và ứng dụng desktop đã khởi chạy
  --reset           Xóa dữ liệu demo (có hỏi xác nhận) để lần sau nhập mới
  --no-desktop      Không mở ứng dụng quản trị JavaFX
  --no-browser      Không tự mở trình duyệt web
  --port <số>       Chọn cổng ưu tiên bắt đầu kiểm tra (mặc định 3000)
  --data-dir <path> Dùng thư mục dữ liệu H2 riêng
  -h, --help        Hiển thị hướng dẫn này
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stop|-Stop|-stop)
      ACTION="stop"
      shift
      ;;
    --reset|-Reset|-reset)
      ACTION="reset"
      shift
      ;;
    --no-desktop|-NoDesktop)
      NO_DESKTOP=1
      shift
      ;;
    --no-browser|-NoBrowser)
      NO_BROWSER=1
      shift
      ;;
    --port|-Port)
      PREFERRED_PORT="${2:-0}"
      shift 2
      ;;
    --data-dir|-DataDir)
      CUSTOM_DATA_DIR="${2:-}"
      shift 2
      ;;
    -h|--help)
      print_help
      exit 0
      ;;
    *)
      echo "[LỖI] Tham số không hợp lệ: $1" >&2
      print_help
      exit 1
      ;;
  esac
done

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[LỖI] Thiếu công cụ '$1'. Vui lòng cài đặt '$1' rồi chạy lại." >&2
    exit 1
  fi
}

kill_tree() {
  local pid="$1"
  if [[ -z "$pid" ]] || ! [[ "$pid" =~ ^[0-9]+$ ]] || [[ "$pid" -le 0 ]]; then
    return 0
  fi
  if ! kill -0 "$pid" 2>/dev/null; then
    return 0
  fi

  # Dừng tiến trình con trước vì Maven sẽ gọi tiến trình Java con.
  if command -v pgrep >/dev/null 2>&1; then
    local child
    for child in $(pgrep -P "$pid" 2>/dev/null || true); do
      kill_tree "$child"
    done
  fi
  kill "$pid" 2>/dev/null || true
  sleep 1
  if kill -0 "$pid" 2>/dev/null; then
    kill -9 "$pid" 2>/dev/null || true
  fi
}

stop_dippie() {
  echo "Đang dừng các tiến trình Dippie..."
  if [[ ! -f "$PID_FILE" ]]; then
    echo "  Không tìm thấy tiến trình Dippie nào đang chạy từ trình khởi chạy này."
    return 0
  fi

  local BACKEND_PID=""
  local DESKTOP_PID=""
  source "$PID_FILE" || true

  local stopped=0
  if [[ -n "${DESKTOP_PID:-}" ]] && kill -0 "$DESKTOP_PID" 2>/dev/null; then
    kill_tree "$DESKTOP_PID"
    echo "  Đã dừng Dippie Admin Desktop (PID $DESKTOP_PID)."
    stopped=1
  fi
  if [[ -n "${BACKEND_PID:-}" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill_tree "$BACKEND_PID"
    echo "  Đã dừng Dippie Backend (PID $BACKEND_PID)."
    stopped=1
  fi

  rm -f "$PID_FILE"
  if [[ "$stopped" -eq 0 ]]; then
    echo "  Các tiến trình Dippie trước đó đã kết thúc."
  else
    echo "Hoàn tất dừng Dippie."
  fi
}

reset_dippie_data() {
  local target_dir="${CUSTOM_DATA_DIR:-$DEFAULT_DATA_DIR}"
  echo "Đặt lại dữ liệu demo Dippie"
  echo "  Thư mục dữ liệu sẽ bị xóa: $target_dir"

  if [[ ! -e "$target_dir" ]]; then
    echo "  Chưa có dữ liệu demo để xóa. Lần chạy tiếp theo sẽ tự tạo mới."
    return 0
  fi

  local answer=""
  read -r -p "Bạn có chắc chắn muốn xóa toàn bộ dữ liệu demo? (nhập Y hoặc C để đồng ý): " answer
  case "$answer" in
    y|Y|yes|YES|c|C|co|CO|có|CÓ)
      if [[ -f "$PID_FILE" ]]; then
        stop_dippie
        sleep 1
      fi
      rm -rf "$target_dir"
      echo "Đã xóa dữ liệu demo tại '$target_dir'."
      echo "Lần chạy 'bash run-all.sh' tiếp theo sẽ tự động tạo mới và nhập lại 6 bộ phim."
      ;;
    *)
      echo "Đã hủy thao tác đặt lại dữ liệu."
      ;;
  esac
}

if [[ "$ACTION" == "stop" ]]; then
  stop_dippie
  exit 0
fi

if [[ "$ACTION" == "reset" ]]; then
  reset_dippie_data
  exit 0
fi

require_cmd curl
require_cmd tar

inspect_java_candidate() {
  local java_bin="$1"
  if [[ -z "$java_bin" ]] || [[ ! -x "$java_bin" ]]; then
    return 1
  fi

  local props
  props="$("$java_bin" -XshowSettings:properties -version 2>&1 || true)"
  local java_home
  java_home="$(printf '%s\n' "$props" | sed -n 's/^[[:space:]]*java\.home = //p' | head -n 1 | tr -d '\r')"
  local java_ver
  java_ver="$(printf '%s\n' "$props" | sed -n 's/^[[:space:]]*java\.version = //p' | head -n 1 | tr -d '\r')"

  if [[ -z "$java_home" ]] || [[ -z "$java_ver" ]]; then
    return 1
  fi

  local major=0
  if [[ "$java_ver" =~ ^1\.([0-9]+) ]]; then
    major="${BASH_REMATCH[1]}"
  elif [[ "$java_ver" =~ ^([0-9]+) ]]; then
    major="${BASH_REMATCH[1]}"
  fi

  # Cần đủ JDK (có javac) để Maven biên dịch mã nguồn.
  if [[ "$major" -ge 21 ]] && [[ -x "$java_home/bin/javac" ]]; then
    printf '%s|%s\n' "$java_home" "$java_ver"
    return 0
  fi
  return 1
}

ensure_jdk21() {
  echo "[1/6] Kiểm tra Java (JDK 21 trở lên)..."
  local found=""

  if [[ -n "${JAVA_HOME:-}" ]]; then
    found="$(inspect_java_candidate "$JAVA_HOME/bin/java" || true)"
    if [[ -n "$found" ]]; then
      RESOLVED_JAVA_HOME="${found%%|*}"
      echo "  Dùng JDK sẵn có tại JAVA_HOME: $RESOLVED_JAVA_HOME (Java ${found#*|})"
      return 0
    fi
  fi

  if [[ "$(uname -s)" == "Darwin" ]] && [[ -x "/usr/libexec/java_home" ]]; then
    local mac_home
    mac_home="$(/usr/libexec/java_home -v 21+ 2>/dev/null || true)"
    if [[ -n "$mac_home" ]]; then
      found="$(inspect_java_candidate "$mac_home/bin/java" || true)"
      if [[ -n "$found" ]]; then
        RESOLVED_JAVA_HOME="${found%%|*}"
        echo "  Dùng JDK sẵn có trên macOS: $RESOLVED_JAVA_HOME (Java ${found#*|})"
        return 0
      fi
    fi
  fi

  if command -v java >/dev/null 2>&1; then
    found="$(inspect_java_candidate "$(command -v java)" || true)"
    if [[ -n "$found" ]]; then
      RESOLVED_JAVA_HOME="${found%%|*}"
      echo "  Dùng JDK sẵn có trên PATH: $RESOLVED_JAVA_HOME (Java ${found#*|})"
      return 0
    fi
  fi

  local local_jdk="$TOOLS_DIR/jdk"
  if [[ -d "$local_jdk" ]]; then
    found="$(inspect_java_candidate "$local_jdk/bin/java" || true)"
    if [[ -n "$found" ]]; then
      RESOLVED_JAVA_HOME="${found%%|*}"
      echo "  Dùng JDK 21 di động đã tải sẵn: $RESOLVED_JAVA_HOME (Java ${found#*|})"
      return 0
    fi
    rm -rf "$local_jdk"
  fi

  local os_raw arch_raw adoptium_os adoptium_arch
  os_raw="$(uname -s)"
  arch_raw="$(uname -m)"
  case "$os_raw" in
    Darwin) adoptium_os="mac" ;;
    Linux)  adoptium_os="linux" ;;
    *)
      echo "[LỖI] Hệ điều hành chưa được hỗ trợ tự động tải JDK: $os_raw" >&2
      exit 1
      ;;
  esac
  case "$arch_raw" in
    x86_64|amd64)  adoptium_arch="x64" ;;
    arm64|aarch64) adoptium_arch="aarch64" ;;
    *)
      echo "[LỖI] Kiến trúc CPU chưa được hỗ trợ tự động tải JDK: $arch_raw" >&2
      exit 1
      ;;
  esac

  local jdk_url="https://api.adoptium.net/v3/binary/latest/21/ga/${adoptium_os}/${adoptium_arch}/jdk/hotspot/normal/eclipse"
  local archive="$TMP_DIR/temurin-jdk21.tar.gz"
  local extract_dir="$TMP_DIR/jdk-extract"

  mkdir -p "$TMP_DIR"
  rm -rf "$archive" "$extract_dir"
  echo "  Chưa có JDK 21+. Đang tải Eclipse Temurin JDK 21 (${adoptium_os}/${adoptium_arch}) vào .tools/jdk..."
  if ! curl -fL --retry 3 --retry-delay 2 --connect-timeout 30 --progress-bar -o "$archive" "$jdk_url"; then
    rm -f "$archive"
    echo "[LỖI] Không thể tải JDK 21 từ Adoptium. Hãy kiểm tra kết nối Internet." >&2
    exit 1
  fi

  echo "  Đang giải nén JDK 21..."
  mkdir -p "$extract_dir"
  tar -xzf "$archive" -C "$extract_dir"

  # Trên macOS thư mục JDK nằm ở Contents/Home; tìm bin/javac sẽ khớp đúng cả macOS lẫn Linux.
  local javac_path
  javac_path="$(find "$extract_dir" -type f -path '*/bin/javac' | head -n 1 || true)"
  if [[ -z "$javac_path" ]]; then
    rm -rf "$archive" "$extract_dir"
    echo "[LỖI] Gói JDK tải về không chứa bin/javac." >&2
    exit 1
  fi

  local extracted_home
  extracted_home="$(dirname "$(dirname "$javac_path")")"
  rm -rf "$local_jdk"
  mv "$extracted_home" "$local_jdk"
  rm -rf "$archive" "$extract_dir"

  found="$(inspect_java_candidate "$local_jdk/bin/java" || true)"
  if [[ -z "$found" ]]; then
    echo "[LỖI] Đã giải nén JDK vào .tools/jdk nhưng không chạy kiểm tra được." >&2
    exit 1
  fi

  RESOLVED_JAVA_HOME="${found%%|*}"
  echo "  Đã cài JDK 21 di động thành công: $RESOLVED_JAVA_HOME (Java ${found#*|})"
}

inspect_node_candidate() {
  local node_bin="$1"
  if [[ -z "$node_bin" ]] || [[ ! -x "$node_bin" ]]; then
    return 1
  fi
  local ver
  ver="$("$node_bin" -v 2>/dev/null | tr -d '\r' || true)"
  if [[ "$ver" =~ ^v([0-9]+)\. ]] && [[ "${BASH_REMATCH[1]}" -ge 20 ]]; then
    local node_dir
    node_dir="$(dirname "$node_bin")"
    if [[ -x "$node_dir/npm" ]]; then
      printf '%s|%s\n' "$node_dir" "$ver"
      return 0
    fi
  fi
  return 1
}

ensure_frontend_dist() {
  echo "[2/6] Kiểm tra giao diện web (frontend/dist)..."
  local frontend_dir="$ROOT_DIR/frontend"
  local index_html="$frontend_dir/dist/index.html"

  if [[ -f "$index_html" ]]; then
    echo "  Giao diện đã được build sẵn, không cần cài Node.js."
    return 0
  fi

  echo "  Chưa có frontend/dist/index.html, cần build giao diện trước khi chạy..."
  local found=""
  if command -v node >/dev/null 2>&1; then
    found="$(inspect_node_candidate "$(command -v node)" || true)"
  fi
  if [[ -z "$found" ]] && [[ -x "$TOOLS_DIR/node/bin/node" ]]; then
    found="$(inspect_node_candidate "$TOOLS_DIR/node/bin/node" || true)"
  fi

  if [[ -z "$found" ]]; then
    local os_raw arch_raw node_os node_arch
    os_raw="$(uname -s)"
    arch_raw="$(uname -m)"
    case "$os_raw" in
      Darwin) node_os="darwin" ;;
      Linux)  node_os="linux" ;;
      *)
        echo "[LỖI] Không hỗ trợ tải Node.js tự động trên: $os_raw" >&2
        exit 1
        ;;
    esac
    case "$arch_raw" in
      x86_64|amd64)  node_arch="x64" ;;
      arm64|aarch64) node_arch="arm64" ;;
      *)
        echo "[LỖI] Không hỗ trợ tải Node.js tự động trên kiến trúc: $arch_raw" >&2
        exit 1
        ;;
    esac

    local node_ver="$NODE_FALLBACK_VERSION"
    if command -v python3 >/dev/null 2>&1; then
      local detected_ver
      detected_ver="$(curl -sf --max-time 15 "https://nodejs.org/dist/index.json" | python3 -c '
import json, sys
for item in json.load(sys.stdin):
    v = item.get("version", "")
    if item.get("lts") and v.startswith("v"):
        major = int(v[1:].split(".")[0])
        if major >= 22:
            print(v)
            break
' 2>/dev/null || true)"
      if [[ -n "$detected_ver" ]]; then
        node_ver="$detected_ver"
      fi
    fi

    local node_url="https://nodejs.org/dist/${node_ver}/node-${node_ver}-${node_os}-${node_arch}.tar.gz"
    local archive="$TMP_DIR/node-lts.tar.gz"
    local extract_dir="$TMP_DIR/node-extract"
    mkdir -p "$TMP_DIR"
    rm -rf "$archive" "$extract_dir"

    echo "  Chưa có Node.js 20+. Đang tải Node.js ${node_ver} (${node_os}-${node_arch}) vào .tools/node..."
    if ! curl -fL --retry 3 --retry-delay 2 --connect-timeout 30 --progress-bar -o "$archive" "$node_url"; then
      rm -f "$archive"
      echo "[LỖI] Không thể tải Node.js từ $node_url." >&2
      exit 1
    fi

    mkdir -p "$extract_dir"
    tar -xzf "$archive" -C "$extract_dir"
    local node_bin_found
    node_bin_found="$(find "$extract_dir" -type f -path '*/bin/node' | head -n 1 || true)"
    if [[ -z "$node_bin_found" ]]; then
      rm -rf "$archive" "$extract_dir"
      echo "[LỖI] Gói Node.js tải về không chứa bin/node." >&2
      exit 1
    fi
    rm -rf "$TOOLS_DIR/node"
    mv "$(dirname "$(dirname "$node_bin_found")")" "$TOOLS_DIR/node"
    rm -rf "$archive" "$extract_dir"

    found="$(inspect_node_candidate "$TOOLS_DIR/node/bin/node" || true)"
    if [[ -z "$found" ]]; then
      echo "[LỖI] Đã giải nén Node.js nhưng không chạy kiểm tra được." >&2
      exit 1
    fi
  fi

  local node_bin_dir="${found%%|*}"
  echo "  Dùng Node.js ${found#*|} tại $node_bin_dir"
  export PATH="$node_bin_dir:$PATH"

  (
    cd "$frontend_dir"
    echo "  Đang cài đặt thư viện giao diện (npm ci)..."
    npm ci
    echo "  Đang build giao diện (npm run build)..."
    npm run build
  )

  if [[ ! -f "$index_html" ]]; then
    echo "[LỖI] Build giao diện xong nhưng không thấy frontend/dist/index.html." >&2
    exit 1
  fi
  echo "  Build giao diện thành công."
}

is_port_free() {
  local p="$1"
  if command -v lsof >/dev/null 2>&1; then
    if lsof -nP -iTCP:"$p" -sTCP:LISTEN >/dev/null 2>&1; then
      return 1
    fi
  elif command -v ss >/dev/null 2>&1; then
    if ss -tln 2>/dev/null | grep -Eq "[:.]${p}[[:space:]]"; then
      return 1
    fi
  fi

  if (echo >/dev/tcp/127.0.0.1/"$p") >/dev/null 2>&1; then
    return 1
  fi
  return 0
}

select_free_port() {
  local start_port="$1"
  local p
  for ((p = start_port; p < start_port + 100; p++)); do
    if is_port_free "$p"; then
      printf '%s\n' "$p"
      return 0
    fi
  done
  echo "[LỖI] Không tìm được cổng trống trong khoảng $start_port..$((start_port + 99))." >&2
  exit 1
}

prepare_mvnw() {
  local src="$ROOT_DIR/backend/springboot/mvnw"
  local dest="$RUN_DIR/mvnw"
  mkdir -p "$RUN_DIR"
  if [[ ! -f "$src" ]]; then
    echo "[LỖI] Không tìm thấy Maven wrapper tại $src." >&2
    exit 1
  fi
  # Xóa ký tự xuống dòng CRLF của Windows phòng khi gói ZIP được nén trên Windows.
  tr -d '\r' < "$src" > "$dest"
  chmod +x "$dest"
  MVNW_CLEAN="$dest"
}

count_series_cards() {
  local json_text="$1"
  if command -v python3 >/dev/null 2>&1; then
    printf '%s' "$json_text" | python3 -c 'import json, sys; d = json.load(sys.stdin); print(len(d) if isinstance(d, list) else 0)' 2>/dev/null || echo 0
  elif command -v jq >/dev/null 2>&1; then
    printf '%s' "$json_text" | jq 'if type == "array" then length else 0 end' 2>/dev/null || echo 0
  else
    printf '%s' "$json_text" | grep -o '"seasonCount"' | wc -l | tr -d '[:space:]'
  fi
}

extract_json_field() {
  local json_text="$1"
  local expr="$2"
  if command -v python3 >/dev/null 2>&1; then
    printf '%s' "$json_text" | python3 -c "
import json, sys
d = json.load(sys.stdin)
cur = d
for part in '${expr}'.split('.'):
    if isinstance(cur, dict) and part in cur:
        cur = cur[part]
    else:
        cur = None
        break
if isinstance(cur, bool):
    print('true' if cur else 'false')
elif cur is None:
    print('')
else:
    print(cur)
" 2>/dev/null || true
  elif command -v jq >/dev/null 2>&1; then
    printf '%s' "$json_text" | jq -r ".${expr} // empty" 2>/dev/null || true
  else
    local key="${expr##*.}"
    printf '%s' "$json_text" | sed -nE "s/.*\"${key}\"[[:space:]]*:[[:space:]]*\"([^\"]*)\".*/\1/p; s/.*\"${key}\"[[:space:]]*:[[:space:]]*(true|false|[0-9]+).*/\1/p" | head -n 1
  fi
}

import_demo_catalogs() {
  local port="$1"
  local api_base="http://127.0.0.1:${port}/api"

  echo "[4/6] Kiểm tra dữ liệu danh mục phim mẫu..."
  local cards_json existing_count
  cards_json="$(curl -sf --max-time 15 "${api_base}/catalog/series/cards" || echo '[]')"
  existing_count="$(count_series_cards "$cards_json")"

  if [[ "$existing_count" -ge "${#CATALOG_NAMES[@]}" ]]; then
    echo "  Đã có sẵn ${existing_count} bộ phim trong cơ sở dữ liệu, bỏ qua bước nhập."
    return 0
  fi

  echo "  Hiện có ${existing_count}/${#CATALOG_NAMES[@]} bộ phim. Đang đăng nhập tài khoản admin và nhập 6 danh mục..."
  local login_resp login_code login_body
  login_resp="$(curl -sS --max-time 30 -w $'\n%{http_code}' \
    -X POST "${api_base}/auth/login" \
    -H "Content-Type: application/json; charset=utf-8" \
    -d '{"usernameOrEmail":"admin","password":"password123"}')"
  login_code="${login_resp##*$'\n'}"
  login_body="${login_resp%$'\n'*}"

  local token role
  token="$(extract_json_field "$login_body" "data.token")"
  role="$(extract_json_field "$login_body" "data.role")"
  if [[ "$login_code" != "200" ]] || [[ -z "$token" ]] || [[ "$role" != "ROLE_ADMIN" ]]; then
    echo "[LỖI] Đăng nhập tài khoản admin thất bại (HTTP ${login_code})." >&2
    exit 1
  fi

  local idx=0
  local name
  for name in "${CATALOG_NAMES[@]}"; do
    idx=$((idx + 1))
    local catalog_file="$ROOT_DIR/data/catalog-by-series/catalog.${name}.json"
    if [[ ! -f "$catalog_file" ]]; then
      echo "[LỖI] Thiếu tệp danh mục: $catalog_file" >&2
      exit 1
    fi

    printf "  [%d/%d] Đang nhập danh mục: %s..." "$idx" "${#CATALOG_NAMES[@]}" "$name"

    local val_resp val_code val_body valid_flag conflicts_num
    val_resp="$(curl -sS --max-time 60 -w $'\n%{http_code}' \
      -X POST "${api_base}/admin/catalog/validate" \
      -H "Authorization: Bearer ${token}" \
      -H "Content-Type: application/json; charset=utf-8" \
      --data-binary "@${catalog_file}")"
    val_code="${val_resp##*$'\n'}"
    val_body="${val_resp%$'\n'*}"
    valid_flag="$(extract_json_field "$val_body" "valid")"
    conflicts_num="$(extract_json_field "$val_body" "conflicts")"

    if [[ "$val_code" != "200" ]] || [[ "$valid_flag" != "true" ]] || [[ "${conflicts_num:-1}" != "0" ]]; then
      echo " thất bại!"
      echo "[LỖI] Kiểm tra hợp lệ danh mục '${name}' thất bại (HTTP ${val_code}, valid=${valid_flag}, conflicts=${conflicts_num})." >&2
      exit 1
    fi

    local imp_resp imp_code imp_body imp_ok
    imp_resp="$(curl -sS --max-time 60 -w $'\n%{http_code}' \
      -X POST "${api_base}/admin/catalog/import" \
      -H "Authorization: Bearer ${token}" \
      -H "Content-Type: application/json; charset=utf-8" \
      --data-binary "@${catalog_file}")"
    imp_code="${imp_resp##*$'\n'}"
    imp_body="${imp_resp%$'\n'*}"
    imp_ok="$(extract_json_field "$imp_body" "success")"

    if [[ "$imp_code" != "200" ]] || [[ "$imp_ok" != "true" ]]; then
      echo " thất bại!"
      echo "[LỖI] Nhập danh mục '${name}' thất bại (HTTP ${imp_code})." >&2
      exit 1
    fi
    echo " xong."
  done

  local after_json after_count
  after_json="$(curl -sf --max-time 15 "${api_base}/catalog/series/cards" || echo '[]')"
  after_count="$(count_series_cards "$after_json")"
  echo "  Đã hoàn tất nhập danh mục (${after_count} bộ phim sẵn sàng)."
}

echo ""
echo "================================================================"
echo "  TRÌNH KHỞI CHẠY DIPPIE (CHẾ ĐỘ DEMO)"
echo "================================================================"
echo ""

mkdir -p "$RUN_DIR"

# Bước 1: tìm hoặc tải JDK 21+ cho phiên chạy này.
RESOLVED_JAVA_HOME=""
ensure_jdk21
export JAVA_HOME="$RESOLVED_JAVA_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

# Bước 2: kiểm tra frontend/dist, thiếu thì mới build.
ensure_frontend_dist

# Bước 3: chọn cổng trống rồi chạy backend ở chế độ demo.
START_PORT="$PREFERRED_PORT"
if [[ "$START_PORT" -le 0 ]]; then
  START_PORT=3000
fi
SELECTED_PORT="$(select_free_port "$START_PORT")"
export PORT="$SELECTED_PORT"

if [[ "$SELECTED_PORT" != "$START_PORT" ]]; then
  echo "[3/6] Cổng ${START_PORT} đang bận, tự động chuyển sang cổng ${SELECTED_PORT}."
else
  echo "[3/6] Khởi động Dippie Backend trên cổng ${SELECTED_PORT}..."
fi

DATA_DIR="${CUSTOM_DATA_DIR:-$DEFAULT_DATA_DIR}"
mkdir -p "$DATA_DIR"
export SPRING_PROFILES_ACTIVE="demo"
export DIPPIE_DATA_DIR="$DATA_DIR"

MVNW_CLEAN=""
prepare_mvnw

: > "$BACKEND_LOG"
(
  cd "$ROOT_DIR/backend/springboot"
  exec "$MVNW_CLEAN" -q -Pdemo spring-boot:run "-Dspring-boot.run.profiles=demo"
) > "$BACKEND_LOG" 2>&1 < /dev/null &
BACKEND_PID=$!

cat > "$PID_FILE" <<EOF
PORT=${SELECTED_PORT}
BACKEND_PID=${BACKEND_PID}
DESKTOP_PID=
EOF

printf "  Đang chờ máy chủ sẵn sàng (lần đầu tải thư viện Maven có thể mất 2-6 phút, tối đa 10 phút)"
START_TS="$(date +%s)"
TIMEOUT_SEC=600
HEALTHY=0
DOTS=0

while true; do
  NOW_TS="$(date +%s)"
  ELAPSED=$((NOW_TS - START_TS))
  if [[ "$ELAPSED" -ge "$TIMEOUT_SEC" ]]; then
    break
  fi

  sleep 2
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo ""
    echo "  [LỖI] Tiến trình Dippie Backend đã dừng đột ngột." >&2
    if [[ -f "$BACKEND_LOG" ]]; then
      echo "  --- Các dòng nhật ký cuối cùng ($BACKEND_LOG) ---" >&2
      tail -n 40 "$BACKEND_LOG" >&2 || true
      echo "  -----------------------------------------------" >&2
    fi
    rm -f "$PID_FILE"
    exit 1
  fi

  if curl -sf --max-time 3 "http://127.0.0.1:${SELECTED_PORT}/api/health" >/dev/null 2>&1; then
    HEALTHY=1
    break
  fi

  DOTS=$((DOTS + 1))
  printf "."
  if (( DOTS % 15 == 0 )); then
    printf " (%ds)\n  " "$ELAPSED"
  fi
done

echo ""
if [[ "$HEALTHY" -ne 1 ]]; then
  echo "  [LỖI] Quá thời gian chờ 10 phút nhưng Dippie Backend vẫn chưa sẵn sàng." >&2
  if [[ -f "$BACKEND_LOG" ]]; then
    tail -n 40 "$BACKEND_LOG" >&2 || true
  fi
  stop_dippie
  exit 1
fi

READY_SEC=$(( $(date +%s) - START_TS ))
echo "  Dippie Backend đã sẵn sàng sau ${READY_SEC}s!"

# Bước 4: nạp thêm 6 bộ phim mẫu nếu DB chưa đủ.
import_demo_catalogs "$SELECTED_PORT"

# Bước 5: mở trang web trên trình duyệt mặc định.
WEB_URL="http://localhost:${SELECTED_PORT}/"
if [[ "$NO_BROWSER" -eq 0 ]]; then
  echo "[5/6] Đang mở trình duyệt tại ${WEB_URL}..."
  if [[ "$(uname -s)" == "Darwin" ]] && command -v open >/dev/null 2>&1; then
    open "$WEB_URL" >/dev/null 2>&1 || true
  elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$WEB_URL" >/dev/null 2>&1 &
  else
    echo "  Không tìm thấy lệnh mở trình duyệt tự động. Hãy mở thủ công: ${WEB_URL}"
  fi
else
  echo "[5/6] Bỏ qua mở trình duyệt (--no-browser)."
fi

# Bước 6: mở app quản trị JavaFX bằng Maven wrapper.
DESKTOP_STARTED=0
DESKTOP_PID=""
if [[ "$NO_DESKTOP" -eq 0 ]]; then
  echo "[6/6] Đang mở ứng dụng quản trị Dippie Admin (JavaFX)..."
  : > "$DESKTOP_LOG"
  (
    cd "$ROOT_DIR/desktop"
    exec "$MVNW_CLEAN" -q compile javafx:run
  ) > "$DESKTOP_LOG" 2>&1 < /dev/null &
  DESKTOP_PID=$!
  DESKTOP_STARTED=1
  cat > "$PID_FILE" <<EOF
PORT=${SELECTED_PORT}
BACKEND_PID=${BACKEND_PID}
DESKTOP_PID=${DESKTOP_PID}
EOF
  echo "  Đã khởi chạy Dippie Admin (lần đầu có thể mất thêm 30-60 giây tải thư viện JavaFX)."
else
  echo "[6/6] Bỏ qua ứng dụng quản trị desktop (--no-desktop)."
fi

echo ""
echo "================================================================"
echo "  DIPPIE ĐÃ SẴN SÀNG!"
echo "================================================================"
echo "  Trang web      : ${WEB_URL}"
echo "  Tài khoản mẫu  :"
echo "    - Người dùng : user  / password123"
echo "    - Quản trị   : admin / password123"
echo "  Nhật ký (log)  :"
echo "    - Backend    : ${BACKEND_LOG}"
if [[ "$DESKTOP_STARTED" -eq 1 ]]; then
  echo "    - Desktop    : ${DESKTOP_LOG}"
fi
echo "  Cách dừng      : bash run-all.sh --stop"
echo "  Đặt lại dữ liệu: bash run-all.sh --reset"
echo "================================================================"
