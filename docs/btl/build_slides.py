# -*- coding: utf-8 -*-
"""Tạo bộ slide thuyết trình 5 phút Dippie (Slide_BTL_Dippie.pptx) bằng python-pptx."""

from pathlib import Path
from PIL import Image
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE

import team_info as team

BASE_DIR = Path(__file__).resolve().parent
ASSETS_DIR = BASE_DIR / "assets"
SCREENSHOTS_DIR = ASSETS_DIR / "screenshots"
DESKTOP_SHOTS_DIR = SCREENSHOTS_DIR / "desktop"
OUT_PPTX = BASE_DIR / "Slide_BTL_Dippie.pptx"

FONT_NAME = "Segoe UI"
CODE_FONT = "Consolas"

PAPER = RGBColor(0xFF, 0xFD, 0xF6)        # Nền giấy trắng ấm
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
MINT_SOFT = RGBColor(0xEE, 0xF4, 0xE9)    # Nền thẻ nhạt
MINT = RGBColor(0xD5, 0xE5, 0xCB)         # Nền nhấn dịu
CODE_BG = RGBColor(0xF4, 0xF7, 0xF2)      # Nền khung mã nguồn
SAGE = RGBColor(0x8D, 0xAD, 0x86)         # Xanh xô thơm
SAGE_DARK = RGBColor(0x28, 0x46, 0x30)    # Xanh xô thơm đậm
HONEY_SOFT = RGBColor(0xFB, 0xF2, 0xDC)   # Vàng kem dịu
HONEY_DARK = RGBColor(0x8C, 0x5A, 0x00)   # Nâu vàng nhấn
BORDER = RGBColor(0xB7, 0xC6, 0xB3)
INK = RGBColor(0x18, 0x24, 0x1E)          # Chữ đậm
MUTED = RGBColor(0x4E, 0x5B, 0x52)        # Chữ phụ

SLIDE_W = Inches(13.333)
SLIDE_H = Inches(7.5)
TOTAL_SLIDES = 14


def set_bg(slide, color=PAPER):
    bg = slide.background
    fill = bg.fill
    fill.solid()
    fill.fore_color.rgb = color


def add_rect(slide, left, top, width, height, fill_color=WHITE, border_color=None, border_pt=1.0, rounded=False):
    shape_type = MSO_SHAPE.ROUNDED_RECTANGLE if rounded else MSO_SHAPE.RECTANGLE
    sh = slide.shapes.add_shape(shape_type, left, top, width, height)
    if rounded:
        sh.adjustments[0] = 0.045
    sh.fill.solid()
    sh.fill.fore_color.rgb = fill_color
    if border_color is not None:
        sh.line.color.rgb = border_color
        sh.line.width = Pt(border_pt)
    else:
        sh.line.fill.background()
    sh.shadow.inherit = False
    return sh


def add_textbox(slide, left, top, width, height, text="", size=18, bold=False, italic=False,
                color=INK, align=PP_ALIGN.LEFT, valign=MSO_ANCHOR.TOP, line_spacing=1.18,
                font_name=FONT_NAME, margin_x=0.05, margin_y=0.03):
    tb = slide.shapes.add_textbox(left, top, width, height)
    tf = tb.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = valign
    tf.margin_left = Inches(margin_x)
    tf.margin_right = Inches(margin_x)
    tf.margin_top = Inches(margin_y)
    tf.margin_bottom = Inches(margin_y)
    if text:
        lines = text.split("\n")
        for idx, line in enumerate(lines):
            p = tf.paragraphs[0] if idx == 0 else tf.add_paragraph()
            p.alignment = align
            p.line_spacing = line_spacing
            r = p.add_run()
            r.text = line
            r.font.name = font_name
            r.font.size = Pt(size)
            r.font.bold = bold
            r.font.italic = italic
            r.font.color.rgb = color
    else:
        p = tf.paragraphs[0]
        p.alignment = align
        p.line_spacing = line_spacing
    return tb


def add_bullets(slide, left, top, width, height, items, size=15.0, color=INK,
                space_after=9, line_spacing=1.18, bullet_char="•  "):
    """Thêm tối đa 5 gạch đầu dòng ngắn gọn, không in đậm trang trí."""
    assert len(items) <= 5, f"Quá 5 gạch đầu dòng trên một khối: {len(items)}"
    tb = slide.shapes.add_textbox(left, top, width, height)
    tf = tb.text_frame
    tf.word_wrap = True
    tf.margin_left = Inches(0.05)
    tf.margin_right = Inches(0.05)
    tf.margin_top = Inches(0.03)
    tf.margin_bottom = Inches(0.03)

    for idx, item in enumerate(items):
        p = tf.paragraphs[0] if idx == 0 else tf.add_paragraph()
        p.alignment = PP_ALIGN.LEFT
        p.line_spacing = line_spacing
        p.space_after = Pt(space_after if idx < len(items) - 1 else 0)

        if bullet_char:
            rb = p.add_run()
            rb.text = bullet_char
            rb.font.name = FONT_NAME
            rb.font.size = Pt(size)
            rb.font.color.rgb = SAGE_DARK

        r = p.add_run()
        r.text = item
        r.font.name = FONT_NAME
        r.font.size = Pt(size)
        r.font.color.rgb = color
    return tb


def add_picture_fit(slide, img_path, left, top, max_w, max_h, frame_border=None):
    """Chèn ảnh giữ đúng tỷ lệ khung hình, căn giữa trong hộp (left, top, max_w, max_h)."""
    with Image.open(img_path) as im:
        iw, ih = im.size
    ratio = iw / ih
    box_ratio = max_w / max_h
    if ratio > box_ratio:
        w = max_w
        h = max_w / ratio
    else:
        h = max_h
        w = max_h * ratio
    x = left + (max_w - w) / 2
    y = top + (max_h - h) / 2
    if frame_border is not None:
        pad = Inches(0.025)
        add_rect(slide, x - pad, y - pad, w + pad * 2, h + pad * 2,
                 fill_color=WHITE, border_color=frame_border, border_pt=1.0, rounded=False)
    return slide.shapes.add_picture(str(img_path), x, y, width=w, height=h)


def add_screenshot_card(slide, left, top, width, height, img_path, caption,
                        bg=WHITE, border=BORDER, caption_size=11.0):
    """Hiển thị ảnh chụp màn hình thật trong khung bo góc kèm chú thích phía dưới."""
    add_rect(slide, left, top, width, height, fill_color=bg, border_color=border, border_pt=1.1, rounded=True)
    pad_x = Inches(0.10)
    pad_top = Inches(0.08)
    cap_h = Inches(0.36)
    img_h = height - pad_top - cap_h - Inches(0.05)
    add_picture_fit(slide, img_path, left + pad_x, top + pad_top, width - pad_x * 2, img_h, frame_border=BORDER)
    add_textbox(slide, left + Inches(0.08), top + height - cap_h - Inches(0.03),
                width - Inches(0.16), cap_h, caption,
                size=caption_size, bold=True, color=SAGE_DARK,
                align=PP_ALIGN.CENTER, valign=MSO_ANCHOR.MIDDLE, line_spacing=1.1)


def set_notes(slide, notes_text):
    notes_slide = slide.notes_slide
    tf = notes_slide.notes_text_frame
    tf.text = notes_text.strip()


