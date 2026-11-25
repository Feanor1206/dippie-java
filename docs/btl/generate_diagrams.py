# -*- coding: utf-8 -*-
"""Tạo các sơ đồ và biểu đồ minh họa cho báo cáo và slide Dippie."""

from pathlib import Path
import zipfile
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Ellipse, FancyArrowPatch
from matplotlib.lines import Line2D
import numpy as np

ASSETS_DIR = Path(__file__).resolve().parent / "assets"

# Bảng màu đồng bộ với giao diện Dippie (giấy trắng + xanh xô thơm + mực đậm)
PAPER = "#FFFDF6"
CARD_BG = "#FFFFFF"
MINT_SOFT = "#EEF4E9"
MINT = "#C9DDBD"
SAGE = "#8DAD86"
SAGE_DARK = "#36563C"
HONEY = "#F5DEAA"
HONEY_DARK = "#9A6700"
SLATE_SOFT = "#F3F5F4"
INK = "#18241E"
MUTED = "#526056"
BORDER = "#7E947B"

plt.rcParams["font.family"] = "sans-serif"
plt.rcParams["font.sans-serif"] = ["Segoe UI", "Arial", "DejaVu Sans"]
plt.rcParams["axes.unicode_minus"] = False


def _box(ax, x, y, w, h, title, subtitle="", fc=CARD_BG, ec=SAGE_DARK, lw=1.4,
         title_size=9.5, sub_size=7.8, title_color=INK, sub_color=MUTED, pad=0.005,
         title_y=0.70, sub_y=0.32):
    patch = FancyBboxPatch(
        (x, y), w, h,
        boxstyle=f"round,pad={pad},rounding_size=0.012",
        facecolor=fc, edgecolor=ec, linewidth=lw, zorder=3
    )
    ax.add_patch(patch)
    if subtitle:
        ax.text(x + w / 2, y + h * title_y, title, ha="center", va="center",
                fontsize=title_size, fontweight="bold", color=title_color, zorder=4)
        ax.text(x + w / 2, y + h * sub_y, subtitle, ha="center", va="center",
                fontsize=sub_size, color=sub_color, zorder=4, linespacing=1.28)
    else:
        ax.text(x + w / 2, y + h / 2, title, ha="center", va="center",
                fontsize=title_size, fontweight="bold", color=title_color, zorder=4)


def _arrow(ax, x1, y1, x2, y2, label="", color=SAGE_DARK, lw=1.4, rad=0.0,
           lx_off=0.0, ly_off=0.02, style="-|>", ls="-", label_size=7.5):
    arr = FancyArrowPatch(
        (x1, y1), (x2, y2),
        arrowstyle=style, mutation_scale=13,
        color=color, linewidth=lw, linestyle=ls,
        connectionstyle=f"arc3,rad={rad}", zorder=5
    )
    ax.add_patch(arr)
    if label:
        mx = (x1 + x2) / 2 + lx_off
        my = (y1 + y2) / 2 + ly_off
        ax.text(mx, my, label, ha="center", va="center", fontsize=label_size,
                color=INK, fontweight="semibold",
                bbox=dict(boxstyle="round,pad=0.2", fc=PAPER, ec="none", alpha=0.95), zorder=6)


