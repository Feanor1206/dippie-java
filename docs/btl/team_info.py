"""Đọc thong-tin-nhom.txt để báo cáo và slide dùng chung một nguồn tên, lớp, giảng viên."""
import os
from pathlib import Path

# Biến môi trường DIPPIE_TEAM_INFO cho phép dựng thử với một bản sao khác mà không đụng file thật.
INFO_FILE = Path(os.environ.get("DIPPIE_TEAM_INFO") or Path(__file__).resolve().parent / "thong-tin-nhom.txt")

DOTS_LONG = "................................................"
DOTS_SHORT = "..................."

# Cách gọi khi chưa điền tên gọi, theo phần việc của từng người.
FALLBACK_CALLS = {
    1: "nhóm trưởng",
    2: "bạn làm JavaFX",
    3: "bạn làm frontend",
    4: "bạn lo triển khai",
}

_FIELD_KEYS = {
    "trường": "truong",
    "khoa": "khoa",
    "học phần": "hoc_phan",
    "mã học phần": "ma_hoc_phan",
    "lớp": "lop",
    "giảng viên hướng dẫn": "giang_vien",
    "nơi làm báo cáo": "noi_lam",
    "họ và tên": "ho_ten",
    "tên gọi": "ten_goi",
    "mã sinh viên": "ma_sv",
}


def _load():
    info = {"members": {i: {} for i in range(1, 5)}}
    current = info
    if not INFO_FILE.exists():
        return info
    for raw in INFO_FILE.read_text(encoding="utf-8-sig").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("["):
            head = line.strip("[]").split(":")[0].strip().lower()
            number = head.replace("thành viên", "").strip()
            current = info["members"].get(int(number), {}) if number.isdigit() else info
            continue
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        field_name = _FIELD_KEYS.get(key.strip().lower())
        if field_name:
            current[field_name] = value.strip()
    return info


INFO = _load()


def field(name, dots=DOTS_LONG):
    """Giá trị đã điền, chưa điền thì trả dòng chấm."""
    return INFO.get(name) or dots


def labeled(label, key, dots=DOTS_LONG, also=()):
    """In "Nhãn: giá trị" nhưng bỏ nhãn khi giá trị đã tự mở đầu bằng nhãn, tránh "Trường: Trường Đại học..."."""
    value = INFO.get(key)
    if not value:
        return f"{label}: {dots}"
    starts = (label.lower(),) + tuple(a.lower() for a in also)
    return value if value.lower().startswith(starts) else f"{label}: {value}"


def member(number):
    return INFO["members"][number]


def full_name(number, dots=DOTS_SHORT):
    return member(number).get("ho_ten") or dots


def student_id(number, dots=DOTS_SHORT):
    return member(number).get("ma_sv") or dots


def label(number):
    """Tên đặt trong bảng và thẻ: họ tên đầy đủ, chưa điền thì "Thành viên N" cho đều nhau."""
    return member(number).get("ho_ten") or f"Thành viên {number}"


def nickname(number):
    """Tên gọi đã điền, chưa điền thì None để câu thoại tự chọn cách nói khác."""
    return member(number).get("ten_goi") or None


def call(number):
    """Cách gọi trong lời thoại: tên gọi nếu có, không thì gọi theo phần việc."""
    return nickname(number) or FALLBACK_CALLS[number]