def add_header_footer(slide, section_tag, title, slide_num, title_size=24):
    set_bg(slide, PAPER)
    add_rect(slide, Inches(0), Inches(0), SLIDE_W, Inches(0.10), fill_color=SAGE_DARK)
    add_textbox(slide, Inches(0.60), Inches(0.20), Inches(7.5), Inches(0.28),
                section_tag.upper(), size=10.5, bold=True, color=SAGE)
    add_textbox(slide, Inches(0.60), Inches(0.47), Inches(12.13), Inches(0.56),
                title, size=title_size, bold=True, color=SAGE_DARK, valign=MSO_ANCHOR.MIDDLE)
    add_rect(slide, Inches(0.60), Inches(1.09), Inches(12.13), Inches(0.025), fill_color=MINT)

    add_rect(slide, Inches(0.60), Inches(7.04), Inches(12.13), Inches(0.015), fill_color=MINT)
    add_textbox(slide, Inches(0.60), Inches(7.09), Inches(9.5), Inches(0.30),
                "Dippie: Website xem và gợi ý phim hoạt hình cho gia đình · Bài tập lớn Java OOP",
                size=10.5, color=MUTED, valign=MSO_ANCHOR.MIDDLE)
    pill = add_rect(slide, Inches(11.86), Inches(7.07), Inches(0.87), Inches(0.30),
                    fill_color=MINT_SOFT, border_color=BORDER, border_pt=0.8, rounded=True)
    ptf = pill.text_frame
    ptf.vertical_anchor = MSO_ANCHOR.MIDDLE
    p = ptf.paragraphs[0]
    p.alignment = PP_ALIGN.CENTER
    r = p.add_run()
    r.text = f"{slide_num} / {TOTAL_SLIDES}"
    r.font.name = FONT_NAME
    r.font.size = Pt(10.5)
    r.font.bold = True
    r.font.color.rgb = SAGE_DARK


def add_card(slide, left, top, width, height, title, body, fill=WHITE, border=BORDER,
             title_color=SAGE_DARK, title_size=15.5, body_size=13.2, accent_top=False,
             title_h=Inches(0.38), body_line_spacing=1.20):
    add_rect(slide, left, top, width, height, fill_color=fill, border_color=border, border_pt=1.1, rounded=True)
    offset_y = Inches(0.11)
    if accent_top:
        add_rect(slide, left + Inches(0.15), top + Inches(0.10), Inches(0.64), Inches(0.05), fill_color=SAGE)
        offset_y = Inches(0.20)
    add_textbox(slide, left + Inches(0.13), top + offset_y, width - Inches(0.26), title_h,
                title, size=title_size, bold=True, color=title_color, valign=MSO_ANCHOR.MIDDLE, line_spacing=1.08)
    body_top = top + offset_y + title_h + Inches(0.04)
    body_h = height - (body_top - top) - Inches(0.09)
    add_textbox(slide, left + Inches(0.13), body_top, width - Inches(0.26), body_h,
                body, size=body_size, color=INK, line_spacing=body_line_spacing)


def add_code_card(slide, left, top, width, height, pillar_title, path_caption, code_text,
                  border=SAGE, title_bg=MINT_SOFT, code_size=9.0):
    """Khung hiển thị 1 trụ cột OOP với đường dẫn tệp và đoạn mã monospace trích nguyên văn."""
    add_rect(slide, left, top, width, height, fill_color=WHITE, border_color=border, border_pt=1.2, rounded=True)
    hdr_h = Inches(0.45)
    add_rect(slide, left + Inches(0.02), top + Inches(0.02), width - Inches(0.04), hdr_h,
             fill_color=title_bg, border_color=None, rounded=False)
    add_textbox(slide, left + Inches(0.11), top + Inches(0.02), width - Inches(0.22), Inches(0.23),
                pillar_title, size=12.4, bold=True, color=SAGE_DARK, valign=MSO_ANCHOR.MIDDLE,
                margin_x=0.02, margin_y=0.0)
    add_textbox(slide, left + Inches(0.11), top + Inches(0.25), width - Inches(0.22), Inches(0.20),
                path_caption, size=7.8, italic=True, color=MUTED, valign=MSO_ANCHOR.MIDDLE,
                font_name=CODE_FONT, margin_x=0.02, margin_y=0.0)

    code_top = top + hdr_h + Inches(0.04)
    code_h = height - hdr_h - Inches(0.09)
    add_rect(slide, left + Inches(0.06), code_top, width - Inches(0.12), code_h,
             fill_color=CODE_BG, border_color=BORDER, border_pt=0.7, rounded=False)
    add_textbox(slide, left + Inches(0.09), code_top + Inches(0.03), width - Inches(0.18), code_h - Inches(0.06),
                code_text.strip("\n"), size=code_size, color=INK, line_spacing=1.04,
                font_name=CODE_FONT, margin_x=0.02, margin_y=0.01)


def add_banner(slide, left, top, width, height, text, fill=MINT, border=SAGE_DARK,
               color=SAGE_DARK, size=13.0, bold=True, rounded=True):
    add_rect(slide, left, top, width, height, fill_color=fill, border_color=border, border_pt=1.0, rounded=rounded)
    add_textbox(slide, left + Inches(0.14), top + Inches(0.04), width - Inches(0.28), height - Inches(0.08),
                text, size=size, bold=bold, color=color, align=PP_ALIGN.CENTER, valign=MSO_ANCHOR.MIDDLE,
                line_spacing=1.14)