def build_architecture_diagram():
    fig, ax = plt.subplots(figsize=(12.6, 7.0), dpi=300)
    fig.patch.set_facecolor(PAPER)
    ax.set_facecolor(PAPER)
    ax.set_xlim(0, 1)
    ax.set_ylim(0, 1)
    ax.axis("off")

    ax.text(0.5, 0.962, "KIẾN TRÚC TỔNG THỂ HỆ THỐNG DIPPIE", ha="center", va="center",
            fontsize=14, fontweight="bold", color=SAGE_DARK)
    ax.text(0.5, 0.922, "Kiến trúc hợp nhất Spring Boot 3.5 (Java 21) + React 19 SPA + Ứng dụng Quản trị JavaFX 21",
            ha="center", va="center", fontsize=9.2, color=MUTED)

    # Nhóm 1: Tầng người dùng (trái)
    ax.add_patch(FancyBboxPatch((0.018, 0.14), 0.21, 0.69,
                                boxstyle="round,pad=0.008,rounding_size=0.016",
                                fc=MINT_SOFT, ec=BORDER, lw=1.2, ls="--", zorder=1))
    ax.text(0.123, 0.806, "TẦNG ỨNG DỤNG KHÁCH", ha="center", va="center",
            fontsize=8.6, fontweight="bold", color=SAGE_DARK)

    _box(ax, 0.032, 0.505, 0.182, 0.255,
         "Trình duyệt Web (SPA)",
         "React 19 · TypeScript · Vite\nTailwind CSS · Responsive\nYouTube IFrame Player API",
         fc=CARD_BG, ec=SAGE_DARK, title_size=9.2, sub_size=7.6)

    _box(ax, 0.032, 0.175, 0.182, 0.27,
         "Dippie Admin (Desktop)",
         "JavaFX 21 · JDBC thuần\nComposite TreeView\nCommand Undo · Observer\nBCrypt · Xuất CSV I/O",
         fc=HONEY, ec=HONEY_DARK, title_size=9.2, sub_size=7.4, title_y=0.78, sub_y=0.38)

    # Reverse proxy Caddy
    _box(ax, 0.268, 0.555, 0.104, 0.165,
         "Caddy Proxy",
         "HTTPS / TLS\n(triển khai\nproduction)",
         fc=SLATE_SOFT, ec=MUTED, title_size=8.6, sub_size=7.3)

    # Nhóm 2: Backend Spring Boot hợp nhất (giữa)
    ax.add_patch(FancyBboxPatch((0.415, 0.14), 0.34, 0.69,
                                boxstyle="round,pad=0.008,rounding_size=0.016",
                                fc=MINT_SOFT, ec=SAGE_DARK, lw=1.6, zorder=1))
    ax.text(0.585, 0.806, "BACKEND HỢP NHẤT — SPRING BOOT 3.5 (JAVA 21 · :3000)",
            ha="center", va="center", fontsize=8.0, fontweight="bold", color=SAGE_DARK)

    _box(ax, 0.432, 0.655, 0.306, 0.115,
         "Spring Security + JWT (HS256) + SPA Static",
         "Xác thực phi trạng thái · Token Versioning · Rate Limit\nPhục vụ trực tiếp giao diện React (frontend/dist)",
         fc=MINT, ec=SAGE_DARK, title_size=8.4, sub_size=7.1, title_y=0.74, sub_y=0.32)

    _box(ax, 0.432, 0.495, 0.145, 0.135,
         "Core REST API",
         "Danh mục · Tìm kiếm\nThư viện · Đánh giá\nTiến độ · Quản trị",
         fc=CARD_BG, ec=SAGE_DARK, title_size=8.4, sub_size=7.1)

    _box(ax, 0.593, 0.495, 0.145, 0.135,
         "Gợi ý & Media",
         "Affinity / Mood\nVé phát JWT 5 phút\nHTTP Byte Range",
         fc=CARD_BG, ec=SAGE_DARK, title_size=8.4, sub_size=7.1)

    _box(ax, 0.432, 0.33, 0.306, 0.14,
         "Đường ống CineBot & Tìm kiếm Cục bộ",
         "IntentParser → LexicalIndex (BM25 trong RAM, 2.517+ tài liệu)\nAnswerGenerator Strategy (Template / DeepSeek) → SSE /api/chat",
         fc=CARD_BG, ec=SAGE_DARK, title_size=8.3, sub_size=7.0, title_y=0.73, sub_y=0.32)

    _box(ax, 0.432, 0.165, 0.306, 0.14,
         "Tầng Dữ liệu & Sự kiện Miền",
         "Spring Data JPA / Hibernate · CatalogReadModelCache (Observer)\nCatalogImportService (1 giao dịch + ReentrantLock + SHA-256)",
         fc=SLATE_SOFT, ec=BORDER, title_size=8.3, sub_size=7.0, title_y=0.73, sub_y=0.32)

    # Nhóm 3: Dữ liệu & Dịch vụ ngoài (phải)
    ax.add_patch(FancyBboxPatch((0.798, 0.14), 0.184, 0.69,
                                boxstyle="round,pad=0.008,rounding_size=0.016",
                                fc=MINT_SOFT, ec=BORDER, lw=1.2, ls="--", zorder=1))
    ax.text(0.89, 0.806, "DỮ LIỆU & DỊCH VỤ NGOÀI", ha="center", va="center",
            fontsize=8.2, fontweight="bold", color=SAGE_DARK)

    _box(ax, 0.812, 0.59, 0.156, 0.17,
         "YouTube & Media",
         "YouTube IFrame API\nTệp cục bộ / Drive\n(6 bộ · 806 tập)",
         fc=CARD_BG, ec=SAGE_DARK, title_size=8.4, sub_size=7.2)

    _box(ax, 0.812, 0.385, 0.156, 0.17,
         "DeepSeek API",
         "Tùy chọn LLM (SSE)\nBám ≤ 3 bằng chứng\nDự phòng mẫu cục bộ",
         fc=SLATE_SOFT, ec=MUTED, title_size=8.4, sub_size=7.2)

    _box(ax, 0.812, 0.165, 0.156, 0.185,
         "Cơ sở dữ liệu",
         "H2 (demo · AUTO_SERVER)\nMySQL 8.0 / 8.4 (prod)\n32 bảng · 4 view · 4 trigger",
         fc=MINT, ec=SAGE_DARK, title_size=8.4, sub_size=7.0)

    # Mũi tên kết nối
    _arrow(ax, 0.220, 0.635, 0.262, 0.635, "HTTPS", ly_off=0.036)
    _arrow(ax, 0.378, 0.635, 0.426, 0.695, "REST / SSE", lx_off=-0.012, ly_off=0.045)
    # Đường cong nhúng YouTube đi phía trên hẳn hộp Backend
    _arrow(ax, 0.123, 0.836, 0.89, 0.836, "Nhúng YouTube trực tiếp trên trình duyệt (IFrame)",
           rad=-0.08, ly_off=0.055, ls="--", color=MUTED, label_size=7.8)
    _arrow(ax, 0.744, 0.41, 0.806, 0.46, "HTTPS\n(tùy chọn)", lx_off=0.002, ly_off=0.048, ls="--", color=MUTED, label_size=7.0)
    _arrow(ax, 0.744, 0.565, 0.806, 0.655, "Đọc tệp", lx_off=-0.002, ly_off=0.038, label_size=7.2)
    _arrow(ax, 0.744, 0.235, 0.806, 0.245, "JPA /\nJDBC", lx_off=0.002, ly_off=0.040, label_size=7.2)
    _arrow(ax, 0.219, 0.185, 0.808, 0.185, "Kết nối JDBC trực tiếp (PreparedStatement & Transaction)",
           rad=0.10, ly_off=-0.075, color=HONEY_DARK, lw=1.6, label_size=7.8)

    # Chú thích bằng Line2D (không dùng ký tự Unicode dễ thiếu glyph)
    legend_handles = [
        Line2D([0], [0], color=SAGE_DARK, lw=1.8, linestyle="-", label="Luồng chính luôn hoạt động"),
        Line2D([0], [0], color=HONEY_DARK, lw=1.8, linestyle="-", label="Kết nối JDBC trực tiếp từ Dippie Admin"),
        Line2D([0], [0], color=MUTED, lw=1.6, linestyle="--", label="Luồng tùy chọn hoặc phát trực tiếp từ trình duyệt"),
    ]
    ax.legend(handles=legend_handles, loc="lower center", bbox_to_anchor=(0.5, 0.01),
              ncol=3, frameon=True, facecolor=PAPER, edgecolor=BORDER, fontsize=8.0)

    out = ASSETS_DIR / "arch_diagram.png"
    fig.savefig(out, dpi=300, bbox_inches="tight", facecolor=PAPER)
    plt.close(fig)
    return out


def build_usecase_diagram():
    fig, ax = plt.subplots(figsize=(11.6, 6.8), dpi=300)
    fig.patch.set_facecolor(PAPER)
    ax.set_facecolor(PAPER)
    ax.set_xlim(0, 1)
    ax.set_ylim(0, 1)
    ax.axis("off")

    ax.text(0.5, 0.96, "SƠ ĐỒ USE CASE TỔNG QUAN HỆ THỐNG DIPPIE", ha="center", va="center",
            fontsize=13.5, fontweight="bold", color=SAGE_DARK)

    ax.add_patch(FancyBboxPatch((0.23, 0.05), 0.54, 0.86,
                                boxstyle="round,pad=0.008,rounding_size=0.02",
                                fc="#FFFFFF", ec=SAGE_DARK, lw=1.6, zorder=1))
    ax.text(0.5, 0.885, "Hệ thống Dippie (Web SPA + Backend Spring Boot + Dippie Admin JavaFX)",
            ha="center", va="center", fontsize=8.8, fontweight="bold", color=SAGE_DARK)

    _box(ax, 0.02, 0.65, 0.17, 0.135, "Khách (Guest)", "Chưa đăng nhập\nTrình duyệt web",
         fc=MINT_SOFT, ec=SAGE_DARK, title_size=9.0, sub_size=7.5)
    _box(ax, 0.02, 0.30, 0.17, 0.155, "Người dùng\n(ROLE_USER)", "Phụ huynh / gia đình\nĐã đăng nhập JWT",
         fc=MINT, ec=SAGE_DARK, title_size=9.0, sub_size=7.5)

    _arrow(ax, 0.105, 0.462, 0.105, 0.642, "kế thừa", lx_off=-0.048, ly_off=0.0, style="-|>", color=MUTED)

    _box(ax, 0.81, 0.58, 0.17, 0.145, "Quản trị viên Web\n(ROLE_ADMIN)", "Giao diện quản trị\ntrên trình duyệt",
         fc=HONEY, ec=HONEY_DARK, title_size=8.8, sub_size=7.4)
    _box(ax, 0.81, 0.22, 0.17, 0.165, "Quản trị viên\nDesktop (JavaFX)", "Ứng dụng Dippie Admin\nkết nối JDBC",
         fc=HONEY, ec=HONEY_DARK, title_size=8.8, sub_size=7.4)

    ucs = [
        ("uc1", 0.38, 0.80, 0.255, 0.075, "UC01. Duyệt bộ phim,\ntìm kiếm & lọc thể loại", MINT_SOFT),
        ("uc2", 0.38, 0.695, 0.255, 0.075, "UC02. Xem tập phim\n(YouTube / Byte Range)", MINT_SOFT),
        ("uc3", 0.38, 0.59, 0.255, 0.075, "UC03. Hỏi đáp CineBot\n& mở đúng mốc cảnh", MINT_SOFT),
        ("uc4", 0.38, 0.485, 0.255, 0.075, "UC04. Đăng ký / Đăng nhập\n& đồng bộ dữ liệu khách", CARD_BG),
        ("uc5", 0.38, 0.38, 0.255, 0.075, "UC05. Lưu Xem sau,\nYêu thích & Chấm điểm 1–10", CARD_BG),
        ("uc6", 0.38, 0.275, 0.255, 0.075, "UC06. Lưu tiến độ xem &\nXem tiếp / Tự chuyển tập", CARD_BG),
        ("uc7", 0.38, 0.165, 0.255, 0.075, "UC07. Nhận gợi ý theo\nsở thích & tâm trạng", CARD_BG),
        ("uc8", 0.645, 0.74, 0.225, 0.08, "UC08. Kiểm tra, nhập/xuất\ngói danh mục & snapshot", "#FFF8E7"),
        ("uc9", 0.645, 0.60, 0.225, 0.08, "UC09. Quản lý tài khoản\n(vai trò, khóa/mở khóa)", "#FFF8E7"),
        ("uc10", 0.645, 0.46, 0.225, 0.08, "UC10. Xem thống kê vận hành\n& nhật ký kiểm toán", "#FFF8E7"),
        ("uc11", 0.645, 0.31, 0.225, 0.08, "UC11. Duyệt cây danh mục\n& kiểm duyệt đánh giá", "#FFF8E7"),
        ("uc12", 0.645, 0.165, 0.225, 0.08, "UC12. Xuất báo cáo CSV &\nHoàn tác thao tác (Undo)", "#FFF8E7"),
    ]
    uc_pos = {}
    for uid, cx, cy, w, h, label, fc in ucs:
        el = Ellipse((cx, cy), w, h, facecolor=fc, edgecolor=SAGE_DARK, linewidth=1.2, zorder=3)
        ax.add_patch(el)
        ax.text(cx, cy, label, ha="center", va="center", fontsize=7.6, color=INK, fontweight="medium", zorder=4)
        uc_pos[uid] = (cx, cy, w, h)

    def link(x1, y1, uid, side="left", color=SAGE_DARK):
        cx, cy, w, h = uc_pos[uid]
        tx = cx - w / 2 if side == "left" else cx + w / 2
        ax.plot([x1, tx], [y1, cy], color=color, linewidth=1.1, zorder=2)

    for u in ("uc1", "uc2", "uc3", "uc4", "uc7"):
        link(0.195, 0.715, u, "left", SAGE_DARK)
    for u in ("uc5", "uc6", "uc7"):
        link(0.195, 0.375, u, "left", SAGE_DARK)
    for u in ("uc8", "uc9", "uc10"):
        link(0.805, 0.65, u, "right", HONEY_DARK)
    for u in ("uc9", "uc10", "uc11", "uc12"):
        link(0.805, 0.30, u, "right", HONEY_DARK)

    out = ASSETS_DIR / "usecase_diagram.png"
    fig.savefig(out, dpi=300, bbox_inches="tight", facecolor=PAPER)
    plt.close(fig)
    return out