def build_slides():
    required_imgs = ["arch_diagram.png", "cinebot_flow.png", "ux_audit_chart.png"]
    if not all((ASSETS_DIR / name).exists() for name in required_imgs):
        import generate_diagrams
        generate_diagrams.generate_all()

    prs = Presentation()
    prs.slide_width = SLIDE_W
    prs.slide_height = SLIDE_H
    blank = prs.slide_layouts[6]

    # =========================================================================
    # SLIDE 1: TIÊU ĐỀ VÀ NHÓM THỰC HIỆN
    # =========================================================================
    s1 = prs.slides.add_slide(blank)
    set_bg(s1, PAPER)
    add_rect(s1, Inches(0), Inches(0), Inches(0.40), SLIDE_H, fill_color=SAGE_DARK)
    add_rect(s1, Inches(0.40), Inches(0), Inches(0.12), SLIDE_H, fill_color=SAGE)

    tag = add_rect(s1, Inches(0.92), Inches(0.55), Inches(5.0), Inches(0.40),
                   fill_color=MINT_SOFT, border_color=SAGE, border_pt=1.0, rounded=True)
    tf = tag.text_frame
    tf.vertical_anchor = MSO_ANCHOR.MIDDLE
    p = tf.paragraphs[0]
    p.alignment = PP_ALIGN.CENTER
    r = p.add_run()
    r.text = "BÀI TẬP LỚN · LẬP TRÌNH HƯỚNG ĐỐI TƯỢNG JAVA"
    r.font.name = FONT_NAME
    r.font.size = Pt(12)
    r.font.bold = True
    r.font.color.rgb = SAGE_DARK

    add_textbox(s1, Inches(0.92), Inches(1.12), Inches(11.6), Inches(0.90),
                "Dippie", size=50, bold=True, color=SAGE_DARK)
    add_textbox(s1, Inches(0.92), Inches(2.04), Inches(11.4), Inches(0.62),
                "Website xem và gợi ý phim hoạt hình cho gia đình",
                size=26, bold=True, color=INK)
    add_textbox(s1, Inches(0.92), Inches(2.72), Inches(11.4), Inches(0.42),
                "Spring Boot 3.5 · Java 21 · React 19 · CineBot SSE · Dippie Admin JavaFX 21",
                size=15, italic=True, color=MUTED)

    kpis = [
        ("6 bộ · 806 tập", "Kho hoạt hình tiếng Việt\n1.705 phân đoạn có mốc cảnh"),
        ("908 ca kiểm thử", "589 backend (1 ca bỏ qua trên Windows)\n286 frontend · 33 desktop · chạy thử 36/36"),
        ("8,2 / 10 điểm UX", "Tăng từ 3,2 lên 6,8 rồi 8,2/10\nqua 3 vòng kiểm thử người dùng"),
    ]
    for i, (big, sub) in enumerate(kpis):
        x = Inches(0.92 + i * 3.95)
        add_rect(s1, x, Inches(3.26), Inches(3.74), Inches(1.24),
                 fill_color=MINT_SOFT, border_color=BORDER, border_pt=1.0, rounded=True)
        add_textbox(s1, x + Inches(0.14), Inches(3.33), Inches(3.46), Inches(0.40),
                    big, size=19, bold=True, color=SAGE_DARK, valign=MSO_ANCHOR.MIDDLE)
        add_textbox(s1, x + Inches(0.14), Inches(3.76), Inches(3.46), Inches(0.68),
                    sub, size=11.3, color=MUTED, line_spacing=1.16)

    add_textbox(s1, Inches(0.92), Inches(4.64), Inches(11.6), Inches(0.32),
                team.labeled("Trường", "truong", "............................", also=("Đại học", "Học viện", "Viện")) + "   ·   " +
                f"Lớp: {team.field('lop', '..................')}   ·   "
                f"Giảng viên: {team.field('giang_vien', '..............................')}",
                size=12.0, bold=True, color=HONEY_DARK)
    members_s1 = [
        (team.label(1), f"Mã SV: {team.student_id(1)}\nNhóm trưởng · backend, CineBot"),
        (team.label(2), f"Mã SV: {team.student_id(2)}\nJavaFX, JDBC và CSDL"),
        (team.label(3), f"Mã SV: {team.student_id(3)}\nFrontend React và UX"),
        (team.label(4), f"Mã SV: {team.student_id(4)}\nDữ liệu phim, triển khai"),
    ]
    for i, (m_name, m_role) in enumerate(members_s1):
        x = Inches(0.92 + i * 2.96)
        add_card(s1, x, Inches(5.06), Inches(2.79), Inches(1.54),
                 m_name, m_role, fill=WHITE, border=SAGE, title_size=14.0, body_size=11.4,
                 title_h=Inches(0.34), body_line_spacing=1.22)

    add_textbox(s1, Inches(0.92), Inches(6.78), Inches(11.6), Inches(0.32),
                "Trình bày 5 phút (14 slide) và demo trực tiếp Web cùng Dippie Admin",
                size=11.8, italic=True, color=MUTED)

    set_notes(s1, (
        f"Dạ em chào thầy và các bạn. Em là {team.nickname(1) or 'nhóm trưởng'}, đại diện nhóm 4 bạn trình bày bài tập lớn Java OOP "
        "với đề tài Dippie, website xem và gợi ý phim hoạt hình cho gia đình kèm ứng dụng quản trị JavaFX. "
        "Trong 5 phút tới, nhóm em sẽ đi qua bài toán, MVC, 4 trụ cột OOP, mẫu GoF rồi demo trực tiếp ạ."
    ))

    # =========================================================================
    # SLIDE 2: BÀI TOÁN THỰC TẾ
    # =========================================================================
    s2 = prs.slides.add_slide(blank)
    add_header_footer(s2, "01 · Đặt vấn đề", "Bốn vướng mắc khi phụ huynh tìm phim cho trẻ nhỏ", 2)

    problems = [
        ("1. Nguồn phim phân tán",
         "Hoạt hình tiếng Việt rải trên nhiều kênh YouTube, đánh số tập lộn xộn và dễ lẫn video không hợp lứa tuổi."),
        ("2. Khó tìm đúng cảnh bé nhớ",
         "Trẻ hay nhớ tình tiết nhỏ (\"Red đội kem\", \"Nobita đi vũ trụ\") thay vì tên tập chính xác."),
        ("3. Thiếu lọc theo ngữ cảnh gia đình",
         "Khó chọn tập ngắn dưới 5 phút trước giờ ngủ, không lưu được chỗ đang xem dở giữa các buổi."),
        ("4. Nguồn video ngoài hay lỗi",
         "Nhiều tập YouTube bị chặn vùng Việt Nam hoặc tắt nhúng, cần quản trị danh mục theo phiên bản."),
    ]
    positions = [
        (Inches(0.60), Inches(1.42)),
        (Inches(6.82), Inches(1.42)),
        (Inches(0.60), Inches(4.20)),
        (Inches(6.82), Inches(4.20)),
    ]
    for (lx, ty), (pt, pb) in zip(positions, problems):
        add_card(s2, lx, ty, Inches(5.90), Inches(2.48), pt, pb,
                 fill=WHITE, border=BORDER, title_size=17.5, body_size=15.0, accent_top=True,
                 title_h=Inches(0.42), body_line_spacing=1.24)

    set_notes(s2, (
        "Dạ, đề tài xuất phát từ nhu cầu thật ở nhà. Phim hoạt hình tiếng Việt nằm rải nhiều kênh YouTube, "
        "trẻ nhỏ lại chỉ nhớ một cảnh như Red đội kem chứ không nhớ tên tập. Phụ huynh cũng khó lọc tập "
        "ngắn dưới 5 phút trước giờ ngủ, lưu đoạn xem dở và xử lý mấy tập bị chặn nhúng ạ."
    ))

    # =========================================================================
    # SLIDE 3: GIẢI PHÁP, CHỨC NĂNG CHÍNH VÀ TRANG CHỦ
    # =========================================================================
    s3 = prs.slides.add_slide(blank)
    add_header_footer(s3, "02 · Sản phẩm Dippie", "Giải pháp tổng thể và 5 nhóm tính năng chính", 3)

    add_bullets(s3, Inches(0.60), Inches(1.38), Inches(6.28), Inches(5.40), [
        "Tìm kiếm 6 bộ (806 tập) không dấu, hiểu bí danh và chịu lỗi gõ 1 đến 2 ký tự.",
        "Phát video YouTube IFrame API và HTTP Byte Range, lưu và xóa lịch sử xem.",
        "Thư viện, chấm điểm 1 đến 10, 6 tâm trạng, gộp dữ liệu khách có xác nhận.",
        "Tài khoản đủ vòng đời: đồng ý điều khoản, đăng xuất thu hồi token, đặt lại mật khẩu, xóa tài khoản.",
        "Trợ lý CineBot (SSE), báo lỗi phát video, quản trị Web 5 mục và Dippie Admin JavaFX.",
    ], size=14.6, space_after=13, line_spacing=1.18)

    add_screenshot_card(
        s3, Inches(7.12), Inches(1.34), Inches(5.60), Inches(3.80),
        SCREENSHOTS_DIR / "home.png",
        "Trang chủ Dippie (1440x900), giao diện sáng cho gia đình",
        bg=WHITE, border=SAGE, caption_size=11.0
    )

    add_rect(s3, Inches(7.12), Inches(5.26), Inches(5.60), Inches(1.58),
             fill_color=MINT_SOFT, border_color=SAGE, border_pt=1.1, rounded=True)
    series_grid = [
        ("Doraemon", "337 tập"), ("Công chúa Ori", "180 tập"),
        ("Larva", "104 tập"), ("Xin Chào Jadoo", "99 tập"),
        ("Heo Peppa", "51 tập"), ("Trạng Quỳnh", "35 tập"),
    ]
    for idx, (sname, sstat) in enumerate(series_grid):
        col = idx % 2
        row = idx // 2
        rx = Inches(7.26 + col * 2.72)
        ry = Inches(5.34 + row * 0.33)
        add_textbox(s3, rx, ry, Inches(1.74), Inches(0.28), sname,
                    size=11.5, bold=True, color=INK, valign=MSO_ANCHOR.MIDDLE)
        add_textbox(s3, rx + Inches(1.72), ry, Inches(0.86), Inches(0.28), sstat,
                    size=11.5, bold=True, color=SAGE_DARK, align=PP_ALIGN.RIGHT, valign=MSO_ANCHOR.MIDDLE)
    add_rect(s3, Inches(7.28), Inches(6.36), Inches(5.28), Inches(0.015), fill_color=BORDER)
    add_textbox(s3, Inches(7.22), Inches(6.41), Inches(5.40), Inches(0.36),
                "Tổng: 6 bộ · 20 mùa · 806 tập · 1.705 phân đoạn",
                size=11.5, bold=True, color=HONEY_DARK, align=PP_ALIGN.CENTER, valign=MSO_ANCHOR.MIDDLE)

    set_notes(s3, (
        "Dippie gom 6 bộ hoạt hình quen thuộc gồm 20 mùa, 806 tập và 1.705 phân đoạn có mốc thời gian. "
        f"Bên phải là ảnh trang chủ do {team.call(3)} dựng. Người dùng tìm không dấu được, lưu tiến độ "
        "xem, chấm điểm 1 đến 10, chọn 6 tâm trạng và hỏi CineBot nhảy đúng cảnh phim ạ."
    ))

    # =========================================================================
    # SLIDE 4: CÔNG NGHỆ SỬ DỤNG
    # =========================================================================
    s4 = prs.slides.add_slide(blank)
    add_header_footer(s4, "03 · Công nghệ", "Công nghệ áp dụng trong Dippie", 4)

    cols = [
        ("Backend Java 21",
         "• Records, Text Blocks,\n  Pattern Matching, Sealed\n"
         "• Spring Boot 3.5, JPA Criteria\n"
         "• Spring Security, JWT HS256\n"
         "• Flyway, UTC, H2 / MySQL TLS",
         MINT_SOFT, SAGE_DARK),
        ("Frontend Web SPA",
         "• React 19, TypeScript 5.7\n"
         "• Vite 8 đóng gói phục vụ :3000\n"
         "• Tailwind CSS 3.4 responsive\n"
         "• YouTube IFrame Player API\n"
         "• Báo mất mạng, pháp lý, ARIA",
         WHITE, SAGE_DARK),
        ("Desktop JavaFX",
         "• JavaFX 21: BarChart,\n  TreeView, TableView\n"
         "• JDBC thuần, PreparedStatement\n"
         "• Giao dịch commit / rollback\n"
         "• BCrypt và xuất CSV I/O",
         HONEY_SOFT, HONEY_DARK),
        ("AI, Stream, DevOps",
         "• Chỉ mục BM25 cục bộ trên RAM\n"
         "• SSE /api/chat, ChatStreamGuard\n"
         "• Phát video HTTP Byte Range\n"
         "• CHAY_DIPPIE.bat, Docker, Caddy\n"
         "• GitHub Actions CI, Dependabot",
         MINT_SOFT, SAGE_DARK),
    ]
    for i, (ct, cb, cf, tc) in enumerate(cols):
        x = Inches(0.60 + i * 3.09)
        add_card(s4, x, Inches(1.38), Inches(2.90), Inches(4.48),
                 ct, cb, fill=cf, border=BORDER, title_color=tc,
                 title_size=15.0, body_size=12.8, accent_top=True,
                 title_h=Inches(0.38), body_line_spacing=1.24)

    add_banner(
        s4, Inches(0.60), Inches(6.04), Inches(12.13), Inches(0.76),
        "Hợp nhất REST API, chỉ mục BM25, luồng SSE, Byte Range và giao diện React 19 trong 1 tiến trình Java 21.",
        fill=MINT, border=SAGE_DARK, color=SAGE_DARK, size=13.5, bold=True
    )

    set_notes(s4, (
        "Về công nghệ, backend dùng Java 21, Spring Boot 3.5, JWT HS256 và JPA, chạy được cả H2 demo lẫn "
        "MySQL 8.4. Giao diện web viết bằng React 19 và TypeScript, còn bản desktop dùng JavaFX 21 và JDBC "
        "thuần. Ngoài ra có BM25 trong RAM, SSE CineBot, Byte Range và Docker kèm Caddy HTTPS ạ."
    ))

    # =========================================================================
    # SLIDE 5: KIẾN TRÚC TỔNG THỂ VÀ ÁNH XẠ MVC
    # =========================================================================
    s5 = prs.slides.add_slide(blank)
    add_header_footer(s5, "04 · Thiết kế hệ thống", "Kiến trúc hợp nhất và ánh xạ MVC (Web và Desktop)", 5)

    # Bên trái: sơ đồ kiến trúc + 2 thẻ số liệu
    add_picture_fit(s5, ASSETS_DIR / "arch_diagram.png", Inches(0.55), Inches(1.26), Inches(7.72), Inches(4.36))

    left_badges = [
        "1 tiến trình JVM (:3000)\nĐộ trễ API demo 3 đến 43 ms",
        "32 bảng BCNF · 74 package MVC · 5 Flyway\nH2 AUTO_SERVER dùng chung Web, JavaFX",
    ]
    for i, btxt in enumerate(left_badges):
        bx = Inches(0.60 + i * 3.88)
        add_banner(s5, bx, Inches(5.76), Inches(3.74), Inches(1.06), btxt,
                   fill=MINT_SOFT, border=SAGE, color=SAGE_DARK, size=11.5, bold=True)

    # Bên phải: ánh xạ MVC và 5 tầng backend (controller / service / repository / model / dto)
    add_textbox(s5, Inches(8.46), Inches(1.18), Inches(4.27), Inches(0.28),
                "ÁNH XẠ MVC VÀ 5 TẦNG BACKEND", size=11.8, bold=True, color=SAGE_DARK, valign=MSO_ANCHOR.MIDDLE)
    mvc_cards = [
        ("Controller (C)",
         "BE: controller (25 REST + GlobalExceptionHandler).\nDesktop: AdminController (filterCatalog), LoginController.",
         HONEY_SOFT, HONEY_DARK),
        ("Service (giữa C và M)",
         "BE: service (nghiệp vụ, BM25, CineBot, DiscoverService).\nDesktop: service (AdminServices, Command, Proxy).",
         MINT_SOFT, SAGE_DARK),
        ("Model (M: model + repository)",
         "BE: model (JPA, quy tắc) + repository (JPA, query).\nDesktop: model (TopRated, RatingRow...) + dao + db.",
         WHITE, SAGE_DARK),
        ("View (V: React + dto)",
         "Web: React 19 SPA nhận JSON từ dto (60 record).\nDesktop: 9 lớp view chỉ gọi controller, model, export.",
         MINT_SOFT, SAGE_DARK),
    ]
    for i, (mt, mb, mfill, mclr) in enumerate(mvc_cards):
        my = Inches(1.50 + i * 1.22)
        add_card(s5, Inches(8.46), my, Inches(4.27), Inches(1.14),
                 mt, mb, fill=mfill, border=BORDER, title_color=mclr,
                 title_size=12.0, body_size=10.0, accent_top=False,
                 title_h=Inches(0.25), body_line_spacing=1.14)

    add_banner(
        s5, Inches(8.46), Inches(6.40), Inches(4.27), Inches(0.50),
        "Controller chỉ gọi service, không gọi thẳng repository\nModel độc lập tầng trên · Repository không gọi service",
        fill=MINT, border=SAGE_DARK, color=SAGE_DARK, size=8.8, bold=True
    )

    set_notes(s5, (
        "Dạ, phần này em trình bày kiến trúc tổng thể và ánh xạ MVC. Bên trái là một tiến trình Spring Boot "
        "phục vụ cả React lẫn API, nối chung cơ sở dữ liệu 32 bảng với Dippie Admin. Bên phải, backend chia "
        "theo tầng trước thành controller, service, repository, model và dto rồi chia gói con theo tính năng; "
        "controller chỉ gọi service chứ không chạm thẳng repository, còn tầng model độc lập. Bản JavaFX cũng "
        "tách chặt MVC, tầng view chỉ gọi controller, model và export ạ."
    ))

    # =========================================================================
    # SLIDE 6: 4 TRỤ CỘT OOP TRONG CODE DIPPIE
    # =========================================================================
    s6 = prs.slides.add_slide(blank)
    add_header_footer(s6, "05 · Cốt lõi OOP Java", "4 trụ cột OOP trong code Dippie", 6)

    code_extends = """public abstract class DomainException extends RuntimeException {
    private final HttpStatus status;
    ...
}
// không tìm thấy dữ liệu cần lấy, trả 404
public class NotFoundException extends DomainException {
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}"""

    code_interface = """// kiểu Strategy cho các cách gợi ý phim, thêm kiểu gợi ý mới thì viết thêm 1 lớp là xong
public interface RecommendationStrategy {
    RecommendationResult recommend(RecommendationContext context);
}
// interface chung cho mọi DAO chỉ đọc theo thực thể T và kiểu khóa chính ID
public interface Dao<T, ID> {
    Optional<T> findById(ID id);
    List<T> findAll();
    long count();
}"""

    code_abstract = """public abstract class AbstractLibraryListService<E extends LibraryEntry> {
    ...
    protected abstract E newEntry(Long userId, String movieId, LocalDateTime addedAt);
}
public class FavoriteService extends AbstractLibraryListService<FavoriteItem> {
    ...
    @Override
    protected FavoriteItem newEntry(Long userId, String movieId, LocalDateTime addedAt) {
        return new FavoriteItem(userId, movieId, addedAt);
    }
}"""

    code_poly = """RecommendationStrategy strategy = wantsMood ? mood : affinity;
var result = strategy.recommend(context);
...
@ExceptionHandler(DomainException.class)
public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException exception) {
    HttpHeaders headers = new HttpHeaders();
    exception.headers().forEach(headers::add);
    return ResponseEntity.status(exception.status())
            .headers(headers)
            .body(ApiResponse.failure(exception.getMessage()));
}"""

    oop_cards = [
        (Inches(0.58), Inches(1.20), "1. Kế thừa (extends)",
         "exception/DomainException.java & exception/NotFoundException.java",
         code_extends, SAGE, MINT_SOFT),
        (Inches(6.74), Inches(1.20), "2. Giao diện (interface)",
         "service/recommendation/RecommendationStrategy.java & desktop/dao/Dao.java",
         code_interface, SAGE, MINT_SOFT),
        (Inches(0.58), Inches(3.86), "3. Lớp trừu tượng (abstract / Template Method)",
         "service/library/AbstractLibraryListService.java & service/library/FavoriteService.java",
         code_abstract, HONEY_DARK, HONEY_SOFT),
        (Inches(6.74), Inches(3.86), "4. Đa hình (Polymorphism)",
         "service/recommendation/RecommendationService.java & controller/web/GlobalExceptionHandler.java",
         code_poly, SAGE, MINT_SOFT),
    ]
    for cx, cy, ctitle, cpath, ccode, cbdr, chdr in oop_cards:
        add_code_card(s6, cx, cy, Inches(6.00), Inches(2.58), ctitle, cpath, ccode,
                      border=cbdr, title_bg=chdr, code_size=8.3)

    add_banner(
        s6, Inches(0.58), Inches(6.52), Inches(12.16), Inches(0.42),
        "Backend chính (333 tệp): 4 lớp abstract · 35 interface (4 sealed) · 11 lớp con CatalogRecord · 11 lớp con DomainException · 72 @Override",
        fill=MINT, border=SAGE_DARK, color=SAGE_DARK, size=11.0, bold=True
    )

    set_notes(s6, (
        "Dạ, đây là 4 đoạn code thật minh họa 4 trụ cột OOP. Kế thừa có DomainException và NotFoundException; "
        "interface có RecommendationStrategy; lớp abstract có AbstractLibraryListService để FavoriteService "
        "và WatchlistService chỉ cài newEntry; còn đa hình thể hiện ở strategy.recommend và "
        "GlobalExceptionHandler xử lý chung mọi DomainException qua exception.status() ạ."
    ))

    # =========================================================================
    # SLIDE 7: CÁC MẪU THIẾT KẾ GOF VÀ KỸ THUẬT JAVA 21
    # =========================================================================
    s7 = prs.slides.add_slide(blank)
    add_header_footer(s7, "06 · Design Patterns & Java 21", "Các mẫu thiết kế GoF và tính năng Java 21 trong dự án", 7)

    add_rect(s7, Inches(0.60), Inches(1.34), Inches(12.13), Inches(5.48),
             fill_color=WHITE, border_color=SAGE, border_pt=1.2, rounded=True)
    add_bullets(s7, Inches(0.84), Inches(1.50), Inches(11.65), Inches(5.16), [
        "Java 21 và I/O: 145 tệp record, 4 sealed interface backend (ChatEvent, PlaybackPrincipal, PlaybackAssetId, HealthStatus), CatalogNode ở desktop; FileChannel 64 KB (ResolvedMediaStream) và CsvExporter UTF-8 BOM.",
        "Adapter (tách phụ thuộc chéo giữa các tính năng ở tầng service): ActiveViewerLookup / UserViewerAdapter, MediaFileAvailabilityAdapter, MoviePresentationAdapter và RatingLookup / RatingSummaries.",
        "Proxy và Facade: Caching Proxy CatalogReadModelCache, Protection Proxy AdminQuery<T> và PlaybackAccess; Facade CatalogImportService, ChatService, DiscoverService và AdminServices.",
        "Strategy, Observer và Template Method: RecommendationStrategy, AnswerGenerator, MailDeliveryStrategy, CsvStrategy; CatalogImportedEvent và JavaFX Property; AbstractLibraryListService và AbstractJdbcDao.",
        "State, Command, Composite và Creational: CatalogSnapshot.Status và ConnectionState; WatchProgressCommand, ChatStreamGuard.Permit, AccountCommand + CommandHistory (Hoàn tác); CatalogNode; ConnectionProvider và DatabaseFactory.",
    ], size=13.6, space_after=12, line_spacing=1.18)

    set_notes(s7, (
        "Về mẫu thiết kế GoF, backend dùng các lớp Adapter như UserViewerAdapter, MediaFileAvailabilityAdapter và "
        "MoviePresentationAdapter để tách phụ thuộc chéo giữa các tính năng trong tầng service. Ngoài ra có Proxy "
        "CatalogReadModelCache, Strategy cho gợi ý và gửi mail, State trên CatalogSnapshot, 4 sealed interface "
        "Java 21, còn desktop dùng Composite CatalogNode và Command cho nút Hoàn tác ạ."
    ))

    # =========================================================================
    # SLIDE 8: TRỢ LÝ CINEBOT
    # =========================================================================
    s8 = prs.slides.add_slide(blank)
    add_header_footer(s8, "07 · Tính năng nổi bật", "Trợ lý CineBot: BM25 cục bộ, SSE và LLM có căn cứ", 8)

    add_picture_fit(s8, ASSETS_DIR / "cinebot_flow.png", Inches(0.60), Inches(1.24), Inches(12.13), Inches(3.18))

    add_bullets(s8, Inches(0.60), Inches(4.58), Inches(12.13), Inches(2.26), [
        "Chỉ mục BM25 cục bộ trong RAM phủ hơn 2.517 tài liệu, phản hồi SSE trung bình 5 ms, chạy được cả khi mất mạng.",
        "Hỏi theo tình tiết như 'Cảnh Red đội kem' sẽ mở đúng tập Kem tại 00:33, hỗ trợ hỏi tiếp nhiều lượt.",
        "Lọc thời lượng dưới 5 phút, từ chối phim ngoài danh mục (Naruto, One Piece) và tự quay về mẫu cục bộ nếu DeepSeek lỗi.",
    ], size=14.8, space_after=11, line_spacing=1.20)

    set_notes(s8, (
        "Phần CineBot này em làm. "
        + "Câu hỏi đi qua 5 bước trong Java, "
        "tìm trên chỉ mục BM25 trong RAM hơn 2.517 tài liệu rồi phát luồng SSE. Nhờ bám dữ liệu chuẩn, "
        "bấm Xem tập sẽ mở đúng giây cảnh phim và từ chối phim ngoài kho ạ."
    ))

    # =========================================================================
    # SLIDE 9: ỨNG DỤNG QUẢN TRỊ JAVAFX (DIPPIE ADMIN)
    # =========================================================================
    s9 = prs.slides.add_slide(blank)
    add_header_footer(s9, "08 · Ứng dụng Desktop", "Dippie Admin: Ứng dụng quản trị JavaFX 21 và JDBC", 9, title_size=23)

    add_bullets(s9, Inches(0.60), Inches(1.34), Inches(6.38), Inches(4.78), [
        "JDBC thuần với PreparedStatement và giao dịch commit/rollback; DatabaseFactory chọn H2 hoặc MySQL.",
        "Đăng nhập kiểm tra BCrypt cho ROLE_ADMIN; Proxy AdminQuery xác minh phiên trước mỗi truy vấn.",
        "Tab Tổng quan vẽ BarChart điểm 1 đến 10; tab Danh mục hiển thị cây Composite trên TreeView.",
        "Tab Người dùng khóa/mở khóa, đổi vai trò kèm ghi kiểm toán và nút Hoàn tác (Command Pattern).",
        "Tab Đánh giá kiểm duyệt bình chọn; tự làm mới tab qua Observer; xuất báo cáo CSV UTF-8 BOM.",
    ], size=13.8, space_after=10, line_spacing=1.17)

    add_banner(
        s9, Inches(0.60), Inches(6.24), Inches(6.38), Inches(0.66),
        "64 tệp Java (3.605 dòng) · 9 package MVC · 9 lớp View · 33/33 ca JUnit 5 đạt",
        fill=MINT_SOFT, border=SAGE, color=SAGE_DARK, size=11.8, bold=True
    )

    add_screenshot_card(
        s9, Inches(7.18), Inches(1.28), Inches(5.55), Inches(3.52),
        DESKTOP_SHOTS_DIR / "dashboard.png",
        "Tab Tổng quan: số liệu thực, top phim và biểu đồ điểm 1 đến 10",
        bg=WHITE, border=SAGE, caption_size=10.5
    )
    add_screenshot_card(
        s9, Inches(7.18), Inches(4.90), Inches(2.71), Inches(2.00),
        DESKTOP_SHOTS_DIR / "catalog.png",
        "Tab Danh mục (Composite)",
        bg=WHITE, border=BORDER, caption_size=10.0
    )
    add_screenshot_card(
        s9, Inches(10.02), Inches(4.90), Inches(2.71), Inches(2.00),
        DESKTOP_SHOTS_DIR / "users.png",
        "Tab Người dùng (Hoàn tác)",
        bg=WHITE, border=BORDER, caption_size=10.0
    )

    set_notes(s9, (
        (f"Phần Dippie Admin này do {team.nickname(2)} làm và trình bày. " if team.nickname(2) else "Tiếp theo là ứng dụng desktop Dippie Admin. ")
        + "Ứng dụng gồm "
        "64 tệp Java và 33 ca test JUnit 5, dùng JavaFX 21 với JDBC thuần trên 4 tab: Tổng quan có BarChart, "
        "Danh mục dùng Composite, Người dùng có Hoàn tác bằng Command và Xuất CSV UTF-8 BOM ạ."
    ))

    # =========================================================================
    # SLIDE 10: KIỂM THỬ VÀ 3 VÒNG ĐÁNH GIÁ UX
    # =========================================================================
    s10 = prs.slides.add_slide(blank)
    add_header_footer(s10, "09 · Chất lượng phần mềm", "Kiểm thử tự động, rà soát bảo mật và 3 vòng UX", 10)

    add_picture_fit(s10, ASSETS_DIR / "ux_audit_chart.png", Inches(0.55), Inches(1.32), Inches(7.92), Inches(5.46))

    metrics_s10 = [
        ("3,2 lên 6,8 lên 8,2", "Điểm UX trung bình trên thang 10\nSửa 5/5 lỗi vòng 1 · chạy thử cuối 36/36", MINT_SOFT, SAGE_DARK, 17.5),
        ("589 ca backend", "69 lớp JUnit, 1 ca bỏ qua trên Windows\nSửa 6/6 lỗi nghiêm trọng OWASP (30 mục)", WHITE, SAGE_DARK, 17.5),
        ("286 FE · 33 desktop", "29 bộ test frontend · 5 lớp JUnit desktop\n0 tràn ngang mobile 375 px · chạm 44 px", HONEY_SOFT, HONEY_DARK, 17.5),
    ]
    for i, (m_big, m_desc, m_bg, m_clr, big_sz) in enumerate(metrics_s10):
        y = Inches(1.40 + i * 1.80)
        add_rect(s10, Inches(8.70), y, Inches(4.02), Inches(1.64),
                 fill_color=m_bg, border_color=BORDER, border_pt=1.2, rounded=True)
        add_textbox(s10, Inches(8.88), y + Inches(0.12), Inches(3.68), Inches(0.46),
                    m_big, size=big_sz, bold=True, color=m_clr, valign=MSO_ANCHOR.MIDDLE)
        add_textbox(s10, Inches(8.88), y + Inches(0.64), Inches(3.68), Inches(0.88),
                    m_desc, size=11.6, color=INK, line_spacing=1.20)

    set_notes(s10, (
        "Dạ, nhóm em chạy 3 vòng kiểm thử trải nghiệm trên cả máy tính lẫn điện thoại 375 pixel, điểm trung "
        "bình tăng từ 3,2 lên 6,8 rồi đạt 8,2 trên 10. Về kiểm thử tự động có 589 ca backend, 286 ca frontend, "
        "33 ca desktop, chạy thử trọn bộ trên bản cuối đạt 36 trên 36, và đợt rà soát OWASP xử lý xong 6 trên 6 lỗi nghiêm trọng ạ."
    ))

    # =========================================================================
    # SLIDE 11: TRIỂN KHAI THỬ NGHIỆM
    # =========================================================================
    s11 = prs.slides.add_slide(blank)
    add_header_footer(s11, "10 · Vận hành & Đo tải", "Triển khai thử nghiệm từ Demo 1 cú nhấp đến Production", 11, title_size=23)

    deploy_cards = [
        ("1. Chạy thử 1 cú nhấp",
         "• Nhấp đúp CHAY_DIPPIE.bat\n"
         "• Tự tải JDK 21 di động nếu thiếu\n"
         "• Tự nạp 6 bộ (806 tập), mở Web\n"
         "  và Dippie Admin (H2 demo)\n"
         "• HUONG_DAN_CHAY.md · zip phát hành",
         MINT_SOFT, SAGE_DARK),
        ("2. Ứng dụng Desktop",
         "• Mở tự động cùng CHAY_DIPPIE.bat\n"
         "  hoặc chạy riêng .\\start-desktop.ps1\n"
         "• Mặc định: H2 (AUTO_SERVER)\n"
         "• MySQL qua desktop.properties\n"
         "• Đồng bộ ngay với bản Web",
         HONEY_SOFT, HONEY_DARK),
        ("3. Docker và Caddy HTTPS",
         "• Dockerfile 3 tầng (non-root)\n"
         "• docker-compose.yml (prod)\n"
         "• Caddyfile: TLS tự động, SSE\n"
         "• MySQL TLS, Flyway V1 đến V5\n"
         "• backup/restore MySQL SHA-256",
         WHITE, SAGE_DARK),
    ]
    for i, (dt, db, dbg, dclr) in enumerate(deploy_cards):
        x = Inches(0.60 + i * 4.12)
        add_card(s11, x, Inches(1.36), Inches(3.88), Inches(3.74),
                 dt, db, fill=dbg, border=BORDER, title_color=dclr,
                 title_size=15.0, body_size=12.6, accent_top=True,
                 title_h=Inches(0.36), body_line_spacing=1.22)

    add_rect(s11, Inches(0.60), Inches(5.26), Inches(12.13), Inches(1.60),
             fill_color=MINT_SOFT, border_color=SAGE, border_pt=1.2, rounded=True)
    add_bullets(s11, Inches(0.80), Inches(5.36), Inches(11.73), Inches(1.40), [
        "Đo tải và chạy thử cuối: API demo 3 đến 43 ms; chạy thử trọn bộ đạt 36/36 (nạp 6 bộ, 806 tập trong khoảng 7 giây); MySQL 100.000 tập vẫn mở chi tiết trong 6,4 ms.",
        "Mục còn mở: 707/806 tập phụ thuộc YouTube (44 tập Ori mở ngoài), 99 tập Jadoo cần tệp Drive, chưa có hồ sơ trẻ em/PIN phụ huynh và phụ đề.",
    ], size=12.8, space_after=8, line_spacing=1.18)

    set_notes(s11, (
        (f"Phần triển khai do {team.nickname(4)} lo. Người dùng" if team.nickname(4) else "Về triển khai, người dùng")
        + " chỉ cần giải nén gói phát hành tạo bằng "
        "make-release-zip.ps1 rồi nhấp đúp CHAY_DIPPIE.bat là tự tải JDK nếu thiếu, nạp 6 bộ phim và mở cả Web lẫn "
        "Dippie Admin. Bản production chạy 3 container Caddy HTTPS, app non-root và MySQL TLS cùng Flyway ạ."
    ))

    # =========================================================================
    # SLIDE 12: PHÂN CÔNG NHÓM VÀ QUY TRÌNH LÀM VIỆC
    # =========================================================================
    s12 = prs.slides.add_slide(blank)
    add_header_footer(s12, "11 · Tổ chức nhóm", "Phân công 4 thành viên và quy trình phối hợp", 12)

    team_rows = [
        (team.label(1), "Nhóm trưởng · backend, CineBot",
         "Spring Boot 3.5, bảo mật JWT và rà soát OWASP, danh mục, tiến độ xem; chỉ mục BM25, luồng CineBot SSE, gợi ý Affinity và Mood."),
        (team.label(2), "JavaFX, JDBC và CSDL",
         "Dippie Admin JavaFX 21 với JDBC thuần (64 tệp, 33 test, MVC, DAO, Composite, Command); 32 bảng MySQL/H2, Flyway, view, trigger và đo tải."),
        (team.label(3), "Frontend React và UX",
         "81 tệp React 19/Tailwind, trình phát YouTube, đồng bộ dữ liệu khách, trang lịch sử, 286 ca test; phía Java làm DTO và test API thư viện."),
        (team.label(4), "Dữ liệu phim và triển khai",
         "Chuẩn hóa 6 bộ phim, CHAY_DIPPIE.bat, Docker, Caddy HTTPS, CI và kịch bản demo; phía Java làm CatalogPackageValidator kèm test."),
    ]
    for i, (m_id, m_role, m_work) in enumerate(team_rows):
        y = Inches(1.36 + i * 1.12)
        bg = WHITE if i % 2 == 0 else MINT_SOFT
        add_rect(s12, Inches(0.60), y, Inches(12.13), Inches(0.98),
                 fill_color=bg, border_color=BORDER, border_pt=1.0, rounded=True)
        add_rect(s12, Inches(0.60), y, Inches(0.14), Inches(0.98), fill_color=SAGE_DARK)
        add_textbox(s12, Inches(0.88), y + Inches(0.08), Inches(2.60), Inches(0.36),
                    m_id, size=15.0, bold=True, color=SAGE_DARK, valign=MSO_ANCHOR.MIDDLE)
        add_textbox(s12, Inches(0.88), y + Inches(0.46), Inches(2.70), Inches(0.38),
                    m_role, size=11.8, bold=True, color=HONEY_DARK, valign=MSO_ANCHOR.MIDDLE)
        add_textbox(s12, Inches(3.74), y + Inches(0.08), Inches(8.84), Inches(0.82),
                    m_work, size=13.0, color=INK, valign=MSO_ANCHOR.MIDDLE, line_spacing=1.16)

    add_banner(
        s12, Inches(0.60), Inches(5.96), Inches(12.13), Inches(0.86),
        "Quy trình nhóm: nhánh Git theo tính năng · hợp đồng API CONTRACT.md\nHọp hằng tuần theo Task Board · Review mã chéo qua Pull Request và GitHub Actions CI",
        fill=HONEY_SOFT, border=HONEY_DARK, color=HONEY_DARK, size=12.6, bold=True
    )

    set_notes(s12, (
        f"Dạ, phần Java chính do em và {team.nickname(2) or 'một bạn'} chia nhau: em làm backend và CineBot, {team.nickname(2) or 'bạn ấy'} làm Dippie Admin "
        f"bằng JavaFX cùng cơ sở dữ liệu. {team.nickname(3) or 'Một bạn'} làm frontend, {team.nickname(4) or 'bạn còn lại'} lo dữ liệu phim và triển khai, "
        "hai bạn đều nhận thêm một phần Java nhỏ có test. "
        "Nhóm chốt hợp đồng API trong CONTRACT.md và review chéo qua Pull Request ạ."
    ))

    # =========================================================================
    # SLIDE 13: GIAO DIỆN THỰC TẾ WEB VÀ DI ĐỘNG
    # =========================================================================
    s13 = prs.slides.add_slide(blank)
    add_header_footer(s13, "12 · Giao diện thực tế", "Giao diện Dippie trên máy tính và điện thoại", 13)

    add_screenshot_card(
        s13, Inches(0.60), Inches(1.32), Inches(4.46), Inches(2.68),
        SCREENSHOTS_DIR / "series-list.png",
        "Trang Phim bộ (/series), lọc 6 thể loại và sắp xếp",
        bg=WHITE, border=BORDER, caption_size=10.8
    )
    add_screenshot_card(
        s13, Inches(5.22), Inches(1.32), Inches(4.46), Inches(2.68),
        SCREENSHOTS_DIR / "search-khung-long.png",
        "Trang Kho phim (/search), tìm 'khủng long' (9 tập)",
        bg=WHITE, border=BORDER, caption_size=10.8
    )
    add_screenshot_card(
        s13, Inches(0.60), Inches(4.16), Inches(4.46), Inches(2.68),
        SCREENSHOTS_DIR / "series-doraemon.png",
        "Chi tiết Doraemon: Lưu, Yêu thích, Chấm điểm và 7 mùa",
        bg=WHITE, border=BORDER, caption_size=10.8
    )

    add_rect(s13, Inches(5.22), Inches(4.16), Inches(4.46), Inches(2.68),
             fill_color=MINT_SOFT, border_color=SAGE, border_pt=1.1, rounded=True)
    add_textbox(s13, Inches(5.40), Inches(4.26), Inches(4.10), Inches(0.34),
                "CHI TIẾT GIAO DIỆN VÀ UX", size=12.8, bold=True, color=SAGE_DARK, valign=MSO_ANCHOR.MIDDLE)
    add_bullets(s13, Inches(5.38), Inches(4.66), Inches(4.14), Inches(2.08), [
        "Nền sáng màu giấy trắng, xanh xô thơm dịu mắt.",
        "Lưu, Yêu thích và Chấm điểm ngay trên banner.",
        "Tìm không dấu, lỗi gõ, lọc thể loại và năm.",
        "Di động 390 px: 0 tràn ngang, vùng chạm >= 44 px.",
    ], size=11.8, space_after=8, line_spacing=1.16)

    add_screenshot_card(
        s13, Inches(9.84), Inches(1.32), Inches(2.89), Inches(5.52),
        SCREENSHOTS_DIR / "home-mobile.png",
        "Trang chủ di động (390x844)",
        bg=WHITE, border=SAGE, caption_size=11.0
    )

    set_notes(s13, (
        (f"Phần giao diện này {team.nickname(3)} trình bày nhanh. " if team.nickname(3) else "Phần giao diện nhóm em đi nhanh. ")
        + "Đây là 4 ảnh chụp thật: trang Phim bộ lọc "
        "6 thể loại, trang Kho phim tìm khủng long ra 9 tập Doraemon, trang chi tiết có chấm điểm trực tiếp "
        "và bản di động 390 nhân 844 pixel không tràn ngang ạ."
    ))

    # =========================================================================
    # SLIDE 14: KẾT LUẬN VÀ LỘ TRÌNH DEMO
    # =========================================================================
    s14 = prs.slides.add_slide(blank)
    add_header_footer(s14, "13 · Tổng kết", "Tổng kết và trình diễn ứng dụng trực tiếp", 14)

    add_bullets(s14, Inches(0.60), Inches(1.44), Inches(6.58), Inches(4.42), [
        "Hoàn thành web cho gia đình (6 bộ, 806 tập) và ứng dụng desktop Dippie Admin dùng chung CSDL.",
        "Áp dụng trực tiếp Java 21, OOP, MVC, JavaFX 21, JDBC và các mẫu thiết kế GoF vào mã nguồn.",
        "589 ca test backend (1 ca bỏ qua trên Windows), 286 ca frontend, 33 ca desktop, chạy thử cuối 36/36; UX đạt 8,2/10.",
        "Hướng mở rộng: hồ sơ trẻ em có mã PIN phụ huynh, phụ đề, giảm phụ thuộc YouTube và đủ năm phát hành.",
    ], size=14.6, space_after=14, line_spacing=1.20)

    add_rect(s14, Inches(7.44), Inches(1.40), Inches(5.29), Inches(4.48),
             fill_color=MINT_SOFT, border_color=SAGE_DARK, border_pt=1.4, rounded=True)
    add_textbox(s14, Inches(7.68), Inches(1.56), Inches(4.82), Inches(0.38),
                "LỘ TRÌNH DEMO TRỰC TIẾP (5 PHÚT)", size=15.0, bold=True, color=SAGE_DARK, valign=MSO_ANCHOR.MIDDLE)
    add_bullets(s14, Inches(7.68), Inches(2.04), Inches(4.82), Inches(3.68), [
        "1. Đăng nhập user, xem mục Xem tiếp và thử 6 tâm trạng.",
        "2. Tìm kiếm không dấu, lỗi gõ và chấm điểm 1 đến 10.",
        "3. Hỏi CineBot 'Cảnh Red đội kem', mở đúng mốc 00:33.",
        "4. Đăng nhập admin, mở Thống kê và Nhật ký kiểm toán.",
        "5. Mở Dippie Admin: Danh mục, Hoàn tác và Xuất CSV.",
    ], size=13.5, space_after=10, line_spacing=1.18, bullet_char="")

    add_banner(
        s14, Inches(0.60), Inches(6.08), Inches(12.13), Inches(0.76),
        "Xin cảm ơn Thầy/Cô và các bạn đã lắng nghe · Mời chuyển sang phần Demo và Hỏi đáp",
        fill=SAGE_DARK, border=SAGE_DARK, color=PAPER, size=17.0, bold=True, rounded=False
    )

    set_notes(s14, (
        "Dạ, nhóm em đã hoàn thiện cả bản Web lẫn Dippie Admin JavaFX dùng chung CSDL, áp dụng OOP, MVC "
        "và GoF trong mã nguồn với điểm UX 8,2 trên 10. Ngay sau đây nhóm xin demo trực tiếp theo 5 bước "
        "bên phải. Em cảm ơn thầy và các bạn đã lắng nghe ạ."
    ))

    OUT_PPTX.parent.mkdir(parents=True, exist_ok=True)
    prs.save(str(OUT_PPTX))
    print(f"Đã tạo slide thuyết trình: {OUT_PPTX} ({OUT_PPTX.stat().st_size:,} bytes), số slide: {len(prs.slides)}")


if __name__ == "__main__":
    build_slides()