def build_ux_audit_chart():
    journeys = ["Khám phá\n& Tìm kiếm", "Xem phim\n& Tiến độ", "Đánh giá\n(1–10)", "Thư viện\ncá nhân", "Gợi ý\nphim", "Trợ lý\nCineBot"]
    r1 = [4, 3, 2, 2, 3, 5]
    r2 = [7, 7, 6, 8, 6, 7]
    r3 = [8, 7, 9, 9, 8, 8]

    x = np.arange(len(journeys))
    w = 0.25

    fig, ax = plt.subplots(figsize=(10.5, 5.4), dpi=300)
    fig.patch.set_facecolor(PAPER)
    ax.set_facecolor("#FFFFFF")

    b1 = ax.bar(x - w, r1, width=w, label="Vòng 1 (TB: 3,2/10)", color="#D7DFD4", edgecolor=MUTED, linewidth=0.9)
    b2 = ax.bar(x, r2, width=w, label="Vòng 2 (TB: 6,8/10)", color=SAGE, edgecolor=SAGE_DARK, linewidth=0.9)
    b3 = ax.bar(x + w, r3, width=w, label="Vòng 3 (TB: 8,2/10)", color=SAGE_DARK, edgecolor=INK, linewidth=0.9)

    for bars, txt_col in [(b1, MUTED), (b2, INK), (b3, SAGE_DARK)]:
        for bar in bars:
            h = bar.get_height()
            ax.text(bar.get_x() + bar.get_width() / 2, h + 0.18, f"{int(h)}",
                    ha="center", va="bottom", fontsize=9.2, fontweight="bold", color=txt_col)

    ax.axhline(3.2, color="#9AA89B", linestyle=":", linewidth=1.2)
    ax.axhline(6.8, color=SAGE, linestyle="--", linewidth=1.2)
    ax.axhline(8.2, color=HONEY_DARK, linestyle="-.", linewidth=1.5, label="Trung bình Vòng 3 (8,2/10)")

    ax.set_ylim(0, 10.4)
    ax.set_yticks(range(0, 11, 2))
    ax.set_xticks(x)
    ax.set_xticklabels(journeys, fontsize=9.5, fontweight="semibold", color=INK)
    ax.set_ylabel("Điểm đánh giá (thang 10)", fontsize=10, fontweight="bold", color=INK)
    ax.set_title("Tiến bộ điểm trải nghiệm người dùng (UX) qua 3 vòng kiểm thử",
                 fontsize=12.8, fontweight="bold", color=SAGE_DARK, pad=36)
    ax.grid(axis="y", linestyle="-", alpha=0.25, color=BORDER)
    ax.set_axisbelow(True)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.spines["left"].set_color(BORDER)
    ax.spines["bottom"].set_color(BORDER)
    ax.legend(loc="lower center", bbox_to_anchor=(0.5, 1.01), frameon=True,
              facecolor=PAPER, edgecolor=BORDER, fontsize=8.8, ncol=4)

    out = ASSETS_DIR / "ux_audit_chart.png"
    fig.savefig(out, dpi=300, bbox_inches="tight", facecolor=PAPER)
    plt.close(fig)
    return out


def build_cinebot_flow():
    fig, ax = plt.subplots(figsize=(11.8, 4.6), dpi=300)
    fig.patch.set_facecolor(PAPER)
    ax.set_facecolor(PAPER)
    ax.set_xlim(0, 1)
    ax.set_ylim(0, 1)
    ax.axis("off")

    ax.text(0.5, 0.925, "LUỒNG XỬ LÝ CÂU HỎI CINEBOT (BM25 CỤC BỘ + SSE + LLM CÓ CĂN CỨ)",
            ha="center", va="center", fontsize=12, fontweight="bold", color=SAGE_DARK)

    _box(ax, 0.015, 0.45, 0.16, 0.35,
         "1. Câu hỏi & Lịch sử",
         "POST /api/chat\nChatRequestValidator\n(giới hạn 256 KB,\n4.000 ký tự, Guard)",
         fc=MINT_SOFT, ec=SAGE_DARK, title_size=8.5, sub_size=7.2)

    _box(ax, 0.215, 0.45, 0.165, 0.35,
         "2. Phân tích ý định",
         "IntentParser\nNhận diện bộ/mùa/tập,\nthời lượng, tâm trạng,\nvá hội thoại nhiều lượt",
         fc=CARD_BG, ec=SAGE_DARK, title_size=8.5, sub_size=7.2)

    _box(ax, 0.42, 0.45, 0.175, 0.35,
         "3. Truy xuất BM25",
         "LocalCatalogSearch\nLexicalIndex (RAM)\n2.517+ tài liệu · Bigram\nKhông dấu · Sửa lỗi gõ",
         fc=MINT, ec=SAGE_DARK, title_size=8.5, sub_size=7.2)

    _box(ax, 0.635, 0.45, 0.165, 0.35,
         "4. Sinh câu trả lời",
         "AnswerGenerator\n(Strategy Pattern)\nTemplate mặc định hoặc\nDeepSeek (≤ 3 bằng chứng)",
         fc=HONEY, ec=HONEY_DARK, title_size=8.5, sub_size=7.2)

    _box(ax, 0.84, 0.45, 0.145, 0.35,
         "5. Luồng SSE",
         "LocalChatController\nmetadata → token\n→ done (thẻ phim\n+ mốc ?t=ms)",
         fc=MINT_SOFT, ec=SAGE_DARK, title_size=8.5, sub_size=7.2)

    _arrow(ax, 0.181, 0.625, 0.209, 0.625)
    _arrow(ax, 0.386, 0.625, 0.414, 0.625)
    _arrow(ax, 0.601, 0.625, 0.629, 0.625)
    _arrow(ax, 0.806, 0.625, 0.834, 0.625)

    _box(ax, 0.42, 0.07, 0.175, 0.24,
         "Dữ liệu danh mục sẵn sàng",
         "6 bộ · 806 tập · 1.705 đoạn\nTự cập nhật khi nhập gói mới",
         fc=SLATE_SOFT, ec=BORDER, title_size=8.0, sub_size=7.1)
    _arrow(ax, 0.5075, 0.316, 0.5075, 0.444, "CatalogImportedEvent", lx_off=0.0, ly_off=0.0, color=MUTED, ls="--")

    _box(ax, 0.635, 0.07, 0.35, 0.24,
         "Kiểm soát độ tin cậy (Grounded RAG & Fallback)",
         "Từ chối câu ngoài danh mục · Kiểm tra DeepSeek bám bằng chứng\nTự động dự phòng về TemplateAnswerGenerator khi mạng/API lỗi",
         fc=SLATE_SOFT, ec=HONEY_DARK, title_size=7.9, sub_size=6.7)
    _arrow(ax, 0.7175, 0.316, 0.7175, 0.444, "", color=HONEY_DARK, ls="--")

    out = ASSETS_DIR / "cinebot_flow.png"
    fig.savefig(out, dpi=300, bbox_inches="tight", facecolor=PAPER)
    plt.close(fig)
    return out


def build_db_overview():
    fig, ax = plt.subplots(figsize=(11.8, 6.4), dpi=300)
    fig.patch.set_facecolor(PAPER)
    ax.set_facecolor(PAPER)
    ax.set_xlim(0, 1)
    ax.set_ylim(0, 1)
    ax.axis("off")

    ax.text(0.5, 0.955, "KIẾN TRÚC LƯỢC ĐỒ CƠ SỞ DỮ LIỆU DIPPIE (32 BẢNG · 5 PHÂN NHÓM)",
            ha="center", va="center", fontsize=12.8, fontweight="bold", color=SAGE_DARK)
    ax.text(0.5, 0.912, "Chuẩn hóa BCNF trên cột quan hệ · H2 (demo) và MySQL 8.0/8.4 (5 migration Flyway, 4 view, 2 hàm, 3 thủ tục, 4 trigger)",
            ha="center", va="center", fontsize=8.4, color=MUTED)

    _box(ax, 0.025, 0.555, 0.275, 0.295,
         "1. Tài khoản, Kiểm toán & Hỗ trợ (5 bảng)",
         "• users (id, username, email, role,\n  enabled, token_version, terms)\n• admin_audit_events · audit_log\n• playback_reports · password_reset_tokens",
         fc=HONEY, ec=HONEY_DARK, title_size=8.5, sub_size=7.2, title_y=0.79, sub_y=0.36)

    _box(ax, 0.025, 0.13, 0.275, 0.315,
         "2. Tương tác & Thư viện (4 bảng)",
         "• watch_progress (user_id, episode_id,\n  asset_id, position_ms, opt_lock_version)\n• watchlist_items (xem sau)\n• favorite_items (yêu thích)\n• movie_ratings (điểm nguyên 1–10)",
         fc=MINT, ec=SAGE_DARK, title_size=9.0, sub_size=7.6, title_y=0.80, sub_y=0.38)

    _box(ax, 0.365, 0.53, 0.29, 0.32,
         "3. Danh mục Phân cấp (6 bảng)",
         "• catalog_series (6 bộ phim)\n• catalog_seasons (20 mùa, FK series)\n• catalog_episodes (806 tập, FK ghép)\n• catalog_segments (1.705 phân đoạn)\n• catalog_scenes · catalog_text_tracks",
         fc=MINT_SOFT, ec=SAGE_DARK, title_size=9.0, sub_size=7.6, title_y=0.80, sub_y=0.38)

    _box(ax, 0.365, 0.13, 0.29, 0.28,
         "4. Lớp Phim & Thể loại (4 bảng)",
         "• movies (chiếu tương thích 812 mục)\n• media_assets (nguồn tệp video)\n• movie_genre_terms (nhãn thể loại)\n• movie_genres (N–N phim ↔ thể loại)",
         fc=CARD_BG, ec=SAGE_DARK, title_size=9.0, sub_size=7.6, title_y=0.78, sub_y=0.38)

    _box(ax, 0.72, 0.13, 0.255, 0.72,
         "5. Tài nguyên, Từ điển &\nNhập danh mục (13 bảng)",
         "• catalog_media_assets\n• catalog_episode_assets (N–N)\n• catalog_genres, catalog_characters\n• catalog_sources,\n  catalog_attribute_scales\n• 5 bảng nối N–N:\n  series/episode ↔ genre/character,\n  character ↔ series\n• catalog_packages (SHA-256)\n• catalog_snapshots (trạng thái chỉ mục)",
         fc=SLATE_SOFT, ec=BORDER, title_size=8.8, sub_size=7.5, title_y=0.84, sub_y=0.40)

    _arrow(ax, 0.162, 0.548, 0.162, 0.452, "1 : N (user_id)", lx_off=0.062, ly_off=0.0)
    _arrow(ax, 0.306, 0.26, 0.359, 0.26, "FK movie_id", ly_off=0.038)
    _arrow(ax, 0.306, 0.41, 0.359, 0.56, "FK episode_id", lx_off=-0.002, ly_off=0.048)
    _arrow(ax, 0.661, 0.67, 0.714, 0.67, "N : N", ly_off=0.036)
    _arrow(ax, 0.51, 0.523, 0.51, 0.417, "đồng bộ khi nhập", lx_off=0.075, ly_off=0.0, ls="--", color=MUTED)
    _arrow(ax, 0.714, 0.32, 0.661, 0.32, "tài nguyên", ly_off=0.036, ls="--", color=MUTED)

    ax.text(0.5, 0.05, "Toàn bộ khóa ngoại bảo toàn toàn vẹn tham chiếu; gói nhập mới ghi đồng thời phân nhóm 3, 4 và 5 trong 1 giao dịch nguyên tử.",
            ha="center", va="center", fontsize=8.3, color=MUTED)

    out = ASSETS_DIR / "db_overview.png"
    fig.savefig(out, dpi=300, bbox_inches="tight", facecolor=PAPER)
    plt.close(fig)
    return out


def extract_existing_screenshots():
    """Trích xuất 4 ảnh giao diện phù hợp từ báo cáo cũ nếu tệp tồn tại."""
    old_docx = Path(r"C:\Users\Admin\Downloads\Bao_cao_BTL_Dippie_Den_trang (1).docx")
    mapping = {
        "word/media/image14.png": "screenshot_larva_player.png",
        "word/media/image15.png": "screenshot_ori_external.png",
        "word/media/image16.png": "screenshot_cinebot.png",
        "word/media/image17.png": "screenshot_login.png",
    }
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    extracted = []
    if not old_docx.exists():
        return extracted
    with zipfile.ZipFile(old_docx) as zf:
        for src, dst_name in mapping.items():
            if src in zf.namelist():
                dst = ASSETS_DIR / dst_name
                dst.write_bytes(zf.read(src))
                extracted.append(dst)
                print(f"Extracted: {dst} ({dst.stat().st_size:,} bytes)")
    return extracted


def generate_all():
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    paths = [
        build_architecture_diagram(),
        build_usecase_diagram(),
        build_ux_audit_chart(),
        build_cinebot_flow(),
        build_db_overview(),
    ]
    for p in paths:
        print(f"Generated: {p} ({p.stat().st_size:,} bytes)")
    paths.extend(extract_existing_screenshots())
    return paths


if __name__ == "__main__":
    generate_all()
