# -*- coding: utf-8 -*-
"""Tạo báo cáo Bài tập lớn Dippie (Bao_cao_BTL_Dippie.docx) bằng python-docx."""

import re
from pathlib import Path
import docx
from docx.shared import Inches, Pt, Mm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import qn, nsdecls

import team_info as team

BASE_DIR = Path(__file__).resolve().parent
ASSETS_DIR = BASE_DIR / "assets"
SCREENSHOTS_DIR = ASSETS_DIR / "screenshots"
DESKTOP_SHOTS_DIR = SCREENSHOTS_DIR / "desktop"
OUT_DOCX = BASE_DIR / "Bao_cao_BTL_Dippie.docx"

# Báo cáo nộp giảng viên nên để chữ đen, bảng kẻ mảnh, tiêu đề cột tô xám nhạt (theo mẫu báo cáo CNPM của nhóm).
CLR_PRIMARY = RGBColor(0x00, 0x00, 0x00)
CLR_INK = RGBColor(0x00, 0x00, 0x00)
CLR_MUTED = RGBColor(0x40, 0x40, 0x40)
CLR_WARN = RGBColor(0x00, 0x00, 0x00)

HEX_HEADER_BG = "E7E6E6"
HEX_ALT_ROW = "FFFFFF"
HEX_BORDER = "000000"
HEX_CODE_BG = "F2F2F2"
HEX_PLACEHOLDER_BG = "FFFFFF"
HEX_PLACEHOLDER_BORDER = "000000"
LOGO_PATH = BASE_DIR / "assets" / "logo-truong.png"
FIGURE_CAPTION_STYLE = "Chu thich hinh"
TABLE_CAPTION_STYLE = "Chu thich bang"

# ---------------------------------------------------------------------------
# Tiện ích định dạng XML cho python-docx 1.2.0
# ---------------------------------------------------------------------------

def set_run_font(run, name="Times New Roman", size_pt=13, bold=False, italic=False, color=CLR_INK):
    run.font.name = name
    run.font.size = Pt(size_pt)
    run.bold = bold
    run.italic = italic
    if color is not None:
        run.font.color.rgb = color
    rPr = run._r.get_or_add_rPr()
    rFonts = rPr.find(qn("w:rFonts"))
    if rFonts is None:
        rFonts = OxmlElement("w:rFonts")
        rPr.insert(0, rFonts)
    for attr in ("w:ascii", "w:hAnsi", "w:cs", "w:eastAsia"):
        rFonts.set(qn(attr), name)


def set_cell_shading(cell, fill_hex):
    tcPr = cell._tc.get_or_add_tcPr()
    for old in tcPr.findall(qn("w:shd")):
        tcPr.remove(old)
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{fill_hex}"/>')
    tcPr.append(shd)


def set_cell_margins(cell, top=80, bottom=80, left=110, right=110):
    tcPr = cell._tc.get_or_add_tcPr()
    for old in tcPr.findall(qn("w:tcMar")):
        tcPr.remove(old)
    tcMar = OxmlElement("w:tcMar")
    for side, val in (("top", top), ("bottom", bottom), ("left", left), ("right", right)):
        node = OxmlElement(f"w:{side}")
        node.set(qn("w:w"), str(val))
        node.set(qn("w:type"), "dxa")
        tcMar.append(node)
    tcPr.append(tcMar)


def set_table_borders(table, color=HEX_BORDER, sz="4", val="single"):
    tblPr = table._tbl.tblPr
    for old in tblPr.findall(qn("w:tblBorders")):
        tblPr.remove(old)
    borders = parse_xml(
        f'<w:tblBorders {nsdecls("w")}>'
        f'<w:top w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'<w:bottom w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'<w:left w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'<w:right w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'<w:insideH w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'<w:insideV w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'</w:tblBorders>'
    )
    tblPr.append(borders)


def mark_repeat_header(row):
    trPr = row._tr.get_or_add_trPr()
    trPr.append(OxmlElement("w:tblHeader"))


def prevent_row_split(row):
    trPr = row._tr.get_or_add_trPr()
    trPr.append(OxmlElement("w:cantSplit"))


def mm_to_dxa(mm_val):
    return int(round(mm_val * 56.6929133858))


def set_table_col_widths(table, col_widths_mm):
    """Ép chính xác độ rộng bảng và từng cột (tblW + tblLayout=fixed + tblGrid + tcW) để không tràn trang."""
    table.autofit = False
    dxa_list = [mm_to_dxa(w) for w in col_widths_mm]
    total_dxa = sum(dxa_list)

    tblPr = table._tbl.tblPr
    for old in tblPr.findall(qn("w:tblW")):
        tblPr.remove(old)
    tblW = OxmlElement("w:tblW")
    tblW.set(qn("w:w"), str(total_dxa))
    tblW.set(qn("w:type"), "dxa")
    tblPr.append(tblW)

    for old in tblPr.findall(qn("w:tblLayout")):
        tblPr.remove(old)
    tblLayout = OxmlElement("w:tblLayout")
    tblLayout.set(qn("w:type"), "fixed")
    tblPr.append(tblLayout)

    tblGrid = table._tbl.tblGrid
    if tblGrid is not None:
        for old in list(tblGrid.findall(qn("w:gridCol"))):
            tblGrid.remove(old)
        for dxa in dxa_list:
            gc = OxmlElement("w:gridCol")
            gc.set(qn("w:w"), str(dxa))
            tblGrid.append(gc)

    for ci, w_mm in enumerate(col_widths_mm):
        table.columns[ci].width = Mm(w_mm)

    for row in table.rows:
        for ci, dxa in enumerate(dxa_list):
            cell = row.cells[ci]
            tcPr = cell._tc.get_or_add_tcPr()
            for old in tcPr.findall(qn("w:tcW")):
                tcPr.remove(old)
            tcW = OxmlElement("w:tcW")
            tcW.set(qn("w:w"), str(dxa))
            tcW.set(qn("w:type"), "dxa")
            tcPr.append(tcW)


def add_field(paragraph, instr_text, placeholder_text="", size_pt=12, italic=False, color=CLR_INK):
    r_begin = paragraph.add_run()
    set_run_font(r_begin, size_pt=size_pt, italic=italic, color=color)
    fld_begin = OxmlElement("w:fldChar")
    fld_begin.set(qn("w:fldCharType"), "begin")
    fld_begin.set(qn("w:dirty"), "true")
    r_begin._r.append(fld_begin)

    r_instr = paragraph.add_run()
    set_run_font(r_instr, size_pt=size_pt, italic=italic, color=color)
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = f" {instr_text} "
    r_instr._r.append(instr)

    r_sep = paragraph.add_run()
    set_run_font(r_sep, size_pt=size_pt, italic=italic, color=color)
    fld_sep = OxmlElement("w:fldChar")
    fld_sep.set(qn("w:fldCharType"), "separate")
    r_sep._r.append(fld_sep)

    if placeholder_text:
        r_val = paragraph.add_run(placeholder_text)
        set_run_font(r_val, size_pt=size_pt, italic=italic, color=color)

    r_end = paragraph.add_run()
    set_run_font(r_end, size_pt=size_pt, italic=italic, color=color)
    fld_end = OxmlElement("w:fldChar")
    fld_end.set(qn("w:fldCharType"), "end")
    r_end._r.append(fld_end)


def add_bottom_border_to_paragraph(paragraph, color="36563C", sz="12"):
    pPr = paragraph._p.get_or_add_pPr()
    pBdr = parse_xml(
        f'<w:pBdr {nsdecls("w")}>'
        f'<w:bottom w:val="single" w:sz="{sz}" w:space="4" w:color="{color}"/>'
        f'</w:pBdr>'
    )
    pPr.append(pBdr)


# ---------------------------------------------------------------------------
# Lớp dựng báo cáo có theo dõi danh mục Hình và Bảng
# ---------------------------------------------------------------------------

class ReportBuilder:
    def __init__(self):
        self.doc = docx.Document()
        self.figures = []   # [(num, caption)]
        self.tables = []    # [(num, caption)]
        self.fig_list_anchor = None
        self.tbl_list_anchor = None
        self.chapter = None
        self.fig_in_chapter = 0
        self.tbl_in_chapter = 0
        self._configure_document()

    def _configure_document(self):
        # Không đặt w:updateFields=true ở mức settings để tránh hộp thoại chặn khi xuất PDF qua Word COM;
        # mục lục và số trang được cập nhật bằng Fields.Update() hoặc Ctrl+A rồi F9 trong Word.
        section = self.doc.sections[0]
        section.page_width = Mm(210)
        section.page_height = Mm(297)
        section.top_margin = Mm(25)
        section.bottom_margin = Mm(25)
        section.left_margin = Mm(30)
        section.right_margin = Mm(20)
        section.different_first_page_header_footer = True

        normal = self.doc.styles["Normal"]
        normal.font.name = "Times New Roman"
        normal.font.size = Pt(13)
        normal.font.color.rgb = CLR_INK
        rPr = normal.element.get_or_add_rPr()
        rFonts = rPr.find(qn("w:rFonts"))
        if rFonts is None:
            rFonts = OxmlElement("w:rFonts")
            rPr.insert(0, rFonts)
        for attr in ("w:ascii", "w:hAnsi", "w:cs", "w:eastAsia"):
            rFonts.set(qn(attr), "Times New Roman")
        normal.paragraph_format.line_spacing = 1.5
        normal.paragraph_format.space_before = Pt(0)
        normal.paragraph_format.space_after = Pt(6)
        normal.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY

        # Chương căn giữa cỡ 16, mục 1.1 cỡ 14, mục 1.1.1 cỡ 13, đều đậm và đen như báo cáo CNPM.
        for lvl, size in ((1, 16), (2, 14), (3, 13)):
            st = self.doc.styles[f"Heading {lvl}"]
            st.font.name = "Times New Roman"
            st.font.size = Pt(size)
            st.font.bold = True
            st.font.italic = False
            st.font.color.rgb = CLR_INK
            st.paragraph_format.space_before = Pt(12 if lvl == 1 else 10)
            st.paragraph_format.space_after = Pt(10 if lvl == 1 else 6)
            st.paragraph_format.line_spacing = 1.3
            st.paragraph_format.keep_with_next = True
            st.paragraph_format.keep_together = True
            srPr = st.element.get_or_add_rPr()
            srFonts = srPr.find(qn("w:rFonts"))
            if srFonts is None:
                srFonts = OxmlElement("w:rFonts")
                srPr.insert(0, srFonts)
            # Xoá font theo theme, nếu không Word vẫn hiện tiêu đề bằng Calibri Light.
            for attr in ("w:asciiTheme", "w:hAnsiTheme", "w:eastAsiaTheme", "w:cstheme"):
                if srFonts.get(qn(attr)) is not None:
                    del srFonts.attrib[qn(attr)]
            for attr in ("w:ascii", "w:hAnsi", "w:cs", "w:eastAsia"):
                srFonts.set(qn(attr), "Times New Roman")

        # Hai kiểu chú thích riêng để Word gom thành danh mục hình và danh mục bảng có số trang.
        from docx.enum.style import WD_STYLE_TYPE
        for style_name in (FIGURE_CAPTION_STYLE, TABLE_CAPTION_STYLE):
            cst = self.doc.styles.add_style(style_name, WD_STYLE_TYPE.PARAGRAPH)
            cst.base_style = normal
            cst.font.size = Pt(12)
            cst.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
            cst.paragraph_format.line_spacing = 1.2
            cst.paragraph_format.first_line_indent = Pt(0)

        # Không có dòng đầu trang; chân trang chỉ có số trang ở giữa, trang bìa không đánh số.
        footer = section.footer
        fp = footer.paragraphs[0]
        fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        fp.paragraph_format.line_spacing = 1.0
        add_field(fp, "PAGE", "1", size_pt=12, color=CLR_INK)

    def page_break(self):
        self.doc.add_page_break()

    def h1(self, text, break_before=True):
        if break_before:
            self.doc.add_page_break()
        # Hình và bảng đánh số lại theo chương: Hình 1.1, Bảng 3.2; phần phụ lục dùng PL.1.
        found = re.match(r"Chương (\d+)", text)
        self.chapter = found.group(1) if found else ("PL" if text.startswith("Phụ lục") else None)
        self.fig_in_chapter = 0
        self.tbl_in_chapter = 0
        p = self.doc.add_heading(text, level=1)
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        return p

    def _number(self, kind):
        if kind == "fig":
            self.fig_in_chapter += 1
            n = self.fig_in_chapter
        else:
            self.tbl_in_chapter += 1
            n = self.tbl_in_chapter
        return f"{self.chapter}.{n}" if self.chapter else str(n)

    def h2(self, text):
        p = self.doc.add_heading(text, level=2)
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        return p

    def h3(self, text):
        p = self.doc.add_heading(text, level=3)
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        return p

    def p(self, text, bold_prefix=None, italic=False, align=WD_ALIGN_PARAGRAPH.JUSTIFY, space_after=6, keep_with_next=False):
        para = self.doc.add_paragraph()
        para.alignment = align
        para.paragraph_format.line_spacing = 1.5
        para.paragraph_format.space_before = Pt(0)
        para.paragraph_format.space_after = Pt(space_after)
        para.paragraph_format.keep_with_next = keep_with_next
        if align == WD_ALIGN_PARAGRAPH.JUSTIFY:
            para.paragraph_format.first_line_indent = Mm(10)
        if bold_prefix:
            r0 = para.add_run(bold_prefix)
            set_run_font(r0, size_pt=13, bold=True, color=CLR_INK)
        r1 = para.add_run(text)
        set_run_font(r1, size_pt=13, italic=italic, color=CLR_INK)
        return para

    def bullet(self, text, bold_prefix=None):
        para = self.doc.add_paragraph(style="List Bullet")
        para.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
        para.paragraph_format.line_spacing = 1.5
        para.paragraph_format.space_before = Pt(0)
        para.paragraph_format.space_after = Pt(4)
        if bold_prefix:
            r0 = para.add_run(bold_prefix)
            set_run_font(r0, size_pt=13, bold=True, color=CLR_INK)
        r1 = para.add_run(text)
        set_run_font(r1, size_pt=13, color=CLR_INK)
        return para

    def code_block(self, title, code_text, font_size=9.5):
        p_lbl = self.doc.add_paragraph()
        p_lbl.alignment = WD_ALIGN_PARAGRAPH.LEFT
        p_lbl.paragraph_format.space_before = Pt(6)
        p_lbl.paragraph_format.space_after = Pt(2)
        p_lbl.paragraph_format.keep_with_next = True
        rl = p_lbl.add_run(title)
        set_run_font(rl, size_pt=12, bold=True, italic=True, color=CLR_INK)

        tbl = self.doc.add_table(rows=1, cols=1)
        tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
        set_table_col_widths(tbl, [160.0])
        cell = tbl.cell(0, 0)
        set_cell_shading(cell, HEX_CODE_BG)
        set_cell_margins(cell, top=90, bottom=90, left=140, right=120)
        set_table_borders(tbl, color=HEX_BORDER, sz="4")
        prevent_row_split(tbl.rows[0])

        lines = code_text.strip("\n").split("\n")
        for idx, line in enumerate(lines):
            cp = cell.paragraphs[0] if idx == 0 else cell.add_paragraph()
            cp.alignment = WD_ALIGN_PARAGRAPH.LEFT
            cp.paragraph_format.line_spacing = 1.15
            cp.paragraph_format.space_before = Pt(0)
            cp.paragraph_format.space_after = Pt(0)
            cr = cp.add_run(line)
            set_run_font(cr, name="Consolas", size_pt=font_size, color=CLR_INK)

        sp = self.doc.add_paragraph()
        sp.paragraph_format.space_after = Pt(4)
        sp.paragraph_format.line_spacing = 1.0

    def table(self, caption, headers, rows, col_widths_mm=None, font_size=11):
        t_num = self._number("tbl")
        self.tables.append((t_num, caption))

        p_cap = self.doc.add_paragraph(style=TABLE_CAPTION_STYLE)
        p_cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_cap.paragraph_format.space_before = Pt(8)
        p_cap.paragraph_format.space_after = Pt(4)
        p_cap.paragraph_format.line_spacing = 1.2
        p_cap.paragraph_format.keep_with_next = True
        r1 = p_cap.add_run(f"Bảng {t_num}. ")
        set_run_font(r1, size_pt=12, bold=True, color=CLR_INK)
        r2 = p_cap.add_run(caption)
        set_run_font(r2, size_pt=12, italic=True, color=CLR_INK)

        tbl = self.doc.add_table(rows=1 + len(rows), cols=len(headers))
        tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
        set_table_borders(tbl, color=HEX_BORDER, sz="4")

        total_mm = 160.0
        if col_widths_mm is None:
            col_widths_mm = [total_mm / len(headers)] * len(headers)
        elif sum(col_widths_mm) > total_mm:
            scale = total_mm / sum(col_widths_mm)
            col_widths_mm = [w * scale for w in col_widths_mm]
        set_table_col_widths(tbl, col_widths_mm)

        hdr_row = tbl.rows[0]
        mark_repeat_header(hdr_row)
        prevent_row_split(hdr_row)
        for ci, h_text in enumerate(headers):
            cell = hdr_row.cells[ci]
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            set_cell_shading(cell, HEX_HEADER_BG)
            set_cell_margins(cell, top=75, bottom=75, left=85, right=85)
            cp = cell.paragraphs[0]
            cp.alignment = WD_ALIGN_PARAGRAPH.CENTER
            cp.paragraph_format.line_spacing = 1.15
            cp.paragraph_format.space_before = Pt(0)
            cp.paragraph_format.space_after = Pt(0)
            cp.paragraph_format.keep_with_next = True
            cr = cp.add_run(h_text)
            set_run_font(cr, size_pt=font_size, bold=True, color=CLR_INK)

        for ri, row_data in enumerate(rows):
            row = tbl.rows[ri + 1]
            prevent_row_split(row)
            bg = HEX_ALT_ROW if ri % 2 == 1 else "FFFFFF"
            for ci, val in enumerate(row_data):
                cell = row.cells[ci]
                cell.vertical_alignment = WD_ALIGN_VERTICAL.TOP
                set_cell_shading(cell, bg)
                set_cell_margins(cell, top=60, bottom=60, left=85, right=85)
                cp = cell.paragraphs[0]
                cp.alignment = WD_ALIGN_PARAGRAPH.LEFT
                cp.paragraph_format.line_spacing = 1.18
                cp.paragraph_format.space_before = Pt(0)
                cp.paragraph_format.space_after = Pt(0)
                cr = cp.add_run(str(val))
                set_run_font(cr, size_pt=font_size, bold=(ci == 0 and len(headers) >= 3 and len(str(val)) <= 28), color=CLR_INK)

        sp = self.doc.add_paragraph()
        sp.paragraph_format.space_after = Pt(4)
        sp.paragraph_format.line_spacing = 1.0
        return tbl

    def figure(self, image_path, caption, width_inches=5.9):
        f_num = self._number("fig")
        self.figures.append((f_num, caption))

        p_img = self.doc.add_paragraph()
        p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_img.paragraph_format.space_before = Pt(8)
        p_img.paragraph_format.space_after = Pt(3)
        p_img.paragraph_format.line_spacing = 1.0
        p_img.paragraph_format.keep_with_next = True
        run = p_img.add_run()
        run.add_picture(str(image_path), width=Inches(width_inches))

        p_cap = self.doc.add_paragraph(style=FIGURE_CAPTION_STYLE)
        p_cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_cap.paragraph_format.space_before = Pt(2)
        p_cap.paragraph_format.space_after = Pt(10)
        p_cap.paragraph_format.line_spacing = 1.2
        r1 = p_cap.add_run(f"Hình {f_num}. ")
        set_run_font(r1, size_pt=12, bold=True, color=CLR_INK)
        r2 = p_cap.add_run(caption)
        set_run_font(r2, size_pt=12, italic=True, color=CLR_INK)

    def build_cover_page(self):
        """Trang bìa giống bài CNPM: trường, khoa, logo, tên báo cáo, lớp, giảng viên, bảng thành viên, nơi và tháng."""
        def center_line(text, size=13, bold=True, sa=6, sb=0):
            p = self.doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.line_spacing = 1.3
            p.paragraph_format.first_line_indent = Pt(0)
            p.paragraph_format.space_before = Pt(sb)
            p.paragraph_format.space_after = Pt(sa)
            set_run_font(p.add_run(text), size_pt=size, bold=bold, color=CLR_INK)
            return p

        center_line(team.field("truong"), sa=4)
        center_line(team.field("khoa"), sa=10)
        if LOGO_PATH.exists():
            p_logo = self.doc.add_paragraph()
            p_logo.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p_logo.paragraph_format.space_after = Pt(14)
            p_logo.add_run().add_picture(str(LOGO_PATH), width=Mm(30))

        center_line("Báo cáo bài tập lớn", size=20, sb=6, sa=10)
        center_line(team.field("hoc_phan", "Lập trình hướng đối tượng"), sa=6)
        center_line("Dippie: website xem và gợi ý phim hoạt hình cho gia đình", sa=6)
        center_line(f"Mã học phần {team.field('ma_hoc_phan', '................')}", sa=6)
        center_line(f"Lớp {team.field('lop', '................')}", sa=6)
        center_line(f"Giảng viên hướng dẫn: {team.field('giang_vien', '................................')}", sa=14)

        mem_tbl = self.doc.add_table(rows=5, cols=2)
        mem_tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
        set_table_borders(mem_tbl, color="BFBFBF", sz="4")
        set_table_col_widths(mem_tbl, [80.0, 70.0])
        rows = [("Họ và tên", "Mã sinh viên")] + [(team.full_name(n), team.student_id(n)) for n in range(1, 5)]
        for ri, (name, sid) in enumerate(rows):
            row = mem_tbl.rows[ri]
            prevent_row_split(row)
            for ci, val in enumerate((name, sid)):
                c = row.cells[ci]
                if ri == 0:
                    set_cell_shading(c, HEX_HEADER_BG)
                set_cell_margins(c, top=70, bottom=70, left=90, right=90)
                cp = c.paragraphs[0]
                cp.alignment = WD_ALIGN_PARAGRAPH.CENTER
                cp.paragraph_format.first_line_indent = Pt(0)
                cp.paragraph_format.line_spacing = 1.2
                cp.paragraph_format.space_after = Pt(0)
                set_run_font(cp.add_run(val), size_pt=13, bold=(ri == 0), color=CLR_INK)

        # Dòng nơi và tháng nằm ở chân trang của riêng trang bìa nên luôn sát đáy trang.
        first_footer = self.doc.sections[0].first_page_footer
        fp = first_footer.paragraphs[0]
        fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        set_run_font(fp.add_run(f"{team.field('noi_lam', '................')}, tháng 11 năm 2026"), size_pt=13, color=CLR_INK)


    def _front_title(self, text, page_break=True):
        if page_break:
            self.doc.add_page_break()
        p = self.doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.first_line_indent = Pt(0)
        p.paragraph_format.space_after = Pt(12)
        set_run_font(p.add_run(text), size_pt=16, bold=True, color=CLR_INK)
        return p

    def build_front_matter(self):
        self._front_title("Mục lục")
        p_toc = self.doc.add_paragraph()
        p_toc.alignment = WD_ALIGN_PARAGRAPH.LEFT
        p_toc.paragraph_format.line_spacing = 1.35
        add_field(
            p_toc,
            'TOC \\o "1-3" \\h \\z \\u',
            "Mở bằng Word rồi bấm Ctrl+A, F9 để hiện mục lục có số trang.",
            size_pt=12, italic=True, color=CLR_MUTED
        )

        self._front_title("Danh mục hình vẽ")
        self.fig_list_anchor = self.doc.add_paragraph()
        self._front_title("Danh mục bảng")
        self.tbl_list_anchor = self.doc.add_paragraph()

    def _fill_list(self, anchor, style_name, prefix, items):
        """Danh mục là trường TOC của Word gom theo kiểu chú thích, nên có số trang sau khi cập nhật.
        Trước khi cập nhật vẫn hiện sẵn danh sách tên hình, bảng."""
        anchor.alignment = WD_ALIGN_PARAGRAPH.LEFT
        anchor.paragraph_format.line_spacing = 1.35
        anchor.paragraph_format.first_line_indent = Pt(0)
        preview = "\n".join(f"{prefix} {num}. {cap}" for num, cap in items)
        add_field(anchor, f'TOC \\h \\z \\t "{style_name},1"', preview, size_pt=12, color=CLR_INK)

    def populate_lists_of_figures_and_tables(self):
        if self.fig_list_anchor is not None:
            self._fill_list(self.fig_list_anchor, FIGURE_CAPTION_STYLE, "Hình", self.figures)
        if self.tbl_list_anchor is not None:
            self._fill_list(self.tbl_list_anchor, TABLE_CAPTION_STYLE, "Bảng", self.tables)

    def save(self, path=OUT_DOCX):
        self.populate_lists_of_figures_and_tables()
        path.parent.mkdir(parents=True, exist_ok=True)
        self.doc.save(str(path))
        return path


# ---------------------------------------------------------------------------
# Xây dựng toàn bộ nội dung báo cáo
# ---------------------------------------------------------------------------

def build_report():
    required_imgs = [
        "arch_diagram.png", "usecase_diagram.png", "ux_audit_chart.png",
        "cinebot_flow.png", "db_overview.png",
    ]
    if not all((ASSETS_DIR / name).exists() for name in required_imgs):
        import generate_diagrams
        generate_diagrams.generate_all()

    rb = ReportBuilder()
    rb.build_cover_page()
    rb.build_front_matter()

    # =======================================================================
    # LỜI MỞ ĐẦU
    # =======================================================================
    rb.h1("Lời mở đầu", break_before=True)
    rb.p(
        "Nhiều gia đình có trẻ nhỏ thường mở hoạt hình tiếng Việt cho con xem, nhưng nguồn phim trên mạng lại nằm "
        "rải rác ở nhiều kênh YouTube, danh sách phát hoặc thư mục cá nhân khác nhau. Phụ huynh hay gặp khó khi muốn "
        "tìm nhanh một tập ngắn trước giờ đi ngủ, nhớ lại đoạn bé đang xem dở hôm trước, hoặc tìm đúng tập khi bé chỉ "
        "nhớ một chi tiết nhỏ như \"cảnh Red đội kem\" hay \"Nobita đi vũ trụ\"."
    )
    rb.p(
        "Để giải quyết bài toán này và vận dụng kiến thức môn Lập trình hướng đối tượng Java, nhóm chúng em gồm 4 thành "
        "viên đã thiết kế và cài đặt hệ thống Dippie, gồm website xem và gợi ý phim hoạt hình cho gia đình cùng ứng dụng "
        "quản trị desktop Dippie Admin viết bằng JavaFX 21 và JDBC thuần. Toàn bộ phần web chạy trong một tiến trình "
        "Java 21 với Spring Boot 3.5 trên cổng 3000, xử lý luôn API nghiệp vụ, xác thực JWT, truyền phát video HTTP "
        "Byte Range, tìm kiếm BM25 trong bộ nhớ, trợ lý hội thoại CineBot qua SSE và phục vụ giao diện React 19."
    )
    rb.p(
        "Bộ dữ liệu thử nghiệm của Dippie hiện có 6 bộ hoạt hình quen thuộc với 20 mùa, 806 tập phim, 1.705 phân đoạn "
        "có mốc thời gian và 51 hồ sơ nhân vật (Doraemon, Công chúa Ori, Ấu trùng tinh nghịch Larva, Xin Chào Jadoo, "
        "Heo Peppa và Trạng Quỳnh Thời Nhí Nhố). Sau 3 vòng kiểm thử trải nghiệm người dùng, điểm đánh giá trung bình "
        "tăng từ 3,2/10 lên 6,8/10 và đạt 8,2/10. Kho mã nguồn cuối có 589 ca kiểm thử JUnit Surefire ở backend "
        "Spring Boot (1 ca bỏ qua trên Windows do tạo liên kết tượng trưng cần quyền quản trị viên), 286 ca kiểm thử "
        "hành vi ở frontend React và 33 ca kiểm thử JUnit 5 ở ứng dụng desktop Dippie Admin. Ở phép kiểm thử khói "
        "tổng thể trên bản dựng chạy thử cuối cùng, toàn bộ 36/36 bước kiểm tra đều đạt."
    )

    # =======================================================================
    # CHƯƠNG 1. GIỚI THIỆU CÔNG NGHỆ
    # =======================================================================
    rb.h1("Chương 1. Giới thiệu công nghệ", break_before=True)
    rb.p(
        "Trong đề tài này, nhóm chúng em kết hợp phần nền tảng Java học trên lớp (Java 21, JavaFX 21, JDBC, các mẫu "
        "thiết kế GoF) với một số công nghệ web và vận hành thực tế gồm Spring Boot 3.5, React 19, TypeScript, "
        "Tailwind CSS, Server-Sent Events, tìm kiếm BM25 cục bộ kèm tùy chọn DeepSeek LLM, Docker Compose, MySQL 8.4, "
        "Caddy HTTPS và GitHub Actions CI."
    )

    rb.h2("1.1. Các công nghệ lõi phía backend và desktop Java")

    rb.h3("1.1.1. Java 21 (LTS)")
    rb.p(
        "Nhóm chọn Java 21 vì đây là bản hỗ trợ dài hạn (LTS) có nhiều cú pháp mới gọn và an toàn kiểu hơn so với các "
        "bản Java cũ trong giáo trình. Trong 333 tệp mã nguồn chính (24.416 dòng gồm 23.140 dòng không tính chú thích "
        "và 1.276 dòng chú thích tiếng Việt) của backend Spring Boot, chúng em dùng trực tiếp các tính năng sau:"
    )
    rb.bullet(
        "Java Records xuất hiện trong 145 tệp để định nghĩa DTO và đối tượng giá trị bất biến như ApiResponse<T>, "
        "ByteRange, WatchDuration, SearchHit, ChatIntent và Viewer. Cách viết này bỏ được phần getter, equals và "
        "hashCode lặp lại mà không cần cài thêm Lombok."
    )
    rb.bullet(
        "Text Blocks xuất hiện trong 17 tệp cho các câu truy vấn JPQL nhiều dòng (AdminStatsQuery, "
        "CatalogEpisodeRepository, MovieRatingRepository) và chuỗi JSON mẫu, dễ đọc và đỡ sai ký tự thoát."
    )
    rb.bullet(
        "Pattern Matching cho instanceof (14 tệp) và biểu thức switch dạng mũi tên (12 tệp) được dùng trong "
        "AccessPolicy, PublicationStatus, MovieQueryService, CatalogImportService, LocalChatController và "
        "PlaybackContextResolver để xử lý rẽ nhánh rõ ràng."
    )
    rb.bullet(
        "Sealed Types được áp dụng ở cả hai module Java. Ở backend có 4 sealed interface là ChatEvent (Metadata, "
        "Token, Done, Error cho luồng SSE), PlaybackPrincipal (Authenticated, Anonymous cho quyền phát video), "
        "PlaybackContextResolver.PlaybackAssetId (Youtube, File) và HealthStatus (UpStatus, DownStatus cho kiểm "
        "tra sức khỏe hệ thống). Ở module desktop có sealed interface CatalogNode cho phép đúng 3 nhánh SeriesNode, "
        "SeasonNode và EpisodeNode."
    )
    rb.bullet(
        "Nhóm không bật Virtual Threads vì các truy vấn H2/MySQL cục bộ và tìm kiếm BM25 trong RAM đều hoàn tất "
        "trong khoảng 3 đến 43 ms trên mô hình luồng servlet mặc định kết hợp AtomicReference và ReentrantLock."
    )

    rb.h3("1.1.2. Spring Boot 3.5 và hệ sinh thái Spring")
    rb.p(
        "Spring Boot 3.5.16 giúp nhóm gom toàn bộ REST API, xác thực, truy cập cơ sở dữ liệu, luồng SSE và tệp tĩnh "
        "của React SPA vào chung một tệp JAR thực thi. Trong quá trình cài đặt, chúng em dùng các module chính gồm "
        "spring-boot-starter-web (25 lớp Controller cho REST API, StreamingResponseBody ở CineBot và định tuyến SPA), "
        "spring-boot-starter-security kết hợp thư viện jjwt 0.13.0 (JWT HS256, BCrypt và phân quyền ROLE_USER, "
        "ROLE_ADMIN), spring-boot-starter-data-jpa với Hibernate ORM (hỗ trợ JpaSpecificationExecutor và Criteria API "
        "để lọc, sắp xếp và phân trang trực tiếp bằng SQL), Flyway cho migration lược đồ trên MySQL, "
        "spring-boot-starter-validation cùng springdoc-openapi-starter-webmvc-ui 2.8.17 (chỉ bật Swagger UI tại "
        "/swagger-ui.html trong hồ sơ demo, mặc định tắt ở các môi trường khác) và bộ kiểm thử JUnit 5 với MockMvc "
        "gồm 69 lớp (70 tệp) với 589 ca Surefire (1 ca bỏ qua trên Windows do symlink cần quyền quản trị)."
    )

    rb.h3("1.1.3. Spring Security và JWT có phiên bản hóa")
    rb.p(
        "Hệ thống xác thực người dùng theo kiểu phi trạng thái bằng JSON Web Token ký thuật toán HS256. Mỗi token "
        "chứa tên đăng nhập (sub), thời điểm phát hành (iat), hạn dùng mặc định 24 giờ và số phiên bản token (ver, "
        "khớp cột users.token_version). Khi người dùng bấm đăng xuất (POST /api/auth/logout qua "
        "SessionLogoutController), đổi mật khẩu, đặt lại mật khẩu hoặc khi quản trị viên khóa tài khoản hay đổi vai "
        "trò, token_version tăng thêm 1 nên mọi token cũ lập tức hết hiệu lực. Riêng luồng phát tệp video dùng vé "
        "JWT ngắn hạn sống 5 phút (type=playback_grant) tách riêng với token đăng nhập. Chuỗi lọc trong SecurityConfig "
        "chạy theo nguyên tắc mặc định từ chối (fail-closed), gắn sẵn các header bảo mật (CSP, HSTS, Referrer-Policy, "
        "Permissions-Policy, Cache-Control: no-store cho API nhạy cảm), đặt RequestBodySizeLimitFilter chặn thân yêu "
        "cầu vượt ngưỡng (64 KB mặc định, 256 KB cho /api/chat, 25 MB cho nhập danh mục, trả HTTP 413), kiểm tra mật "
        "khẩu bằng PasswordPolicy (tối thiểu 6 ký tự cho người dùng, 16 ký tự cho quản trị viên, tối đa 72 byte "
        "UTF-8) và áp dụng giới hạn tần suất qua LoginRateLimiter và FixedWindowRateLimiter theo IP thực trích bởi "
        "ClientIpResolver (chỉ tin X-Forwarded-For từ dải proxy tin cậy)."
    )

    rb.h3("1.1.4. Cơ sở dữ liệu kép: H2 khi demo và MySQL 8.4 khi chạy thật")
    rb.p(
        "Dippie chạy được trên cả H2 và MySQL thông qua Spring Profiles với cùng một mô hình thực thể JPA, đồng nhất "
        "múi giờ UTC cho cả Jackson lẫn kết nối JDBC/Hibernate. Ứng dụng không tự bật hồ sơ demo khi thiếu cấu hình "
        "để tránh mở nhầm dữ liệu mẫu. Ở chế độ demo cục bộ, H2 mở chế độ tương thích MySQL "
        "(MODE=MySQL;AUTO_SERVER=TRUE) và lưu tệp tại target/dippie-java/dippie để Spring Boot và ứng dụng desktop "
        "Dippie Admin truy cập cùng lúc vào chung tệp cơ sở dữ liệu. Khi chuyển sang hồ sơ mysql hoặc prod, hệ thống "
        "kết nối MySQL 8.0/8.4 có bật TLS (sslMode=REQUIRED trên prod, tắt allowPublicKeyRetrieval), chạy Flyway "
        "migration từ V1 đến V5 và giữ spring.jpa.hibernate.ddl-auto=validate để đối chiếu đúng cấu trúc 32 bảng, "
        "4 view, 2 hàm, 3 thủ tục lưu trữ và 4 trigger."
    )

    rb.h3("1.1.5. JavaFX 21 và JDBC thuần (ứng dụng desktop Dippie Admin)")
    rb.p(
        "Bên cạnh giao diện web, nhóm viết thêm module desktop/ gồm 64 tệp Java chính (3.605 dòng gồm 223 dòng chú "
        "thích tiếng Việt) chia trên 9 package và 5 lớp kiểm thử JUnit 5 đạt 33/33 ca. Đây là ứng dụng quản trị "
        "desktop Dippie Admin dùng JavaFX 21.0.6 và JDBC thuần (PreparedStatement, quản lý giao dịch tường minh bằng "
        "setAutoCommit(false), commit và rollback). Ứng dụng có 4 tab là Tổng quan, Danh mục, Người dùng và Đánh giá, "
        "vừa phục vụ quản trị nhanh vừa là nơi nhóm thực hành trực tiếp JavaFX, JDBC, Java I/O Streams và các mẫu "
        "thiết kế GoF."
    )

    rb.h2("1.2. Các công nghệ phía giao diện web, đa phương tiện và trợ lý AI")

    rb.h3("1.2.1. React 19, TypeScript 5.7, Vite 8 và Tailwind CSS 3.4")
    rb.p(
        "Phần giao diện web nằm trong thư mục frontend/ (81 tệp ứng dụng, 22.104 dòng mã), viết bằng React 19, "
        "TypeScript (kiểm tra kiểu bằng npm run typecheck), React Router 7, biểu tượng lucide-react và Tailwind CSS "
        "3.4. Giao diện dùng nền sáng màu giấy trắng kết hợp màu xanh xô thơm để trẻ nhỏ và phụ huynh nhìn lâu "
        "đỡ mỏi mắt, hiển thị gọn từ màn hình máy tính đến điện thoại 375 px, kèm thanh thông báo mất mạng "
        "OfflineBanner và các trang pháp lý Điều khoản sử dụng, Chính sách quyền riêng tư. Vite 8 đóng gói mã nguồn "
        "ra frontend/dist để Spring Boot phục vụ trực tiếp trên cổng 3000, nhờ đó không phát sinh lỗi CORS khi chạy."
    )

    rb.h3("1.2.2. Server-Sent Events (SSE) và tích hợp DeepSeek LLM có căn cứ")
    rb.p(
        "Trợ lý CineBot dùng Server-Sent Events (SSE) trên endpoint POST /api/chat (Content-Type: text/event-stream) "
        "để đẩy dần câu trả lời từ máy chủ về trình duyệt theo thứ tự sự kiện metadata, nhiều sự kiện token rồi done "
        "(hoặc error). Với bài toán trả lời một chiều từ server, SSE nhẹ hơn WebSocket, đi qua reverse proxy HTTP dễ "
        "và ít phát sinh lỗi kết nối. Trước khi mở luồng, RequestBodySizeLimitFilter và LocalChatController giới hạn "
        "thân yêu cầu ở mức 256 KB, còn ChatStreamGuard giới hạn tần suất (20 yêu cầu mỗi phút theo IP và tài khoản) "
        "cùng số luồng SSE đồng thời (tối đa 16 luồng)."
    )
    rb.p(
        "Mọi câu hỏi tiếng Việt gửi tới CineBot đều qua IntentParser phân tích ý định rồi truy xuất trên chỉ mục đảo "
        "BM25 lưu trong RAM (LexicalIndex) phủ hơn 2.517 tài liệu của danh mục. Mặc định, TemplateAnswerGenerator "
        "dựng câu trả lời tiếng Việt tại chỗ trong 3 đến 13 ms mà không cần khóa API ngoài. Nếu người vận hành đặt "
        "DIPPIE_LLM_PROVIDER=deepseek và cấu hình DEEPSEEK_API_KEY, DeepSeekAnswerGenerator gửi tối đa 3 trích đoạn "
        "bằng chứng sang DeepSeek API (có giới hạn số lời gọi song song và ngân sách lượt gọi mỗi ngày), kiểm tra "
        "phản hồi để tránh tự bịa tên tập hay mốc giây, và tự quay về TemplateAnswerGenerator khi mạng lỗi hoặc hết "
        "hạn mức."
    )

    rb.h3("1.2.3. YouTube IFrame Player API và truyền phát HTTP Byte Range")
    rb.p(
        "Màn hình xem tập phim hỗ trợ hai nguồn phát. Phần lớn kho phim dùng nguồn YouTube (707/806 tập phụ thuộc "
        "nhúng hoặc mở liên kết YouTube, trong đó 44 tập Công chúa Ori phải mở liên kết gốc); trình duyệt dùng "
        "YouTube IFrame Player API (enablejsapi=1) để nhảy đúng giây của cảnh phim (?start=), đọc vị trí phát "
        "getCurrentTime() khi lưu tiến độ và tự chuyển tập khi xem hết. Với 99 tập Xin Chào Jadoo có nguồn tệp MP4 "
        "cục bộ hoặc Google Drive, backend kiểm tra trạng thái xuất bản của cả tệp lẫn chuỗi bộ, mùa, tập qua "
        "PlaybackAccess rồi phát video qua HTTP Byte Range (GET/HEAD /api/media/stream/{assetId}), trả mã 206 Partial "
        "Content theo từng khối 64 KB đọc bằng FileChannel trong ResolvedMediaStream.writeRange để trình duyệt tua "
        "video ngay mà không phải tải cả tệp."
    )

    rb.h3("1.2.4. Khởi chạy một cú nhấp, Docker Compose, Caddy HTTPS và CI")
    rb.p(
        "Để chạy thử nhanh trên máy Windows không cần tự cài Java hay Node.js, nhóm chuẩn bị tệp CHAY_DIPPIE.bat "
        "(gọi scripts/run-all.ps1), tài liệu HUONG_DAN_CHAY.md, script tương đương run-all.sh cho macOS/Linux và "
        "scripts/make-release-zip.ps1 để đóng gói bản phát hành sẵn giao diện. Ở môi trường container, thư mục "
        "infrastructure/ chứa Dockerfile 3 giai đoạn (build React bằng node:22-alpine, build JAR bằng "
        "eclipse-temurin:21-jdk-jammy và chạy bằng tài khoản non-root dippie trên eclipse-temurin:21-jre-alpine kèm "
        "HEALTHCHECK) cùng hai tệp Compose: infrastructure/docker-compose.yml cho production (gồm caddy:2-alpine, app "
        "dùng hồ sơ backend/springboot/src/main/resources/application-prod.yml và mysql:8.4 đặt trong mạng nội bộ "
        "internal: true) và infrastructure/docker-compose.demo.yml để chạy demo H2 trên 127.0.0.1:3000."
    )
    rb.p(
        "Reverse proxy infrastructure/Caddyfile tự cấp và gia hạn chứng chỉ HTTPS khi đặt biến DOMAIN, gắn các header "
        "bảo mật (HSTS, CSP cho YouTube và SSE, X-Frame-Options: DENY, nosniff), đồng thời tắt nén và đặt "
        "flush_interval -1 cho /api/chat để luồng SSE không bị giữ đệm, cùng giới hạn thân yêu cầu 25 MB cho "
        "/api/admin/catalog/*. Việc sao lưu và khôi phục MySQL được viết sẵn trong scripts/backup-mysql.{sh,ps1} và "
        "scripts/restore-mysql.{sh,ps1} (dùng mysqldump qua MYSQL_PWD, nén gzip, kiểm tra SHA-256 và giữ 14 bản gần "
        "nhất). Toàn bộ kho mã chạy kiểm tra tự động qua .github/workflows/ci.yml (3 job cho Backend Maven, Frontend "
        "và Docker) cùng .github/dependabot.yml quét cập nhật thư viện hằng tuần."
    )

    rb.h2("1.3. Tổng hợp công nghệ sử dụng trong Dippie")
    rb.table(
        "Tổng hợp các công nghệ sử dụng trong Dippie và phần mở rộng so với môn học",
        ["Công nghệ / Kỹ thuật", "Vai trò và nơi áp dụng trong Dippie", "Phần mở rộng so với nội dung học phần"],
        [
            ["Java 21 (Records, Text Blocks, Pattern Matching, Sealed)", "333 tệp mã nguồn backend (com.dippie) và 64 tệp desktop.", "Mở rộng từ cú pháp Java OOP cơ bản lên kiểu bản ghi bất biến, sealed interface và switch đối sánh mẫu."],
            ["Spring Boot 3.5, Spring Data JPA và Flyway", "Tầng REST API (25 Controller), giao dịch, khóa đồng thời, truy vấn Criteria SQL, Flyway V1 đến V5 và SPA.", "Tự động hóa IoC/DI, quản lý phiên bản lược đồ bằng Flyway và ánh xạ ORM chạy trên cả H2 lẫn MySQL."],
            ["Spring Security, JWT (HS256) và BCrypt", "Gói com.dippie.security cùng tầng controller, service, model cho user và account; xác thực web, đăng xuất thu hồi token và vé phát video 5 phút.", "Xác thực phi trạng thái, phiên bản hóa token, header bảo mật, giới hạn thân yêu cầu và giới hạn tần suất theo proxy tin cậy."],
            ["H2 (AUTO_SERVER) và MySQL 8.0 / 8.4 (TLS)", "Cơ sở dữ liệu 32 bảng chuẩn hóa BCNF, múi giờ UTC cho chế độ demo và production.", "Kết hợp lược đồ quan hệ chuẩn, view, hàm, thủ tục, trigger MySQL TLS với chế độ H2 chạy ngay không cần cài đặt."],
            ["JavaFX 21 và JDBC thuần", "Module desktop/ (64 tệp Java, 33 test): cây danh mục, biểu đồ, Hoàn tác và xuất CSV.", "Thực hành trực tiếp JavaFX, JDBC PreparedStatement có giao dịch và các mẫu GoF trên cùng cơ sở dữ liệu."],
            ["React 19, TypeScript, Vite 8 và Tailwind CSS", "Thư mục frontend/ (81 tệp); giao diện web, di động, lịch sử xem, báo lỗi phát, pháp lý và báo mất mạng.", "Tách giao diện thành ứng dụng SPA có kiểm tra kiểu tĩnh thay cho trang HTML tĩnh."],
            ["Server-Sent Events (SSE) và HTTP Byte Range", "Luồng CineBot (/api/chat, ChatStreamGuard) và phát tệp video tua nhanh (/api/media/stream).", "Truyền dữ liệu theo luồng thời gian thực có kiểm soát tải và phát từng đoạn 206 Partial Content."],
            ["BM25 cục bộ và DeepSeek LLM có căn cứ", "Gói com.dippie.service.assistant, com.dippie.model.assistant, com.dippie.dto.assistant và com.dippie.controller.assistant; tìm kiếm không dấu, chịu lỗi gõ và hội thoại.", "Áp dụng truy xuất thông tin BM25 và RAG chống bịa đặt ngay trên JVM."],
            ["YouTube IFrame Player API", "Component YouTubePlayer.tsx; phát nhúng, nhảy đúng mốc giây, lưu tiến độ và tự chuyển tập.", "Điều khiển trình phát YouTube hai chiều qua JavaScript API thay cho thẻ iframe tĩnh."],
            ["CHAY_DIPPIE.bat, Docker, Caddy HTTPS và CI", "CHAY_DIPPIE.bat, HUONG_DAN_CHAY.md, make-release-zip.ps1, infrastructure/, backup-mysql.* và ci.yml.", "Chạy thử 1 cú nhấp tự tải JDK di động, đóng gói container non-root, HTTPS tự động và sao lưu có SHA-256."],
        ],
        col_widths_mm=[38, 64, 58],
        font_size=10.5,
    )

    # =======================================================================
    # CHƯƠNG 2. BÀI TOÁN VÀ CHỨC NĂNG
    # =======================================================================
    rb.h1("Chương 2. Bài toán và chức năng", break_before=True)

    rb.h2("2.1. Đặt bài toán và nhu cầu thực tế")
    rb.p(
        "Khi khảo sát cách phụ huynh mở phim hoạt hình cho trẻ mầm non và tiểu học xem hằng ngày, nhóm chúng em gặp "
        "bốn vấn đề lặp đi lặp lại:"
    )
    rb.bullet(
        "Các tập hoạt hình lồng tiếng hoặc phụ đề tiếng Việt nằm rải rác trên nhiều kênh YouTube, đánh số mùa và tập "
        "không thống nhất, thậm chí dễ lẫn sang video biên tập lại hoặc nội dung không hợp với trẻ nhỏ."
    )
    rb.bullet(
        "Trẻ em hiếm khi nhớ tên tập chính xác mà thường chỉ nhớ nhân vật hoặc một tình huống ngắn như \"cảnh con sâu "
        "Red đội cây kem\", \"Nobita đi vũ trụ\" hay \"Jadoo sợ tăng cân\". Cách tìm theo tiêu đề thông thường dễ trả "
        "nhầm hoặc không ra kết quả."
    )
    rb.bullet(
        "Phụ huynh khó lọc nhanh tập ngắn dưới 5 phút cho bé xem trước giờ ngủ và cũng không có chỗ lưu tập trung "
        "vị trí đang xem dở giữa các buổi xem."
    )
    rb.bullet(
        "Nguồn video thực tế hay gặp lỗi chặn theo vùng lãnh thổ, tắt quyền phát nhúng trên YouTube hoặc thiếu tệp "
        "lưu trữ cục bộ, nên cần cơ chế quản trị danh mục theo phiên bản và hiển thị rõ trạng thái nguồn phát."
    )
    rb.p(
        "Dippie được xây dựng để gom 6 bộ hoạt hình quen thuộc (806 tập) vào một giao diện tiếng Việt gọn, không có "
        "quảng cáo gây nhiễu. Người dùng có thể tìm kiếm chịu lỗi gõ, lọc theo tâm trạng, lưu tiến độ xem, hỏi trợ lý "
        "CineBot nhảy thẳng đến đúng cảnh phim, còn người vận hành có cả trang quản trị web lẫn ứng dụng desktop "
        "JavaFX dùng chung cơ sở dữ liệu."
    )

    rb.h2("2.2. Đối tượng người dùng")
    rb.table(
        "Phân nhóm đối tượng người dùng của hệ thống Dippie",
        ["Đối tượng / Vai trò", "Đặc điểm sử dụng", "Phạm vi thao tác chính"],
        [
            ["Khách (Guest)", "Người dùng mở trang lần đầu, chưa có tài khoản.", "Xem danh sách 6 bộ phim, tìm kiếm và lọc kho phim, xem video công khai, hỏi CineBot, lọc theo tâm trạng, gửi báo lỗi phát video, đọc điều khoản và quyền riêng tư, lưu tạm thư viện và tiến độ trên trình duyệt."],
            ["Người dùng đã đăng nhập (ROLE_USER)", "Phụ huynh và thành viên gia đình có tài khoản JWT.", "Dùng toàn bộ tính năng của Khách, xác nhận chuyển dữ liệu tạm vào tài khoản, quản lý Xem sau và Yêu thích, chấm điểm 1 đến 10, xem và xóa lịch sử xem, nhận gợi ý cá nhân hóa, đổi hoặc đặt lại mật khẩu qua email, đăng xuất thu hồi phiên và tự xóa tài khoản."],
            ["Quản trị viên Web (ROLE_ADMIN)", "Người quản lý nội dung và vận hành trên trình duyệt.", "Kiểm tra hợp lệ, nhập và xuất gói danh mục JSON chuẩn dippie.catalog.v1, theo dõi snapshot chỉ mục, quản lý tài khoản (đổi quyền, khóa hoặc mở khóa), xem thống kê, nhật ký kiểm toán và xử lý báo lỗi phát video."],
            ["Quản trị viên Desktop (Dippie Admin)", "Quản trị viên làm việc trực tiếp với cơ sở dữ liệu qua ứng dụng JavaFX.", "Đăng nhập bằng tài khoản quản trị (BCrypt), xem biểu đồ thống kê, duyệt cây danh mục Bộ, Mùa và Tập, quản lý tài khoản có hoàn tác (Undo), kiểm duyệt đánh giá và xuất tệp CSV."],
        ],
        col_widths_mm=[34, 46, 80],
        font_size=10.5,
    )

    rb.h2("2.3. Danh sách ca sử dụng và sơ đồ ca sử dụng")
    rb.figure(ASSETS_DIR / "usecase_diagram.png", "Sơ đồ Use Case tổng quan hệ thống Dippie", width_inches=6.1)

    rb.table(
        "Danh sách 12 Use Case chính của hệ thống Dippie",
        ["Mã UC", "Tên Use Case", "Tác nhân", "Mô tả ngắn"],
        [
            ["UC01", "Duyệt phim bộ, tìm kiếm và lọc", "Khách, Người dùng", "Xem 6 bộ phim, tìm kiếm không dấu, bí danh và lỗi gõ, lọc theo thể loại, năm phát hành và sắp xếp theo điểm đánh giá, năm hoặc tựa."],
            ["UC02", "Xem tập phim đa nguồn", "Khách, Người dùng", "Phát video YouTube nhúng (YouTube IFrame API), phát tệp cục bộ/Drive qua HTTP Byte Range hoặc mở liên kết YouTube gốc với tập không cho nhúng."],
            ["UC03", "Hỏi đáp trợ lý CineBot", "Khách, Người dùng", "Đặt câu hỏi tiếng Việt (số tập, tình tiết, tóm tắt, gợi ý theo thời lượng), nhận luồng SSE và bấm mở đúng mốc giây của cảnh phim."],
            ["UC04", "Đăng ký, đăng nhập, tài khoản và đồng bộ", "Khách, Người dùng", "Đăng ký kèm đồng ý điều khoản, đăng nhập nhận JWT HS256, xác nhận gộp dữ liệu khách vào tài khoản, đặt lại mật khẩu qua email, đăng xuất thu hồi token và tự xóa tài khoản."],
            ["UC05", "Quản lý thư viện và chấm điểm", "Người dùng", "Thêm hoặc bỏ Xem sau, Yêu thích theo kiểu lũy đẳng và chấm điểm nguyên từ 1 đến 10 cho bộ phim hoặc từng tập."],
            ["UC06", "Lưu tiến độ, lịch sử xem và báo lỗi phát", "Người dùng, Khách", "Lưu vị trí xem theo mili giây có khóa lạc quan, hiện Xem tiếp, xem hoặc xóa lịch sử xem (/lich-su), tự chuyển tập và gửi báo lỗi khi video gặp trục trặc."],
            ["UC07", "Nhận gợi ý cá nhân và tâm trạng", "Khách, Người dùng", "Nhận gợi ý theo lịch sử và đánh giá (Affinity) hoặc chọn 6 nút tâm trạng ngay trên trang chủ (Mood)."],
            ["UC08", "Nhập/xuất danh mục và snapshot", "Quản trị Web", "Kiểm tra cấu trúc, xung đột revision và SHA-256, nhập nguyên tử vào CSDL, làm mới chỉ mục và xuất gói chuẩn dippie.catalog.v1."],
            ["UC09", "Quản lý tài khoản người dùng", "Quản trị Web và Desktop", "Tìm tài khoản, đổi vai trò hoặc khóa/mở khóa, tự thu hồi JWT cũ và chặn khóa nhầm quản trị viên hoạt động cuối cùng."],
            ["UC10", "Thống kê vận hành và kiểm toán", "Quản trị Web và Desktop", "Xem tổng số người dùng, tỷ lệ hoàn thành phiên xem, top tập xem nhiều, top phim điểm cao và nhật ký thao tác quản trị."],
            ["UC11", "Duyệt cây danh mục và duyệt đánh giá", "Quản trị Desktop", "Duyệt cây Bộ phim, Mùa và Tập trên TreeView (Composite) và xem hoặc xóa các lượt đánh giá trên bảng."],
            ["UC12", "Xuất báo cáo CSV và Hoàn tác", "Quản trị Desktop", "Xuất bảng thống kê, người dùng hoặc đánh giá ra tệp CSV chuẩn UTF-8 BOM và hoàn tác (Undo) thay đổi tài khoản qua Command."],
        ],
        col_widths_mm=[15, 38, 28, 79],
        font_size=10.5,
    )

    rb.h2("2.4. Yêu cầu chức năng theo phân nhóm")
    rb.p(
        "Các chức năng của Dippie được chia thành tám mảng tương ứng với hành trình của người xem gia đình và quản trị "
        "viên:"
    )
    rb.bullet(
        "Khám phá và tìm kiếm: hiển thị 6 bộ hoạt hình (806 tập) tại /series và /search; hỗ trợ tìm không dấu "
        "(Trang Quynh, Cong chua Ori), nhận bí danh nhân vật và tên gốc (Doremon, Peppa Pig, Xuka, Chaien, Xeko), "
        "chịu lỗi gõ 1 đến 2 ký tự (pepa, nobitta, doraemom, khung log), lọc theo thể loại hoặc khoảng năm và sắp xếp "
        "theo điểm cộng đồng hoặc năm phát hành (NULLS LAST)."
    )
    rb.bullet(
        "Xem phim, lịch sử xem và báo lỗi phát: phát qua YouTube IFrame Player API hoặc HTTP Byte Range (206 Partial "
        "Content); lưu vị trí xem theo mili giây có khóa lạc quan (opt_lock_version) để tránh hai cửa sổ ghi đè lẫn "
        "nhau; hiển thị hàng Xem tiếp, trang Lịch sử xem (/lich-su) có nút xóa sạch toàn bộ lịch sử (DELETE "
        "/api/users/watch-progress), gợi ý tập tiếp theo (NextEpisodeAction) và tự chuyển tập qua cả ranh giới mùa. "
        "Ngay dưới khung phát có nút Báo lỗi video (PlaybackReportButton) gửi về POST /api/reports/playback. 44 tập "
        "Công chúa Ori không cho nhúng được gắn nhãn \"Chỉ mở trên YouTube\", kèm thanh OfflineBanner báo khi mất mạng."
    )
    rb.bullet(
        "Đánh giá phim: người dùng đã đăng nhập chấm điểm nguyên từ 1 đến 10, sửa hoặc xóa đánh giá ngay trên banner "
        "bộ phim và bảng điều khiển tập tại /series/:id. Điểm trung bình và số lượt đánh giá cập nhật lại ngay không "
        "cần tải lại trang, đồng thời điểm cao (từ 8 trở lên) hoặc thấp (1 đến 4) được dùng trong thuật toán gợi ý."
    )
    rb.bullet(
        "Thư viện cá nhân và vòng đời tài khoản: thêm hoặc bỏ Xem sau và Yêu thích theo kiểu lũy đẳng để tránh lỗi "
        "khi bấm đúp; hiển thị tại /profile với nhãn thể loại tiếng Việt và nút gỡ khỏi Yêu thích. Khi người dùng "
        "đăng ký (bắt buộc đánh dấu đồng ý Điều khoản sử dụng tại /dieu-khoan và Chính sách quyền riêng tư tại "
        "/quyen-rieng-tu) hoặc đăng nhập, hộp thoại GuestSyncDialog hỏi xác nhận trước khi chuyển dữ liệu lưu tạm từ "
        "localStorage lên tài khoản để tránh gộp nhầm trên máy dùng chung trong gia đình. Tài khoản còn có đăng xuất "
        "phía máy chủ thu hồi JWT cũ, đặt lại mật khẩu qua email (/quen-mat-khau, /dat-lai-mat-khau) và tự xóa tài "
        "khoản tại /account (DeleteAccountSection)."
    )
    rb.bullet(
        "Gợi ý phim: dùng hai chiến lược là AffinityRecommendationStrategy (tính theo yêu thích, điểm đánh giá và tiến "
        "độ xem, khách mới nhận 1 tập đại diện cho mỗi bộ trong 6 bộ, người có lịch sử giới hạn tối đa 2 tập mỗi bộ) "
        "và MoodRecommendationStrategy (6 nút tâm trạng trên trang chủ gồm Vui nhộn, Ấm áp gia đình, Nhẹ nhàng trước "
        "giờ ngủ, Phiêu lưu khám phá, Tình bạn, Tập ngắn dưới 10 phút, loại bỏ tuyển tập dài và tập không nhúng được)."
    )
    rb.bullet(
        "Trợ lý CineBot: mở hộp thoại từ thanh điều hướng, nhận câu trả lời qua luồng SSE; trả lời thống kê số tập, "
        "tra cứu mùa/tập, tìm cảnh theo tình tiết kèm liên kết mở thẳng mốc giây (?segment=...&t=ms); hỗ trợ hỏi tiếp "
        "nhiều lượt (\"Cảnh sau đó thì sao\", \"Không, tập khác\", \"phim giống Larva\"), lọc thời lượng dưới 5 phút "
        "và từ chối câu hỏi ngoài kho phim (Naruto, One Piece, thời tiết)."
    )
    rb.bullet(
        "Quản trị web (ROLE_ADMIN): gồm 5 mục trên thanh điều hướng quản trị (AdminNav): kiểm tra, nhập và xuất gói "
        "danh mục JSON chuẩn dippie.catalog.v1 (/admin/import) kèm lịch sử và snapshot chỉ mục, quản lý người dùng "
        "(/admin/users), thống kê (/admin/stats), nhật ký kiểm toán (/admin/audit) và xử lý báo lỗi phát video "
        "(/admin/reports)."
    )
    rb.bullet(
        "Ứng dụng quản trị desktop Dippie Admin: viết bằng JavaFX 21 và JDBC thuần, có màn hình đăng nhập kiểm tra "
        "BCrypt, tab Tổng quan vẽ biểu đồ, tab Danh mục dùng cây Composite, tab Người dùng có nút Hoàn tác (Undo), tab "
        "Đánh giá để kiểm duyệt và nút Xuất CSV."
    )

    rb.h2("2.5. Yêu cầu phi chức năng")
    rb.table(
        "Các yêu cầu phi chức năng và cách kiểm chứng",
        ["Nhóm yêu cầu", "Yêu cầu đặt ra", "Biện pháp kỹ thuật và kiểm chứng"],
        [
            ["Bảo mật", "Chống truy cập trái phép, rò rỉ bản ghi ẩn, SQL Injection, path traversal, CSV injection, tấn công quá tải và khóa nhầm quản trị viên cuối cùng.", "Đợt rà soát theo tiêu chuẩn OWASP ghi nhận 30 phát hiện (6 mức nghiêm trọng, 12 mức cao); nhóm đã xử lý đủ 6/6 mục nghiêm trọng (không bật fail-open hồ sơ demo, giới hạn tần suất và 16 luồng đồng thời trên CineBot qua ChatStreamGuard, kiểm tra trạng thái xuất bản đầy đủ trên luồng phát media qua PlaybackAccess, giới hạn thân yêu cầu HTTP 64 KB / 256 KB / 25 MB trả 413 qua RequestBodySizeLimitFilter) và phần lớn mục mức cao (đăng xuất phía máy chủ tăng tokenVersion, header bảo mật CSP/HSTS/no-store trực tiếp từ SecurityConfig, giới hạn tần suất theo proxy tin cậy với ClientIpResolver và FixedWindowRateLimiter, chính sách mật khẩu PasswordPolicy 6/16 ký tự và 72 byte UTF-8, MySQL TLS, tắt Swagger ngoài demo, Flyway migration và chuẩn hóa dấu thời gian UTC), song song PreparedStatement, Proxy AdminQuery, CsvExporter chống công thức, MediaPathPolicy và khóa PESSIMISTIC_WRITE."],
            ["Hiệu năng", "API phản hồi nhanh trên máy cục bộ và giữ ổn định khi mở rộng tới 100.000 tập.", "Đo thực tế trên demo H2: độ trễ trung bình 3 đến 43 ms (CineBot SSE 5 ms, danh mục 3 ms, tìm kiếm 13 đến 41 ms). Kiểm thử MySQL 8.0.46 ở mốc 100.000 tập: phân trang SQL 1.642 ms, lấy chi tiết tập 6,4 ms; dùng bộ đệm CatalogReadModelCache bất biến không chặn đọc."],
            ["Khả năng tiếp cận (Accessibility)", "Thao tác được bằng bàn phím, tương phản chữ rõ và có nhãn ARIA cho trình đọc màn hình.", "Đặt tablist/tab/tabpanel hỗ trợ phím mũi tên tại /profile; menu/menuitem và phím Escape tại Navbar; role=\"log\" ở CineBot; role=\"alert\" ở AuthModal; tiêu đề trang document.title đổi theo tuyến."],
            ["Thiết bị di động (Mobile)", "Hiển thị gọn trên màn hình điện thoại 375x812 px, dễ chạm bằng ngón tay.", "Kiểm chứng bằng Chromium không giao diện ở 375x812 px: 0 tràn ngang trên 7 tuyến chính; ô nhập và danh sách chọn từ 16 px trở lên để tránh iOS tự phóng to; nút lưu và chip lọc đạt vùng chạm tối thiểu 44x44 px; hiện tóm tắt thẻ phim mà không cần di chuột."],
        ],
        col_widths_mm=[28, 52, 80],
        font_size=10.5,
    )

    # =======================================================================
    # CHƯƠNG 3. THIẾT KẾ VÀ CÀI ĐẶT
    # =======================================================================
    rb.h1("Chương 3. Thiết kế và cài đặt", break_before=True)

    rb.h2("3.1. Kiến trúc tổng thể hệ thống")
    rb.p(
        "Dippie dùng kiến trúc hợp nhất gồm ba tầng phối hợp với nhau. Tầng ứng dụng khách gồm giao diện web React 19 "
        "SPA cho người xem và quản trị viên web, chạy song song với ứng dụng desktop JavaFX 21 Dippie Admin. Tầng máy "
        "chủ hợp nhất là một tiến trình Spring Boot 3.5 (Java 21) nghe trên cổng 3000, đảm nhiệm REST API, xác thực "
        "JWT, truyền phát Byte Range, chỉ mục BM25 trong RAM, luồng SSE CineBot và phục vụ tệp tĩnh SPA. Tầng dữ liệu "
        "và dịch vụ ngoài gồm cơ sở dữ liệu H2 (demo) hoặc MySQL 8.0/8.4 (production), nguồn video (YouTube IFrame, "
        "tệp cục bộ hoặc Google Drive) và DeepSeek API khi người dùng bật."
    )
    rb.figure(ASSETS_DIR / "arch_diagram.png", "Sơ đồ kiến trúc tổng thể hệ thống Dippie", width_inches=6.2)
    rb.p(
        "Việc gom phần nghiệp vụ web, chỉ mục tìm kiếm BM25 và CineBot vào chung một tiến trình JVM giúp nhóm bỏ được "
        "chi phí gọi mạng giữa các dịch vụ (luồng SSE phản hồi trong 3 đến 13 ms), làm mới chỉ mục ngay sau giao dịch "
        "nhập danh mục bằng sự kiện CatalogImportedEvent, và chỉ cần một lệnh PowerShell để dựng bản demo khi chấm bài."
    )

    rb.h2("3.2. Kiến trúc MVC")
    rb.p(
        "Cả ứng dụng web lẫn ứng dụng desktop Dippie Admin đều tổ chức theo kiến trúc MVC nghiêm ngặt, tách rạch ròi "
        "dữ liệu và quy tắc miền (Model), phần hiển thị cùng dữ liệu trao đổi giao diện (View), lớp tiếp nhận và "
        "điều phối yêu cầu (Controller) và tầng Service đứng giữa Controller với Model. Ở backend Spring Boot, mã "
        "nguồn chia theo tầng trước rồi chia gói con theo từng tính năng (controller.<tính năng>, service.<tính năng>, "
        "repository.<tính năng>, model.<tính năng>, dto.<tính năng>), kèm gói ngoại lệ com.dippie.exception và ba nhóm "
        "hỗ trợ là config, security, common. Ba quy tắc phụ thuộc được giữ chặt trong mã nguồn: controller chỉ gọi "
        "service và không truy cập trực tiếp repository, tầng model không phụ thuộc tầng nào phía trên, còn "
        "repository không phụ thuộc service."
    )
    rb.p(
        "Ở ứng dụng desktop Dippie Admin, các gói chia thành model (gồm model.catalog), view, controller, service, "
        "dao, db và export. Các lớp giao diện trong com.dippie.desktop.view chỉ làm việc với controller, model và "
        "export; các kiểu dữ liệu hiển thị cho bảng và màn hình tổng quan (TopRated, MostWatched, DashboardSnapshot, "
        "DashboardCounts, RatingRow) đặt trong com.dippie.desktop.model, còn CatalogPane lọc cây danh mục thông qua "
        "AdminController.filterCatalog."
    )
    rb.table(
        "Ánh xạ kiến trúc MVC trên ứng dụng Web và ứng dụng Desktop Dippie Admin",
        ["Thành phần MVC", "Ứng dụng Web (Spring Boot + React SPA)", "Ứng dụng Desktop (Dippie Admin JavaFX)"],
        [
            [
                "Model (dữ liệu và quy tắc)",
                "Gói model.<tính năng> gồm thực thể JPA (User, Movie, MovieRating, WatchProgress, CatalogSeries, CatalogEpisode...), enum (Role, PublicationStatus) và bản ghi/quy tắc miền (CompletionPolicy, NextEpisodePolicy, ResolvedMediaStream...), cùng gói repository.<tính năng> gồm các Spring Data JPA Repository (UserRepository, MovieRatingRepository, CatalogEpisodeRepository, WatchProgressRepository...) và truy vấn viết tay (AdminStatsQuery, ViewerSignalsRepository).",
                "Gói com.dippie.desktop.model và com.dippie.desktop.model.catalog (UserAccount, AdminSession, Role, cây Composite CatalogNode gồm SeriesNode, SeasonNode, EpisodeNode, các kiểu hàng bảng và thống kê TopRated, MostWatched, DashboardSnapshot, DashboardCounts, RatingRow), cùng 10 lớp JDBC DAO kế thừa AbstractJdbcDao<T, ID> trong com.dippie.desktop.dao và tầng kết nối com.dippie.desktop.db.",
            ],
            [
                "View (giao diện và dữ liệu hiển thị)",
                "Giao diện React 19 SPA trong thư mục frontend/ (SeriesDetailBanner, ItemLibraryPanel, YouTubePlayer, CineBot, OfflineBanner, GuestSyncDialog, WatchlistContext, useSeriesPlayback) cùng các bản ghi JSON trong gói dto.<tính năng> (CatalogSearchHit, CatalogSeriesDetail, CatalogEpisodeDetail, SeriesCard, LibraryItemView, MovieRatingView, WatchProgressView, DiscoverResponse...) gửi qua lại giữa server và trình duyệt.",
                "Lớp khởi chạy DippieAdminApp và 9 lớp giao diện JavaFX trong com.dippie.desktop.view (HeaderBar, LoginPane, WorkspacePane, lớp cơ sở trừu tượng DataPane, 4 tab DashboardPane, CatalogPane, UsersPane, RatingsPane và thanh trạng thái StatusBar). Các lớp View chỉ nhập controller, model và export.",
            ],
            [
                "Controller (điều phối yêu cầu)",
                "Gói controller.<tính năng> gồm 25 lớp @RestController / @Controller (LibraryController, CatalogController, AdminCatalogController, MovieController, WatchProgressController, RecommendationController, LocalChatController, LocalDiscoverController, AuthController, SessionLogoutController, AccountErasureController, PasswordResetController, PlaybackReportController, AdminUserController, AdminStatsController...) và GlobalExceptionHandler (com.dippie.controller.web). Controller chỉ nhận HTTP, gọi Service và trả DTO hoặc SSE.",
                "Gói com.dippie.desktop.controller gồm LoginController, AdminController, UiTasks và ConnectionState: nhận sự kiện giao diện từ View, điều phối lệnh đọc/ghi xuống tầng Service trên luồng nền (kể cả AdminController.filterCatalog cho CatalogPane) và trả kết quả về luồng JavaFX.",
            ],
            [
                "Tầng Service (giữa Controller và Model)",
                "Gói service.<tính năng> chứa logic nghiệp vụ, các mẫu Strategy và Adapter, bộ tìm kiếm BM25 cục bộ và đường ống CineBot (AbstractLibraryListService, MovieRatingService, RecommendationStrategy, RecommendationService, CatalogReadModelCache, CatalogPackageValidator, CatalogImportService, DiscoverService, ChatService, MediaStreamService, MailDeliveryStrategy...).",
                "Gói com.dippie.desktop.service gồm AdminServices, AuthService, CatalogService, DashboardService, UserService, RatingService, AccountCommand, CommandHistory và Proxy AdminQuery: gom quy tắc nghiệp vụ, giao dịch JDBC và cơ chế hoàn tác ở giữa Controller với DAO.",
            ],
        ],
        col_widths_mm=[26, 69, 65],
        font_size=9.8,
    )

    rb.h3("3.2.1. Luồng MVC thực tế: chấm điểm một tập phim")
    rb.p(
        "Để thấy rõ các tầng phối hợp ra sao khi ứng dụng chạy, xét thao tác người dùng chấm điểm từ 1 đến 10 cho một "
        "tập phim trên trang chi tiết /series/:id:"
    )
    rb.bullet(
        "Bước 1 (View): Người dùng bấm chọn điểm trên component ItemLibraryPanel "
        "(frontend/features/library/components/LibraryControls.tsx). Nút bấm gọi hàm rateMovie(itemId, score, title) "
        "trong WatchlistContext (frontend/lib/context/WatchlistContext.tsx)."
    )
    rb.bullet(
        "Bước 2 (Gửi yêu cầu HTTP): WatchlistContext cập nhật trạng thái lạc quan trên giao diện rồi gọi "
        "saveUserRating(...) trong libraryService.ts để gửi yêu cầu POST /api/users/ratings kèm JWT và thân JSON "
        "{ movieId, rating }."
    )
    rb.bullet(
        "Bước 3 (Controller): LibraryController.addRating(RatingRequest) trong gói com.dippie.controller.library nhận "
        "yêu cầu POST /api/users/ratings với bản ghi DTO RatingRequest (gói com.dippie.dto.library), tách hai trường "
        "movieId và rating rồi chuyển sang MovieRatingService.rate(movieId, rating)."
    )
    rb.bullet(
        "Bước 4 (Service và Model): MovieRatingService (gói com.dippie.service.library) xác định người xem hiện tại "
        "qua CurrentViewer, kiểm tra điểm nằm trong đoạn 1 đến 10 bằng quy tắc miền MovieRating.validateRating(rating) "
        "(gói com.dippie.model.library), kiểm tra phim đang hiển thị qua MovieVisibility.findVisibleById(movieId), "
        "rồi tra cứu hoặc lưu thực thể MovieRating xuống cơ sở dữ liệu qua MovieRatingRepository "
        "(gói com.dippie.repository.library) trong giao dịch REQUIRES_NEW (bắt DataIntegrityViolationException nếu "
        "hai yêu cầu ghi trùng lúc)."
    )
    rb.bullet(
        "Bước 5 (DTO và cập nhật View): MovieRatingService trả bản ghi DTO MovieRatingView (gói "
        "com.dippie.dto.library) để LibraryController bọc trong ApiResponse.ok(...) gửi về trình duyệt dạng JSON. "
        "Khi nhận phản hồi thành công, WatchlistContext gọi notifyRatingChanged(movieId) để xóa bộ đệm "
        "seriesCardService và phát sự kiện dippie:rating-changed. Hook useSeriesPlayback đang lắng nghe sự kiện này "
        "gọi refreshEpisodeRating(...) để đọc lại điểm trung bình (tính từ MovieRatingRepository.summarize(...) qua "
        "RatingSummaries) và vẽ lại điểm mới ngay trên màn hình."
    )

    rb.h3("3.2.2. Liên hệ với mẫu Observer và kỷ luật phân tầng MVC")
    rb.p(
        "Như bài giảng Design Patterns trên lớp trình bày (slide 50 đến 52), MVC dùng trực tiếp mẫu Observer để tách "
        "Model khỏi View: nguồn dữ liệu phát thông báo khi trạng thái đổi, còn các màn hình tự đăng ký theo dõi và "
        "cập nhật phần hiển thị của mình mà tầng dữ liệu giữ độc lập với giao diện. Trong Dippie Admin JavaFX, "
        "AdminController công bố các thuộc tính quan sát được gồm usersChangedProperty, ratingsChangedProperty và "
        "undoAvailableProperty, còn UiTasks công bố connectionProperty; các lớp WorkspacePane, UsersPane và "
        "StatusBar gắn listener lên những thuộc tính này để tự làm mới khi thao tác khóa tài khoản, hoàn tác hoặc xóa "
        "đánh giá hoàn tất. Ở phía web, cơ chế tương tự xuất hiện ở cả hai đầu: WatchlistContext phát sự kiện "
        "dippie:rating-changed cho useSeriesPlayback và trang hồ sơ cập nhật điểm, còn phía backend "
        "CatalogImportService phát CatalogImportedEvent để CatalogReadModelCache, LocalCatalogSearch và "
        "CatalogImportAuditListener tự phản ứng độc lập."
    )
    rb.p(
        "Cách chia gói theo tầng trước rồi mới chia theo tính năng giúp ranh giới MVC thể hiện thẳng trên cây thư "
        "mục. Chẳng hạn tính năng thư viện nằm gọn ở đúng năm tầng: LibraryController ở com.dippie.controller.library, "
        "RatingRequest và MovieRatingView ở com.dippie.dto.library, AbstractLibraryListService, FavoriteService, "
        "WatchlistService và MovieRatingService ở com.dippie.service.library, thực thể MovieRating và LibraryEntry "
        "ở com.dippie.model.library, còn MovieRatingRepository và LibraryEntryRepository ở "
        "com.dippie.repository.library. Tương tự, LocalDiscoverController ở com.dippie.controller.assistant chỉ giải "
        "mã đầu vào rồi gọi DiscoverService ở com.dippie.service.assistant, giữ trọn quy tắc controller không đụng "
        "tới repository hay xử lý nghiệp vụ. Giữa các tính năng trong tầng service, các lớp Adapter "
        "(UserViewerAdapter, MediaFileAvailabilityAdapter, MoviePresentationAdapter, RatingSummaries) tiếp tục ngăn "
        "phụ thuộc chéo. Ở ứng dụng desktop Dippie Admin, các lớp trong com.dippie.desktop.view chỉ phụ thuộc "
        "controller, model và export; mọi bản ghi dùng cho bảng và thống kê (TopRated, MostWatched, "
        "DashboardSnapshot, DashboardCounts, RatingRow) nằm trong com.dippie.desktop.model, và CatalogPane lọc danh "
        "mục qua AdminController.filterCatalog để không gọi trực tiếp sang CatalogService."
    )

    rb.h2("3.3. Cấu trúc gói mã nguồn")
    rb.p(
        "Đo trực tiếp trên mã nguồn hiện tại cho thấy backend (backend/springboot/src/main/java) có 333 tệp Java "
        "chính (24.416 dòng tổng cộng, gồm 23.140 dòng không tính chú thích và 1.276 dòng chú thích tiếng Việt) chia "
        "trên 74 package theo mô hình MVC phân tầng trước, gói con theo tính năng. Phần giao diện web frontend/ có "
        "81 tệp ứng dụng (22.104 dòng), còn module desktop/ có 64 tệp Java chính (3.605 dòng gồm 223 dòng chú thích) "
        "trên 9 package."
    )
    rb.table(
        "Cấu trúc các package theo tầng MVC trong backend Spring Boot, frontend và module desktop",
        ["Tầng / Nhóm package", "Số tệp", "Trách nhiệm chính và các lớp tiêu biểu"],
        [
            [
                "controller.<tính năng> (12 gói con: account, admin, assistant, catalog, library, media, movie, progress, recommendation, report, user, web)",
                "26",
                "25 lớp REST/SPA Controller ánh xạ HTTP sang lời gọi Service và trả bản ghi DTO hoặc SSE (LibraryController, CatalogController, AdminCatalogController, MovieController, WatchProgressController, NextEpisodeController, WatchHistoryClearController, MediaStreamController, PlaybackGrantController, RecommendationController, LocalChatController, LocalDiscoverController, AuthController, AccountController, PasswordResetController, SessionLogoutController, AccountErasureController, AdminUserController, AdminStatsController, AdminAuditController, PlaybackReportController, HealthController, SpaController...) cùng GlobalExceptionHandler (com.dippie.controller.web).",
            ],
            [
                "service.<tính năng> (13 gói con: account, admin, assistant, catalog, library, mail, media, movie, progress, recommendation, report, user, web)",
                "101",
                "Toàn bộ logic nghiệp vụ, các mẫu Strategy và Adapter, bộ tìm kiếm BM25 trong RAM và đường ống CineBot: AbstractLibraryListService, FavoriteService, WatchlistService, MovieRatingService, RatingSummaries; RecommendationService, RecommendationStrategy (Affinity, Mood); CatalogImportService, CatalogPackageValidator, CatalogQueryService, CatalogReadModelCache, CatalogSnapshotService; DiscoverService, ChatService, IntentParser, LexicalIndex, LocalCatalogSearch, ChatStreamGuard, AnswerGenerator (Template, DeepSeek); MediaStreamService, PlaybackAccess, MediaFileResolver, MediaFileAvailabilityAdapter; MovieQueryService, MoviePresentationAdapter; WatchProgressService, NextEpisodeService, PlaybackContextResolver; AuthService, UserViewerAdapter; MailDeliveryStrategy; HealthService, SpaIndex.",
            ],
            [
                "repository.<tính năng> (10 gói con: account, admin, catalog, library, media, movie, progress, recommendation, report, user)",
                "22",
                "Các Spring Data JPA Repository và truy vấn viết tay truy cập H2/MySQL: UserRepository, PasswordResetTokenRepository, AdminAuditEventRepository, AdminStatsQuery, 9 repository danh mục (CatalogSeriesRepository, CatalogSeasonRepository, CatalogEpisodeRepository, CatalogSegmentRepository, CatalogSceneRepository, CatalogGenreRepository, CatalogMediaAssetRepository, CatalogPackageRepository, CatalogSnapshotRepository), LibraryEntryRepository, FavoriteItemRepository, WatchlistItemRepository, MovieRatingRepository, MediaAssetRepository, MovieRepository (JpaSpecificationExecutor), WatchProgressRepository, ViewerSignalsRepository và PlaybackReportRepository.",
            ],
            [
                "model.<tính năng> (12 gói con: account, admin, assistant, catalog, library, mail, media, movie, progress, recommendation, report, user)",
                "70",
                "Thực thể JPA, enum và bản ghi/quy tắc miền độc lập với các tầng phía trên: User, Role; PasswordResetToken; AdminAuditEvent; CatalogRecord và 11 thực thể danh mục, CatalogSnapshot, CatalogImportedEvent, PublicationStatus, AccessPolicy, ContentHash, YoutubeSource; LibraryEntry, FavoriteItem, WatchlistItem, MovieRating; EmailMessage; ByteRange, MediaAsset, ResolvedMediaStream; Movie, MovieFilter, MovieGenre, MovieGenreTerm; CompletionPolicy, NextEpisodePolicy, PlaybackContext, WatchDuration, WatchProgress, WatchProgressCommand; MoodPreset, RecommendationCandidate, ViewerSignals; PlaybackReport; ChatIntent, SearchDocument, SearchHit, SearchConstraints.",
            ],
            [
                "dto.<tính năng> (12 gói con: account, admin, assistant, catalog, library, media, movie, progress, recommendation, report, user, web)",
                "60",
                "Các Java Record yêu cầu và phản hồi JSON trao đổi với giao diện React 19: AuthResponse, LoginRequest, RegisterRequest, UserProfileResponse; ChangePasswordRequest, DeleteAccountRequest, PasswordResetRequestDto; AdminPageResponse, AdminStatsResponse, AdminUserView, AdminAuditEventView; ChatEvent (sealed), ChatMetadata, ChatDonePayload, DiscoverRequest, DiscoverResponse; CatalogSearchHit, CatalogSeriesDetail, CatalogEpisodeDetail, SeriesCard, CatalogImportResult; LibraryItemView, MovieIdRequest, MovieRatingView, RatingRequest; MediaAssetView, PlaybackGrantResponse; MovieBrowsePage, MovieView; WatchProgressView; RecommendationRequest, RecommendationResult; PlaybackReportView; HealthStatus (sealed).",
            ],
            [
                "com.dippie.exception (1 gói)",
                "13",
                "Toàn bộ hệ thống ngoại lệ của backend: lớp trừu tượng DomainException và các lớp con (NotFoundException, InvalidRequestException, ConflictException, ForbiddenException, UnauthorizedException, TooManyRequestsException, ServiceUnavailableException, RequestBodyTooLargeException, ChatPayloadTooLargeException, CatalogInputException) cùng ChatCapacityException và RangeNotSatisfiableException.",
            ],
            [
                "config.<tính năng> (7 gói con: account, assistant, mail, media, report, user, web)",
                "11",
                "Các lớp @Configuration và bản ghi @ConfigurationProperties: PasswordResetRateLimitProperties, AssistantProperties, ChatConfiguration, MailConfiguration, MailProperties, MediaProperties, PlaybackReportRateLimitProperties, BootstrapAdminProperties, OpenApiConfig, WebProperties và WebResourceConfig.",
            ],
            [
                "com.dippie.security (1 gói)",
                "18",
                "SecurityConfig fail-closed kèm header bảo mật, RequestBodySizeLimitFilter, JwtService (HS256, tokenVersion, vé 5 phút), JwtAuthenticationFilter, ActiveViewerLookup, CurrentViewer, SecurityContextCurrentViewer, Viewer, sealed PlaybackPrincipal, ClientIpResolver, TrustedProxyProperties, FixedWindowRateLimiter, LoginRateLimiter và PasswordPolicy.",
            ],
            [
                "common.<nhóm> (5 gói con: collection, json, rating, text, web) và com.dippie",
                "12",
                "Điểm khởi chạy DippieApplication cùng tiện ích dùng chung: DiverseSelection, CatalogJson, CatalogJsonWriter, JsonText, RatingLookup, RatingSummary, TextFolding, UnicodeText, ApiResponse<T>, ClockConfig và UtcDateTimeJacksonConfig.",
            ],
            [
                "frontend/ (app, features, components, lib, types)",
                "81",
                "Giao diện React 19, TypeScript và Tailwind CSS chia theo tính năng (series, movies, search, chat, profile, account, lich-su, legal, dat-lai-mat-khau, admin).",
            ],
            [
                "desktop/ (com.dippie.desktop, 9 package)",
                "64",
                "Ứng dụng desktop Dippie Admin (3.605 dòng Java và desktop.css): DippieAdminApp; 4 lớp controller (AdminController có filterCatalog, LoginController, UiTasks, ConnectionState); 13 tệp dao (Dao, RowMapper, AbstractJdbcDao và 10 DAO); 5 tệp db; 2 tệp export CSV; 18 tệp model (13 tệp com.dippie.desktop.model gồm TopRated, MostWatched, DashboardSnapshot, DashboardCounts, RatingRow và 5 tệp com.dippie.desktop.model.catalog với sealed CatalogNode); 12 tệp service và 9 lớp view chỉ phụ thuộc controller, model và export.",
            ],
        ],
        col_widths_mm=[46, 15, 99],
        font_size=9.3,
    )

    rb.h2("3.4. Thiết kế cơ sở dữ liệu")
    rb.p(
        "Lược đồ cơ sở dữ liệu gốc (database/schema/01_init.sql) có 32 bảng quan hệ chuẩn hóa đạt dạng chuẩn "
        "Boyce-Codd (BCNF) trên các cột quan hệ, kèm 14 tệp migration tuần tự từ 02 đến 15 (trong đó "
        "14_query_performance_indexes.sql bổ sung 10 chỉ mục hiệu năng và 15_terms_reports_and_password_reset.sql "
        "thêm cột đồng ý điều khoản cùng 2 bảng playback_reports và password_reset_tokens). Phía backend Spring Boot "
        "tích hợp 5 tệp Flyway migration (V1__baseline_schema.sql đến V5__add_query_performance_indexes.sql) tự động "
        "quản lý phiên bản lược đồ khi chạy MySQL, cùng bộ đối tượng MySQL 8 trong database/objects/ gồm 4 view, 2 hàm "
        "lưu trữ, 3 thủ tục lưu trữ, 4 trigger và 3 tài khoản phân quyền tối thiểu là dippie_app, dippie_readonly và "
        "dippie_admin."
    )
    rb.figure(ASSETS_DIR / "db_overview.png", "Kiến trúc lược đồ cơ sở dữ liệu Dippie (32 bảng chia thành 5 phân nhóm)", width_inches=6.2)

    rb.table(
        "Các bảng chính và ràng buộc trong cơ sở dữ liệu Dippie",
        ["Nhóm bảng", "Bảng tiêu biểu", "Khóa chính, khóa ngoại và ràng buộc chính"],
        [
            ["Tài khoản, Kiểm toán và Hỗ trợ", "users, admin_audit_events, audit_log, playback_reports, password_reset_tokens", "users.id (PK), UNIQUE(username), UNIQUE(email), cột enabled, token_version và terms_accepted_at; audit_log lưu ảnh chụp JSON qua trigger; playback_reports lưu báo lỗi phát video theo trạng thái xử lý; password_reset_tokens lưu mã băm đặt lại mật khẩu có thời hạn."],
            ["Danh mục phân cấp", "catalog_series, catalog_seasons, catalog_episodes, catalog_segments, catalog_scenes", "Khóa ngoại ghép (series_id, season_id) trên catalog_episodes bảo đảm mùa luôn thuộc đúng bộ phim cha; UNIQUE(episode_id, position) và ràng buộc CHECK episode_end_ms > episode_start_ms trên catalog_segments."],
            ["Từ điển, Tài nguyên và Nhập gói", "catalog_genres, catalog_characters, catalog_media_assets, catalog_text_tracks, catalog_packages, catalog_snapshots, 6 bảng nối N-N", "Các bảng nối N-N dùng khóa chính ghép tránh trùng; catalog_packages lưu content_hash (SHA-256) và revision chặn nhập trùng hoặc lùi phiên bản; catalog_snapshots lưu trạng thái đồng bộ chỉ mục."],
            ["Lớp chiếu phim tương thích", "movies, media_assets, movie_genre_terms, movie_genres", "Đồng bộ trong cùng giao dịch nhập danh mục; giúp bộ lọc JPA Specification truy vấn, sắp xếp và phân trang trực tiếp trên SQL với 812 bản ghi phim (6 bộ và 806 tập)."],
            ["Tương tác người dùng", "watch_progress, watchlist_items, favorite_items, movie_ratings", "UNIQUE(user_id, episode_id) trên watch_progress kèm opt_lock_version cho khóa lạc quan và CHECK position_ms >= 0; UNIQUE(user_id, movie_id) trên 3 bảng thư viện; movie_ratings giới hạn điểm nguyên từ 1 đến 10."],
        ],
        col_widths_mm=[30, 48, 82],
        font_size=10.5,
    )

    rb.h2("3.5. Thiết kế giao diện và các luồng nghiệp vụ chính")

    rb.h3("3.5.1. Giao diện trang chủ và danh sách phim bộ")
    rb.p(
        "Giao diện web Dippie chọn nền sáng màu giấy trắng với màu nhấn xanh xô thơm và cỡ chữ dễ đọc cho cả phụ "
        "huynh lẫn trẻ nhỏ. Trang chủ (/) mở đầu bằng khu vực Kho phim hoạt hình, ô tìm nhanh, hàng Các bộ phim có "
        "sẵn nút Lưu xem sau và Yêu thích trên mỗi thẻ, bên dưới là lưới Gợi ý cho bạn kèm 6 nút chọn tâm trạng và "
        "hàng Xem tiếp sau khi đăng nhập. Trang Phim bộ (/series) cho phép tìm theo tên tiếng Việt hoặc tên gốc, lọc "
        "theo 6 thể loại và sắp xếp theo chữ cái, năm phát hành hoặc số tập."
    )
    if (SCREENSHOTS_DIR / "home.png").exists():
        rb.figure(SCREENSHOTS_DIR / "home.png", "Giao diện Trang chủ Dippie (1440x900): khu vực Kho phim hoạt hình và hàng Các bộ phim kèm nút lưu thư viện", width_inches=5.9)
    if (SCREENSHOTS_DIR / "series-list.png").exists():
        rb.figure(SCREENSHOTS_DIR / "series-list.png", "Trang danh sách Phim bộ (/series) với ô tìm kiếm, bộ lọc 6 thể loại, sắp xếp và 6 thẻ hoạt hình", width_inches=5.9)

    rb.h3("3.5.2. Luồng đăng ký, xác thực JWT, đồng bộ khách và các trang tài khoản")
    rb.p(
        "Khi người dùng gửi yêu cầu POST /api/auth/login hoặc /register, ClientIpResolver lấy IP thực theo dải proxy "
        "tin cậy rồi LoginRateLimiter (dùng FixedWindowRateLimiter) kiểm tra giới hạn 30 lượt mỗi phút theo cặp IP "
        "và tài khoản cùng ngưỡng 10 lượt trong 15 phút theo tài khoản. Đăng ký tài khoản mới yêu cầu đánh dấu đồng "
        "ý Điều khoản sử dụng và Chính sách quyền riêng tư (acceptedTerms = true, lưu dấu thời gian terms_accepted_at "
        "và hiển thị đầy đủ tại /dieu-khoan, /quyen-rieng-tu). AuthService kiểm tra mật khẩu qua PasswordPolicy "
        "(tối thiểu 6 ký tự cho người dùng, 16 ký tự cho quản trị viên, tối đa 72 byte UTF-8), so khớp BCrypt kèm "
        "băm giả khi sai tài khoản để hạn chế dò tên đăng nhập, kiểm tra user.isEnabled() rồi cấp JWT HS256 gắn "
        "token_version."
    )
    rb.p(
        "Phía trình duyệt, khi phát hiện có dữ liệu lưu tạm của khách trong localStorage lúc đăng nhập hoặc đăng ký, "
        "hộp thoại GuestSyncDialog hiện số lượng mục đang có trên máy và hỏi người dùng xác nhận Chuyển hay Xóa dữ "
        "liệu tạm để tránh gộp nhầm phim của thành viên khác khi dùng chung máy. Ngoài ra, tài khoản có đủ các luồng "
        "vận hành thực tế gồm đăng xuất phía máy chủ (POST /api/auth/logout qua SessionLogoutController tăng "
        "token_version thu hồi ngay mọi JWT cũ), tự xóa tài khoản tại /account (DELETE /api/users/me qua "
        "AccountErasureController), quên và đặt lại mật khẩu qua email (/quen-mat-khau, /dat-lai-mat-khau, "
        "PasswordResetController dùng MailDeliveryStrategy: gửi SMTP thật hoặc xuất tệp .eml vào target/dippie-mail "
        "khi chạy demo), trang Lịch sử xem (/lich-su) có nút xóa sạch toàn bộ lịch sử (DELETE "
        "/api/users/watch-progress) và thanh OfflineBanner báo rõ khi trình duyệt mất kết nối mạng."
    )
    if (ASSETS_DIR / "screenshot_login.png").exists():
        rb.figure(ASSETS_DIR / "screenshot_login.png", "Hộp thoại Đăng nhập / Đăng ký của Dippie", width_inches=3.6)
    if (SCREENSHOTS_DIR / "legal-terms.png").exists():
        rb.figure(SCREENSHOTS_DIR / "legal-terms.png", "Trang Điều khoản sử dụng (/dieu-khoan) với mục lục rõ ràng cho phụ huynh và chuyển nhanh sang Chính sách quyền riêng tư", width_inches=5.9)

    rb.h3("3.5.3. Luồng xem phim, chọn nguồn phát và lưu tiến độ xem")
    rb.p(
        "Trang chi tiết bộ phim (/series/:id) gọi GET /api/catalog/series/{id} và GET /api/catalog/episodes/{id} để "
        "nạp danh sách mùa, tập, phân đoạn và nguồn phát. Nếu tập có tệp video cục bộ hoặc Drive khả dụng "
        "(available = true), frontend xin vé phát 5 phút qua POST /api/media/grant/{assetId} rồi phát trên thẻ HTML5 "
        "<video> bằng HTTP Byte Range (GET /api/media/stream/{assetId}). Nếu không có tệp cục bộ nhưng có YouTube "
        "nhúng được (canEmbed = true), component YouTubePlayer khởi tạo YouTube IFrame Player API với tham số "
        "start=<giây> và enablejsapi=1."
    )
    rb.p(
        "Khi người dùng tạm dừng, đổi tập hoặc xem hết video, hook useWatchProgress gửi PUT "
        "/api/users/watch-progress/{episodeId} mang theo assetId (ví dụ youtube:<videoId>), positionMs và "
        "expectedVersion. Backend dùng WatchProgressRepository.advanceVersion cập nhật có điều kiện "
        "opt_lock_version = expectedVersion; nếu hai thiết bị ghi cùng lúc gây lệch phiên bản, máy chủ trả 409 "
        "Conflict để trình duyệt nạp lại tiến độ mới nhất. Ngưỡng coi như xem xong nằm tập trung trong "
        "CompletionPolicy (đạt 95% thời lượng hoặc còn không quá 60 giây cuối)."
    )
    if (SCREENSHOTS_DIR / "series-doraemon.png").exists():
        rb.figure(SCREENSHOTS_DIR / "series-doraemon.png", "Trang chi tiết bộ phim Doraemon (/series/ser_doraemon) với thanh Lưu xem sau, Yêu thích, Chấm điểm và 7 mùa", width_inches=5.9)
    if (ASSETS_DIR / "screenshot_larva_player.png").exists():
        rb.figure(ASSETS_DIR / "screenshot_larva_player.png", "Màn hình xem tập Kem (Larva) mở tại mốc 00:33 cùng danh sách mùa và tập", width_inches=5.6)
    if (ASSETS_DIR / "screenshot_ori_external.png").exists():
        rb.figure(ASSETS_DIR / "screenshot_ori_external.png", "Xử lý tập Công chúa Ori không cho phát nhúng bằng liên kết mở trên YouTube", width_inches=5.6)

    rb.h3("3.5.4. Luồng đánh giá 1 đến 10 và cập nhật điểm cộng đồng")
    rb.p(
        "Người dùng đã đăng nhập có thể chấm điểm nguyên từ 1 đến 10 hoặc xóa đánh giá trên cả banner bộ phim "
        "(SeriesDetailBanner) lẫn khung điều khiển tập (ItemLibraryPanel) tại /series/:id. Như đã theo dõi chi tiết "
        "ở mục 3.2.1, MovieRatingService ghi bản ghi trong giao dịch REQUIRES_NEW và WatchlistContext phát sự kiện "
        "dippie:rating-changed để điểm trung bình (averageRating, ratingCount) trên màn hình xem phim cũng như thứ tự "
        "sắp xếp \"Đánh giá cao nhất\" trên /search đồng bộ ngay."
    )

    rb.h3("3.5.5. Luồng gợi ý theo sở thích và tâm trạng")
    rb.p(
        "Khi nhận yêu cầu POST /api/recommend, RecommendationService lấy tín hiệu của người xem từ "
        "ViewerSignalsRepository (tập liked gồm Yêu thích, đánh giá từ 8 trở lên và tập đã xem; tập disliked gồm đánh "
        "giá từ 1 đến 4; tập seen gồm các tập đã tương tác) rồi chọn một trong hai chiến lược:"
    )
    rb.bullet(
        "AffinityRecommendationStrategy được chọn khi người dùng không truyền tâm trạng. Điểm của từng tập ứng viên "
        "chưa xem tính theo công thức: score = 0,1 x điểm_đánh_giá + 2,0 x tỷ_lệ_trùng_thể_loại + 1,5 x "
        "tỷ_lệ_trùng_chủ_đề + 1,2 x tỷ_lệ_trùng_nhân_vật + 0,8 (nếu cùng bộ phim yêu thích) trừ điểm phạt khi trùng "
        "bộ phim, chủ đề hoặc nhân vật bị chấm thấp (tối đa trừ 3,1). Điểm làm tròn HALF_EVEN 4 chữ số thập phân; "
        "khách mới chưa có lịch sử nhận 1 tập đại diện cho mỗi bộ trong 6 bộ qua DiverseSelection.roundRobin, còn "
        "người đã có lịch sử bị giới hạn tối đa 2 tập mỗi bộ để kết quả đỡ bị lặp."
    )
    rb.bullet(
        "MoodRecommendationStrategy chạy khi người dùng bấm một trong 6 chip tâm trạng trên trang chủ (Vui nhộn, Ấm áp "
        "gia đình, Nhẹ nhàng trước giờ ngủ, Phiêu lưu khám phá, Tình bạn, Tập ngắn dưới 10 phút) hoặc gõ chuỗi mô tả "
        "tâm trạng. Chiến lược loại bỏ tuyển tập dài và tập không nhúng được, chấm điểm theo ngữ cảnh (riêng chế độ "
        "trước giờ ngủ giới hạn tối đa 2/6 tập cùng một bộ) và tự quay về AffinityRecommendationStrategy nếu không tìm "
        "ra ứng viên phù hợp."
    )

    rb.h3("3.5.6. Luồng hỏi đáp trợ lý CineBot từ câu hỏi tới luồng SSE")
    rb.p(
        "Câu hỏi gửi tới POST /api/chat đi qua 5 bước ngay trong Java: (1) RequestBodySizeLimitFilter và "
        "LocalChatController giới hạn dung lượng thân yêu cầu tối đa 256 KB (trả HTTP 413 nếu vượt), "
        "ChatRequestValidator kiểm tra trường hợp lệ và độ dài câu hỏi không quá 4.000 ký tự, còn ChatStreamGuard "
        "giới hạn tần suất 20 lượt mỗi phút và tối đa 16 luồng SSE chạy đồng thời; (2) IntentParser phân loại ý định "
        "(chào hỏi, thống kê số tập, gợi ý theo thời lượng hay độ tuổi, tìm phim tương tự, tìm cảnh hoặc xử lý câu hỏi "
        "nối tiếp như \"Cảnh sau đó thì sao\", \"Không, tập khác\"); (3) LocalCatalogSearch truy vấn trên chỉ mục "
        "BM25 LexicalIndex hỗ trợ không dấu, bigram, sửa lỗi gõ và lọc ngưỡng để từ chối câu ngoài danh mục; (4) "
        "AnswerGenerator tạo câu trả lời qua TemplateAnswerGenerator mặc định hoặc DeepSeekAnswerGenerator kèm tối đa "
        "3 trích đoạn bằng chứng; (5) LocalChatController đọc luồng ChatEvent và đẩy từng khung SSE (metadata, token, "
        "done) về CineBot.tsx để hiện chữ dần cùng thẻ tập gắn mốc giây ?t=ms."
    )
    rb.figure(ASSETS_DIR / "cinebot_flow.png", "Sơ đồ luồng xử lý câu hỏi trợ lý CineBot", width_inches=6.2)
    if (ASSETS_DIR / "screenshot_cinebot.png").exists():
        rb.figure(ASSETS_DIR / "screenshot_cinebot.png", "Hộp thoại CineBot trả lời câu 'Cảnh Red đội kem' kèm nút Xem tập ở mốc 00:33", width_inches=3.9)

    rb.h3("3.5.7. Giao diện ứng dụng quản trị desktop Dippie Admin (JavaFX 21)")
    rb.p(
        "Song song với 5 trang quản trị trên web (/admin/import, /admin/users, /admin/stats, /admin/audit và "
        "/admin/reports), ứng dụng desktop Dippie Admin đọc và cập nhật trực tiếp cùng tệp cơ sở dữ liệu H2 hoặc "
        "MySQL thông qua JDBC. Năm ảnh chụp thực tế dưới đây ghi lại toàn bộ giao diện hoạt động của Dippie Admin "
        "trên dữ liệu 6 bộ phim và 806 tập thật: màn hình đăng nhập BCrypt, tab Tổng quan hiển thị cùng nhóm số liệu "
        "thống kê với quản trị web kèm biểu đồ cột phân bố điểm 1 đến 10, tab Danh mục dùng cây Composite trên "
        "TreeView, tab Người dùng có nút Đổi vai trò, Khóa tài khoản, Hoàn tác (Command) và Xuất CSV, cùng tab Đánh "
        "giá lọc theo bộ phim và mức điểm."
    )
    if (DESKTOP_SHOTS_DIR / "login.png").exists():
        rb.figure(DESKTOP_SHOTS_DIR / "login.png", "Màn hình đăng nhập Dippie Admin (JavaFX 21), kiểm tra mật khẩu BCrypt và vai trò ROLE_ADMIN qua JDBC", width_inches=5.4)
    if (DESKTOP_SHOTS_DIR / "dashboard.png").exists():
        rb.figure(DESKTOP_SHOTS_DIR / "dashboard.png", "Tab Tổng quan trên Dippie Admin dùng chung cơ sở dữ liệu với quản trị Web: 5 thẻ số liệu, bảng 10 tập điểm cao nhất và biểu đồ BarChart phân bố điểm 1 đến 10", width_inches=5.9)
    if (DESKTOP_SHOTS_DIR / "catalog.png").exists():
        rb.figure(DESKTOP_SHOTS_DIR / "catalog.png", "Tab Danh mục trên Dippie Admin: duyệt cây Composite Bộ phim, Mùa và Tập trên TreeView kèm tóm tắt và liên kết nguồn phát", width_inches=5.9)
    if (DESKTOP_SHOTS_DIR / "users.png").exists():
        rb.figure(DESKTOP_SHOTS_DIR / "users.png", "Tab Người dùng trên Dippie Admin: lọc trực tiếp trên TableView, đổi vai trò, khóa tài khoản, Hoàn tác (Command) và Xuất CSV", width_inches=5.9)
    if (DESKTOP_SHOTS_DIR / "ratings.png").exists():
        rb.figure(DESKTOP_SHOTS_DIR / "ratings.png", "Tab Đánh giá trên Dippie Admin: lọc theo bộ phim, mức điểm, xuất CSV và xóa lượt đánh giá cần kiểm duyệt", width_inches=5.9)

    rb.h2("3.6. Áp dụng kiến thức môn học (OOP và mẫu thiết kế)")
    rb.p(
        "Đồ án môn Lập trình hướng đối tượng Java đòi hỏi các cơ chế OOP, thư viện chuẩn (ngoại lệ, luồng I/O, "
        "generics, collections, lớp nội, JavaFX, JDBC) và các mẫu thiết kế GoF phải có mặt trong mã nguồn thật để "
        "kiểm chứng được khi chạy ứng dụng. Thống kê trực tiếp từ mã nguồn chính của backend (backend/springboot/src/main/java) "
        "ghi nhận 4 lớp trừu tượng (CatalogRecord, DomainException, AbstractLibraryListService, LibraryEntry), 35 "
        "giao diện (gồm 4 sealed interface Java 21), 11 lớp thực thể kế thừa CatalogRecord, 11 lớp ngoại lệ kế thừa "
        "trực tiếp DomainException (cộng thêm CatalogInputException kế thừa InvalidRequestException) và 72 chú thích "
        "@Override. Phía module desktop/ có thêm 3 lớp trừu tượng (AbstractJdbcDao, DataAccessException, DataPane), 5 "
        "giao diện (gồm sealed interface CatalogNode) và 30 @Override."
    )

    rb.table(
        "Ánh xạ kiến thức môn học và các mẫu thiết kế GoF vào mã nguồn Dippie",
        ["Kiến thức / Mẫu thiết kế", "Nơi áp dụng (Lớp / Gói)", "Cách vận dụng trong dự án"],
        [
            ["Xử lý ngoại lệ (Exceptions)", "Backend: com.dippie.exception gồm DomainException (11 lớp con trực tiếp gồm RequestBodyTooLargeException cùng CatalogInputException), ChatCapacityException, RangeNotSatisfiableException và GlobalExceptionHandler (com.dippie.controller.web); Desktop: DataAccessException (Connection, Query, Transaction, Conflict) và AuthException", "Phân cấp ngoại lệ miền gắn sẵn HttpStatus và ánh xạ tập trung về phản hồi JSON hoặc thông báo tiếng Việt trên JavaFX; dịch SQLException sang lỗi dễ hiểu; dùng try-with-resources đóng tệp và kết nối JDBC."],
            ["Luồng vào/ra (I/O Streams)", "Backend: ResolvedMediaStream (FileChannel, ByteBuffer, OutputStream), RequestBodySizeLimitFilter và LocalChatController (InputStream đếm byte giới hạn dung lượng), CatalogImportService và CatalogJsonWriter, FileOutboxMailDeliveryStrategy (xuất tệp .eml UTF-8), DeepSeekAnswerGenerator (InputStream, BufferedReader); Desktop: CsvExporter (BufferedWriter UTF-8 BOM, Files.move) và DatabaseFactory", "Đọc và phát tệp video theo khối 64 KB; đọc thân yêu cầu có chặn dung lượng; xuất tệp thư .eml; đọc luồng SSE từ DeepSeek; xuất tệp CSV chuẩn UTF-8 BOM mở đúng tiếng Việt trong Excel và chặn công thức bảng tính."],
            ["Kế thừa (Inheritance)", "Backend: DomainException (11 lớp con trực tiếp), CatalogRecord (11 thực thể danh mục), LibraryEntry (WatchlistItem, FavoriteItem), AbstractLibraryListService (WatchlistService, FavoriteService); Desktop: DataPane (4 tab), AbstractJdbcDao (10 DAO)", "Đưa các trường chung (mã định danh, thời gian cập nhật, khóa lạc quan) và khung xử lý lên lớp cha trừu tượng, để lớp con chỉ cài đặt phần khác biệt."],
            ["Đa hình (Polymorphism)", "Backend: RecommendationStrategy, MediaLocationStrategy, AnswerGenerator, MailDeliveryStrategy, CatalogSearch, CatalogIndexPublisher, GlobalExceptionHandler.handleDomain(DomainException); Desktop: DataPane.refresh(), CatalogNode, Command, Dao<T, ID>, CsvStrategy<T>", "Bên gọi chỉ làm việc với giao diện hoặc lớp cha; hành vi thực tế được chọn lúc chạy tùy theo đối tượng cụ thể."],
            ["Lập trình tổng quát (Generics)", "Backend: ApiResponse<T>, AdminPageResponse<T>, LibraryEntryRepository<E extends LibraryEntry>, AbstractLibraryListService<E>, RatingLookup.withSummaries<T, R>, DiverseSelection; Desktop: Dao<T, ID>, AbstractJdbcDao<T, ID>, RowMapper<T>, AdminQuery<T>, CsvStrategy<T>", "Kiểm tra kiểu ngay khi biên dịch, dùng chung khung phản hồi, repository thư viện, truy vấn JDBC, Proxy phiên và xuất CSV cho nhiều thực thể."],
            ["Collections và cấu trúc dữ liệu", "Backend: DiverseSelection (ArrayList, ArrayDeque, LinkedHashMap), FixedWindowRateLimiter (LinkedHashMap), LexicalIndex (HashMap chia nhóm theo độ dài từ), CatalogReadModel bất biến, ConcurrentHashMap, AtomicReference; Desktop: ObservableList, FilteredList, SortedList, LinkedHashMap", "Chọn cấu trúc dữ liệu khớp bài toán truy xuất: chỉ mục đảo BM25, hàng đợi xoay vòng đa dạng hóa bộ phim, tập tra cứu O(1) và bảng TableView lọc, sắp xếp trực tiếp."],
            ["Lớp lồng nhau và lớp ẩn danh", "Backend: LexicalIndex.Posting, ChatRequestValidator.ChatValidationException, ChatStreamGuard.Permit, DiscoverRequest.Rejection, WatchProgressRepository.ContextRow, PlaybackContextResolver.PlaybackAssetId; Desktop: DataAccessException.{Connection,Query,Transaction,Conflict}, JdbcDatabase.Work, Task ẩn danh trong UiTasks", "Gói các kiểu dữ liệu phụ trợ và ngoại lệ nội bộ ngay trong lớp dùng chúng để không làm rối không gian tên package."],
            ["Composite (GoF Cấu trúc)", "Desktop (com.dippie.desktop.model.catalog): sealed interface CatalogNode (Component) với SeriesNode và SeasonNode (Composite), EpisodeNode (Leaf), hiển thị trên TreeView<CatalogNode> trong CatalogPane", "Biểu diễn thống nhất cây Bộ phim, Mùa và Tập để lọc tìm kiếm đệ quy giữ nguyên đường dẫn cha và đọc chi tiết bằng đối sánh mẫu switch."],
            ["Adapter (GoF Cấu trúc)", "Backend: ActiveViewerLookup cài bởi UserViewerAdapter; MediaFileAvailability cài bởi MediaFileAvailabilityAdapter; MoviePresentationLookup cài bởi MoviePresentationAdapter; RatingLookup cài bởi RatingSummaries; SecurityContextCurrentViewer; DeepSeekAnswerGenerator; Desktop: CatalogPane.toItem(...)", "Chuyển đổi dữ liệu giữa các tính năng độc lập (security/user, catalog/media, catalog/movie, library) và đổi cây CatalogNode sang TreeItem của JavaFX."],
            ["Facade (GoF Cấu trúc)", "Backend: CatalogImportService, CatalogQueryService, ChatService, DiscoverService, RecommendationService, MediaStreamService, AuthService, PlaybackReportService, PasswordResetService, GlobalExceptionHandler; Desktop: AdminServices", "Đặt một cổng gọi đơn giản che các bước phức tạp phía sau như kiểm tra SHA-256, chạy giao dịch, làm mới chỉ mục hoặc lắp ghép 10 DAO với 5 dịch vụ desktop."],
            ["Proxy (GoF Cấu trúc)", "Backend: CatalogReadModelCache (Caching Proxy đứng trước CatalogReadModelLoader), JwtAuthenticationFilter, RequestBodySizeLimitFilter và PlaybackAccess (Access-Control Proxy), Proxy Spring AOP (@Transactional); Desktop: AdminQuery<T> (Protection Proxy kiểm tra AdminSession.requireCurrent trước mỗi truy vấn đọc)", "Chặn trước tài nguyên thật để lưu đệm không khóa đọc, kiểm tra quyền truy cập và trạng thái xuất bản, giới hạn dung lượng thân yêu cầu, thu hồi phiên quản trị đã bị đổi quyền trên web và quản lý ranh giới giao dịch."],
            ["Observer (GoF Hành vi)", "Backend: CatalogImportedEvent (CatalogImportService phát tới CatalogReadModelCache, CatalogImportAuditListener, LocalCatalogSearch); Desktop: AdminController.{usersChangedProperty, ratingsChangedProperty, undoAvailableProperty} tới WorkspacePane và UsersPane; UiTasks.connectionProperty tới StatusBar", "Tách nguồn phát sự kiện khỏi các thành phần lắng nghe để bộ đệm, chỉ mục tìm kiếm, nhật ký kiểm toán và các tab JavaFX tự làm mới độc lập."],
            ["Strategy (GoF Hành vi)", "Backend: RecommendationStrategy (AffinityRecommendationStrategy, MoodRecommendationStrategy), MediaLocationStrategy (DriveCacheLocation, DriveMountLocation, LocalMediaLocation), AnswerGenerator (TemplateAnswerGenerator, DeepSeekAnswerGenerator), MailDeliveryStrategy (SmtpMailDeliveryStrategy, FileOutboxMailDeliveryStrategy), CatalogSearch; Desktop: CsvStrategy<T> kết hợp CsvExporter", "Đóng gói từng họ thuật toán gợi ý, tìm tệp, sinh câu trả lời, gửi thư và trích cột CSV thành các đối tượng có thể đổi qua lại mà không sửa lớp gọi."],
            ["Command (GoF Hành vi)", "Backend: WatchProgressCommand (đóng gói lệnh ghi tiến độ xem), ChatStreamGuard.Permit (AutoCloseable); Desktop (com.dippie.desktop.service): giao diện Command (execute/undo), AccountCommand (lưu trạng thái trước/sau) và CommandHistory nối nút Hoàn tác trong UsersPane", "Gói thao tác thay đổi tài khoản hoặc cập nhật tiến độ thành đối tượng lệnh, hỗ trợ hoàn tác an toàn trong giao dịch JDBC có kiểm tra token_version."],
            ["State (GoF Hành vi)", "Backend: CatalogSnapshot cùng enum CatalogSnapshot.Status (PENDING, BUILDING, READY, BLOCKED, SYNC_FAILED với completeIndexSync, recordRetry, supersede), sealed ChatEvent cho các pha SSE; Desktop: enum ConnectionState (NOT_CHECKED, CHECKING, CONNECTED, DISCONNECTED với onRequest())", "Quản lý tường minh các trạng thái hợp lệ và quy tắc chuyển trạng thái của tiến trình đồng bộ chỉ mục, luồng SSE và thanh trạng thái kết nối CSDL."],
            ["Template Method và Factory Method (GoF)", "Backend: AbstractLibraryListService<E> (khung list, add, remove và phương thức nhà máy newEntry(...)) cài bởi WatchlistService và FavoriteService; Desktop: AbstractJdbcDao<T, ID> và DataPane", "Cố định khung xử lý chung ở lớp cha trừu tượng và để lớp con cài đặt bước khởi tạo thực thể hoặc hiển thị bảng riêng."],
            ["Creational (Singleton, Factory, Builder)", "Desktop: enum Singleton ConnectionProvider.INSTANCE và DatabaseFactory (chọn H2 demo hoặc MySQL từ desktop.properties); Backend: Static Factory (ApiResponse.ok, WatchProgress.start), Builder (ResponseEntity, Jwts.builder)", "Quản lý tập trung điểm mở kết nối JDBC, tách logic chọn H2/MySQL khỏi phần còn lại và tạo đối tượng bất biến gọn gàng."],
            ["Kiến trúc MVC", "Web: model + repository (Model), React 19 SPA + dto (View), controller (Controller), service ở giữa; Desktop: model + dao + db (Model), 9 lớp view chỉ gọi controller/model/export (View), controller (Controller), service ở giữa", "Tách biệt dữ liệu, giao diện và điều phối luồng kèm quy tắc phụ thuộc tầng chặt trên cả web lẫn desktop (chi tiết ở mục 3.2)."],
            ["JavaFX GUI và Event Handling", "Desktop (9 lớp view): HeaderBar, LoginPane, WorkspacePane (4 tab), DashboardPane (BarChart và Timeline 30 giây), CatalogPane (TreeView), UsersPane, RatingsPane, StatusBar", "Xây dựng giao diện desktop hướng sự kiện bằng JavaFX 21, chạy JDBC và ghi tệp trên luồng nền UiTasks và ràng buộc disableProperty để tránh bấm lặp."],
            ["JDBC (PreparedStatement và Transaction)", "Desktop (com.dippie.desktop.db và .dao): JdbcDatabase (read/transaction với setAutoCommit(false), commit, rollback), Dao<T, ID>, RowMapper<T>, AbstractJdbcDao<T, ID> và 10 DAO", "Truy vấn có tham số chống SQL Injection, khóa dòng FOR UPDATE, gộp cập nhật dữ liệu và ghi kiểm toán trong cùng một giao dịch trên cả H2 và MySQL."],
        ],
        col_widths_mm=[34, 58, 68],
        font_size=9.5,
    )

    rb.h3("3.6.1. Bốn trụ cột OOP trong mã nguồn Dippie")
    rb.p(
        "Bốn đoạn mã ngắn sau trích nguyên văn từ kho mã nguồn cuối của Dippie (các dòng lược bớt được đánh dấu bằng "
        "\"...\"), minh họa lần lượt kế thừa (extends), giao diện (interface), lớp trừu tượng (abstract) và tính đa "
        "hình. Ở module desktop JavaFX, cùng các cơ chế này xuất hiện trực tiếp trong UsersPane extends DataPane "
        "(DataPane extends BorderPane), giao diện Dao<T, ID> và lớp trừu tượng AbstractJdbcDao<T, ID>."
    )

    rb.code_block(
        "Đoạn mã 1. Kế thừa (extends): exception/DomainException.java & exception/NotFoundException.java",
        """// lớp cha trừu tượng cho mọi lỗi nghiệp vụ trong Dippie, mỗi lớp con tự gắn sẵn HTTP status và header riêng
// nhờ vậy GlobalExceptionHandler chỉ cần bắt đúng 1 chỗ là lo được hết
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final transient Map<String, String> headers;

    protected DomainException(HttpStatus status, String message) {
        this(status, message, Map.of());
    }
    ...
    public HttpStatus status() {
        return status;
    }
}

// không tìm thấy dữ liệu cần lấy, trả 404
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}""",
        font_size=9.0
    )

    rb.code_block(
        "Đoạn mã 2. Giao diện (interface): service/recommendation/RecommendationStrategy.java & desktop/dao/Dao.java",
        """// kiểu Strategy cho các cách gợi ý phim, thêm kiểu gợi ý mới thì viết thêm 1 lớp là xong
public interface RecommendationStrategy {

    RecommendationResult recommend(RecommendationContext context);
}

// interface chung cho mọi DAO chỉ đọc theo thực thể T và kiểu khóa chính ID
public interface Dao<T, ID> {
    Optional<T> findById(ID id);

    List<T> findAll();

    long count();
}""",
        font_size=9.0
    )
    rb.p(
        "Trong gói com.dippie.service.recommendation, hai lớp AffinityRecommendationStrategy và "
        "MoodRecommendationStrategy cài đặt RecommendationStrategy; còn ở module desktop, 10 lớp DAO kế thừa "
        "AbstractJdbcDao<T, ID> cài đặt Dao<T, ID>."
    )

    rb.code_block(
        "Đoạn mã 3. Lớp trừu tượng (abstract / Template Method): service/library/AbstractLibraryListService.java & service/library/FavoriteService.java",
        """// lớp cha dùng Template Method với Factory Method qua newEntry() cho FavoriteService và WatchlistService xài chung luồng xem, thêm, xóa
public abstract class AbstractLibraryListService<E extends LibraryEntry> {
    ...
            entry = requiresNewTransaction.execute(status -> repository.saveAndFlush(
                    newEntry(viewer.userId(), movie.getId(), LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS))
            ));
    ...
    // chỗ Factory Method để lớp con trả đúng FavoriteItem hay WatchlistItem
    protected abstract E newEntry(Long userId, String movieId, LocalDateTime addedAt);
}

// danh sách yêu thích, chỉ cần cài newEntry() trả FavoriteItem còn luồng chung ăn sẵn ở lớp cha
@Service
public class FavoriteService extends AbstractLibraryListService<FavoriteItem> {
    ...
    @Override
    protected FavoriteItem newEntry(Long userId, String movieId, LocalDateTime addedAt) {
        return new FavoriteItem(userId, movieId, addedAt);
    }
}""",
        font_size=9.0
    )

    rb.code_block(
        "Đoạn mã 4. Đa hình (Polymorphism): service/recommendation/RecommendationService.java & controller/web/GlobalExceptionHandler.java",
        """    // có mood hay moodPreset thì chạy MoodRecommendationStrategy, không thì chạy AffinityRecommendationStrategy
    public RecommendationResult recommend(RecommendationRequest request) {
        ...
        boolean wantsMood = request.moodPreset() != null || (request.mood() != null && !request.mood().isBlank());
        RecommendationStrategy strategy = wantsMood ? mood : affinity;
        var result = strategy.recommend(context);
        ...
    }

    // kiểu đa hình, mỗi lớp con DomainException tự mang sẵn HTTP status và header (như Retry-After) nên chỉ việc đọc ra trả
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException exception) {
        HttpHeaders headers = new HttpHeaders();
        exception.headers().forEach(headers::add);
        return ResponseEntity.status(exception.status())
                .headers(headers)
                .body(ApiResponse.failure(exception.getMessage()));
    }""",
        font_size=9.0
    )

    rb.h3("3.6.2. Hai đoạn mã minh họa Proxy/Observer và luồng vào/ra (Java NIO)")
    rb.p(
        "Ngoài bốn trụ cột OOP cơ bản, hai đoạn mã sau trích từ mã nguồn cuối cho thấy cách nhóm kết hợp Caching "
        "Proxy với Observer trong CatalogReadModelCache và dùng FileChannel kết hợp try-with-resources để ghi từng "
        "khối 64 KB video trong ResolvedMediaStream."
    )

    rb.code_block(
        "Đoạn mã 5. Caching Proxy kết hợp Observer (service/catalog/CatalogReadModelCache.java)",
        """@Component
public class CatalogReadModelCache {

    private static final Logger log = LoggerFactory.getLogger(CatalogReadModelCache.class);

    private final AtomicReference<CatalogReadModel> model = new AtomicReference<>();
    private final CatalogReadModelLoader loader;
    ...
    public CatalogReadModel current() {
        CatalogReadModel current = model.get();
        return current == null ? initialize() : current;
    }
    ...
    @EventListener
    public synchronized void refresh(CatalogImportedEvent event) {
        try {
            model.set(loader.load());
        } catch (RuntimeException exception) {
            invalidate();
            log.error("Không thể làm mới bộ nhớ đệm danh mục cho bản chụp {}", event.snapshotId(), exception);
        }
    }
}""",
        font_size=9.0
    )

    rb.code_block(
        "Đoạn mã 6. Đọc và ghi tệp từng khối 64 KB bằng FileChannel (model/media/ResolvedMediaStream.java)",
        """    // ghi đồng bộ từng khúc 64KB bằng FileChannel thay vì async stream, kẻo video dài bị Spring cắt ngang vì async timeout
    public void writeRange(long start, long length, OutputStream outputStream) throws IOException {
        if (length <= 0) {
            return;
        }
        byte[] buffer = new byte[CHUNK_SIZE];
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.position(start);
            long remaining = length;
            // chặn limit của buffer theo số byte còn lại kẻo đọc lố ra ngoài Range khách xin
            while (remaining > 0) {
                byteBuffer.clear();
                byteBuffer.limit((int) Math.min(CHUNK_SIZE, remaining));
                int bytesRead = channel.read(byteBuffer);
                if (bytesRead <= 0) {
                    break;
                }
                outputStream.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
            outputStream.flush();
        }
    }""",
        font_size=9.0
    )

    # =======================================================================
    # CHƯƠNG 4. TỔ CHỨC NHÓM VÀ QUY TRÌNH PHÁT TRIỂN
    # =======================================================================
    rb.h1("Chương 4. Tổ chức nhóm và quy trình phát triển", break_before=True)

    rb.h2("4.1. Cơ cấu tổ chức và vai trò thành viên")
    rb.p(
        "Nhóm có 4 thành viên. Hai bạn giữ phần Java chính: một bạn làm backend Spring Boot cùng CineBot, một bạn "
        "làm ứng dụng Dippie Admin bằng JavaFX và JDBC cùng cơ sở dữ liệu. Hai bạn còn lại lo frontend và khâu dữ liệu, "
        "triển khai, đồng thời nhận thêm vài phần Java nhỏ có test riêng. Mọi người thống nhất API qua CONTRACT.md "
        "và đọc chéo mã của nhau trước khi gộp."
    )
    rb.table(
        "Phân vai của 4 thành viên trong nhóm Dippie",
        ["Thành viên", "Vai trò", "Phạm vi phụ trách chính"],
        [
            [team.label(1), "Nhóm trưởng, backend Spring Boot, CineBot và gợi ý", "Thiết kế kiến trúc Spring Boot 3.5; bảo mật JWT và rà soát OWASP; đăng xuất thu hồi token, xóa tài khoản, đặt lại mật khẩu, báo lỗi phát; tiến độ xem và danh mục; chỉ mục BM25, luồng CineBot SSE và hai chiến lược gợi ý; điều phối gộp nhánh."],
            [team.label(2), "Dippie Admin (JavaFX, JDBC), cơ sở dữ liệu và kiểm thử", "Xây dựng ứng dụng desktop Dippie Admin bằng JavaFX 21 và JDBC thuần (DAO, Composite, Command, Strategy); thiết kế 32 bảng MySQL/H2, Flyway, view, hàm, thủ tục, trigger và đo tải; viết test JUnit cho desktop và các phần backend dùng chung CSDL."],
            [team.label(3), "Frontend React, trải nghiệm người dùng, phụ phần Java", "Xây dựng giao diện React 19, TypeScript và Tailwind CSS; trình phát YouTube, hộp thoại đồng bộ dữ liệu khách, trang lịch sử xem, trang pháp lý, báo mất mạng; sửa lỗi UX qua 3 vòng. Phần Java: các record yêu cầu và phản hồi của thư viện, đánh giá trong com.dippie.dto.library cùng test API trong com.dippie.controller.library."],
            [team.label(4), "Dữ liệu phim, triển khai, kịch bản demo, phụ phần Java", "Chuẩn hóa dữ liệu 6 bộ phim (806 tập, 1.705 phân đoạn); chuẩn bị CHAY_DIPPIE.bat, HUONG_DAN_CHAY.md, Docker Compose, Caddy HTTPS, CI và kịch bản demo. Phần Java: kiểm tra gói danh mục trước khi nhập (CatalogPackageValidator) và test đi kèm."],
        ],
        col_widths_mm=[28, 42, 90],
        font_size=10.5,
    )

    rb.h2("4.2. Phân công công việc và minh chứng đóng góp")
    rb.table(
        "Đầu việc và minh chứng đóng góp của từng thành viên",
        ["Thành viên", "Các đầu việc chính đã thực hiện", "Minh chứng trong kho mã nguồn"],
        [
            [team.label(1), "Khởi tạo backend Spring Boot 3.5; cài đặt bảo mật JWT, tokenVersion, LoginRateLimiter, FixedWindowRateLimiter, RequestBodySizeLimitFilter và xử lý các mục OWASP; xây dựng CatalogImportService, CatalogReadModelCache, WatchProgressService, MediaStreamController; cài LexicalIndex BM25, NameSimilarity, DiscoverService, đường ống CineBot SSE (IntentParser, ChatStreamGuard, Template/DeepSeek AnswerGenerator) và gợi ý Affinity, Mood; viết CONTRACT.md.", "Gói com.dippie.security, com.dippie.exception cùng các tầng controller, service, repository, model và dto cho account, catalog, progress, media, assistant, recommendation; backend/springboot/CONTRACT.md; bộ đối chiếu CatalogSearchParityTest (20 truy vấn cố định); 589 ca kiểm thử backend (1 ca bỏ qua do symlink trên Windows)."],
            [team.label(2), "Phát triển Dippie Admin: đăng nhập kiểm tra BCrypt qua JDBC, tab Tổng quan, Danh mục (TreeView theo Composite), Người dùng (Hoàn tác bằng Command), Đánh giá và xuất CSV; hoàn thiện lược đồ 32 bảng (01_init.sql), 14 migration SQL, 5 Flyway migration (V1 đến V5), 4 view, 2 hàm, 3 thủ tục, 4 trigger và đo tải mốc 100.000 tập.", "Module desktop/ (64 tệp Java, 3.605 dòng, 5 lớp JUnit 5 đạt 33/33 ca); thư mục database/ và db/migration/; báo cáo đo tải MySQL."],
            [team.label(3), "Xây dựng các trang /, /series, /series/:id, /search, /profile, /account, /lich-su, /dieu-khoan, /quyen-rieng-tu, /quen-mat-khau, /dat-lai-mat-khau và /admin/* (5 mục); tích hợp YouTubePlayer.tsx, thanh 6 tâm trạng, chấm điểm 1 đến 10, GuestSyncDialog, OfflineBanner, PlaybackReportButton. Phía Java viết RatingRequest, MovieIdRequest, LibraryItemView, MovieRatingView và LibraryControllerTest.", "81 tệp trong frontend/ (22.104 dòng); 29 bộ kiểm thử frontend/tests/*_suite.mjs với 286 ca; gói com.dippie.dto.library và LibraryControllerTest (gói kiểm thử com.dippie.controller.library); điểm UX phần Thư viện và Đánh giá đạt 9/10."],
            [team.label(4), "Chuẩn hóa 6 gói danh mục trong data/catalog-by-series/; viết CHAY_DIPPIE.bat, HUONG_DAN_CHAY.md, scripts/make-release-zip.ps1, Docker Compose, Caddy HTTPS, script sao lưu, CI/Dependabot và kịch bản demo. Phía Java viết CatalogPackageValidator cùng CatalogPackageValidatorTest để chặn gói danh mục sai trước khi nhập.", "Thư mục data/catalog-by-series/, infrastructure/ (Dockerfile, docker-compose.yml, docker-compose.demo.yml, Caddyfile), CHAY_DIPPIE.bat, HUONG_DAN_CHAY.md, scripts/, .github/workflows/ci.yml; lớp com.dippie.service.catalog.CatalogPackageValidator."],
        ],
        col_widths_mm=[26, 74, 60],
        font_size=10,
    )

    rb.h2("4.3. Quy trình phối hợp và quản lý mã nguồn")
    rb.p(
        "Để bốn người làm song song ít bị xung đột mã, nhóm giữ năm thói quen làm việc xuyên suốt đồ án:"
    )
    rb.bullet(
        "Tách nhánh Git theo từng đầu việc lớn (như nhánh xử lý trải nghiệm ux-pass, nhánh tài liệu ux-docs, nhánh "
        "desktop và nhánh triển khai) rồi mới gộp qua Pull Request."
    )
    rb.bullet(
        "Chốt trước hợp đồng HTTP trong backend/springboot/CONTRACT.md (đường dẫn, tên trường JSON, cấu trúc "
        "ApiResponse, mã trạng thái và thông báo lỗi tiếng Việt) để backend, frontend và CineBot làm song song được "
        "ngay."
    )
    rb.bullet(
        "Cập nhật đầu việc trên bảng nhiệm vụ chung chia theo trạng thái Cần làm, Đang làm, Chờ soát và Xong, họp "
        "nhóm hằng tuần để gỡ vướng và nhắn trao đổi nhanh khi có thay đổi."
    )
    rb.bullet(
        "Mỗi Pull Request cần ít nhất một thành viên khác đọc qua và phải chạy xanh toàn bộ job trong GitHub Actions "
        "CI (Maven test, TypeScript typecheck, Frontend test và Vite build)."
    )
    rb.bullet(
        "Tổ chức 3 vòng kiểm thử người dùng giả lập độc lập theo 3 kịch bản (khách mới, phụ huynh dùng điện thoại, "
        "người dùng quay lại), ghi lại lỗi theo mức độ nặng nhẹ rồi sửa theo từng đợt."
    )

    rb.h2("4.4. Kết quả 3 vòng đánh giá trải nghiệm người dùng")
    rb.p(
        "Nhóm chạy 3 vòng kiểm thử trực tiếp trên ứng dụng đang hoạt động, kết hợp gọi API/SSE và mở trình duyệt "
        "Chromium không giao diện ở hai kích thước màn hình là máy tính (1280x800 px) và điện thoại (375x812 px). "
        "Ba kịch bản thử nghiệm mô phỏng đúng ba kiểu người dùng: khách ghé thăm lần đầu đăng ký tài khoản, phụ huynh "
        "chọn phim cho bé 5 tuổi trên điện thoại, và người dùng tài khoản mẫu user quay lại xem tiếp."
    )
    rb.figure(ASSETS_DIR / "ux_audit_chart.png", "Biểu đồ tiến bộ điểm trải nghiệm người dùng (UX) qua 3 vòng kiểm thử", width_inches=6.0)

    rb.table(
        "So sánh điểm đánh giá 6 hành trình người dùng qua 3 vòng kiểm thử (thang điểm 10)",
        ["Hành trình người dùng", "Vòng 1", "Vòng 2", "Vòng 3", "Các cải thiện đã thực hiện"],
        [
            ["Khám phá và Tìm kiếm", "4", "7", "8", "Sửa 4 chip thể loại trả 0 kết quả; thêm tìm không dấu, bí danh nhân vật (Xuka, Doremon, Peppa Pig), sửa lỗi gõ 1 đến 2 ký tự, lọc thể loại và năm, sắp xếp theo điểm thật và NULLS LAST cho năm."],
            ["Xem phim và Tiến độ", "3", "7", "7", "Gắn YouTube IFrame API để lưu tiến độ, hiện Xem tiếp, tự chuyển tập và chuyển mùa; gắn nhãn 'Chỉ mở trên YouTube' cho 44 tập Ori; nạp sẵn lịch sử mẫu (DemoHistorySeeder); chặn lưu tiến độ tập không nhúng được."],
            ["Đánh giá (1 đến 10)", "2", "6", "9", "Thêm điều khiển chấm điểm 1 đến 10 ngay trên banner bộ phim và bảng chi tiết tập; tính trung bình cộng đồng thật; làm mới giao diện ngay và đồng bộ vào sắp xếp /search."],
            ["Thư viện cá nhân", "2", "8", "9", "Thêm nút Lưu xem sau và Yêu thích (44x44 px) trên mọi thẻ phim và danh sách tập; thêm nút bỏ Yêu thích tại /profile; tự đồng bộ dữ liệu khách khi đăng ký/đăng nhập và giữ lại mục lỗi."],
            ["Gợi ý phim", "3", "6", "8", "Sửa lỗi thích bộ phim lại ẩn hết tập của bộ đó; đa dạng hóa 6 bộ cho khách mới và giới hạn 2 tập mỗi bộ; thêm điểm phạt khi đánh giá thấp; thêm thanh 6 tâm trạng và loại tập lỗi 5 giây hay tập Ori không nhúng."],
            ["Trợ lý CineBot (SSE)", "5", "7", "8", "Sửa liên kết mốc thời gian (?t=ms); sửa chip 'Cảnh sau đó thì sao' tiến đúng đoạn kế; sửa 'phim giống Larva/Peppa/Jadoo/Trạng Quỳnh'; lọc nghiêm dưới 5 phút; từ chối câu ngoài danh mục (Naruto, One Piece, thời tiết)."],
            ["Điểm trung bình chung", "3,2", "6,8", "8,2", "Sửa hết 5/5 lỗi chặn nghiêm trọng từ Vòng 1 và các lỗi hiển thị trên điện thoại (0 tràn ngang, vùng chạm 44 px, chữ 16 px)."],
        ],
        col_widths_mm=[30, 14, 14, 14, 88],
        font_size=10,
    )

    rb.h2("4.5. Chiến lược kiểm thử, rà soát bảo mật và thống kê kết quả")
    rb.p(
        "Hệ thống kiểm thử của Dippie gồm bốn lớp: kiểm thử đơn vị cho các quy tắc độc lập (CompletionPolicy, "
        "NextEpisodePolicy, ByteRange, ContentHash, IntentParser, LexicalIndex, AffinityRecommendationStrategy); "
        "kiểm thử tích hợp bằng @SpringBootTest và MockMvc trên H2 in-memory để chạy xuyên qua bảo mật, giao dịch, "
        "khóa đồng thời cùng 20 truy vấn đối chiếu trong CatalogSearchParityTest; kiểm thử hành vi giao diện ở "
        "frontend với 29 bộ kiểm thử Node test runner (286 ca) kèm kiểm tra kiểu TypeScript và build Vite; cùng "
        "vòng kiểm thử khói tổng thể trên bản dựng demo chạy thật (36/36 phép kiểm tra đạt)."
    )
    rb.table(
        "Thống kê kiểm thử tự động, rà soát bảo mật OWASP và kiểm thử khói cuối",
        ["Phân hệ / Hạng mục kiểm chứng", "Số lớp / bộ", "Kết quả", "Nội dung kiểm chứng tiêu biểu"],
        [
            ["Backend: controller.<tính năng> (11 gói con)", "19 lớp", "Thuộc tổng 589 ca", "Kiểm thử HTTP/MockMvc xuyên bảo mật và giao dịch cho account (đăng xuất thu hồi token, xóa tài khoản, đặt lại mật khẩu), admin, assistant (CineBot SSE), catalog, library, media, movie, progress (khóa lạc quan 409, xóa lịch sử), report, user và web (SPA 404, OpenAPI)."],
            ["Backend: service.<tính năng> (9 gói con)", "29 lớp (+1 fixture)", "Thuộc tổng 589 ca", "Đối chiếu 20 truy vấn BM25 (CatalogSearchParityTest), CineBot (IntentParser, ChatService, Template/DeepSeek), nhập và đệm danh mục (CatalogPackageValidatorTest, CatalogReadModelCacheTest), gợi ý Affinity/Mood, phát media (PlaybackAccessTest, MediaFileResolverTest), gửi thư SMTP/.eml, lọc SQL, tiến độ và chính sách quản trị."],
            ["Backend: model.<tính năng> (4 gói con)", "10 lớp", "Thuộc tổng 589 ca", "Kiểm thử đơn vị quy tắc miền: AccessPolicy, PublicationStatus, ContentHash SHA-256, YoutubeSource, SeriesArtwork, ByteRange 200/206/416, MovieBrowseQuery và WatchProgressCommand."],
            ["Backend: com.dippie.security", "8 lớp", "Thuộc tổng 589 ca", "JWT HS256, tokenVersion, LoginRateLimiter, PasswordPolicy, SecurityContextCurrentViewer và bộ Critical/High/MediumLowSecurityFixesTest."],
            ["Backend: config và com.dippie.common.text", "3 lớp", "Thuộc tổng 589 ca", "Cấu hình Flyway (FlywayConfigurationTest), MediaPropertiesTest và chuẩn hóa tiếng Việt không dấu (TextFoldingTest)."],
            ["Tổng cộng Backend Spring Boot", "69 lớp (70 tệp)", "589 ca Surefire", "Chạy tự động qua ./mvnw -B test trên H2 in-memory (0 lỗi, 1 ca bỏ qua trên Windows do tạo symlink cần quyền quản trị viên ở MediaFileResolverTest)."],
            ["Tổng cộng Frontend React / TypeScript", "29 bộ (*_suite.mjs)", "286 ca", "16.520 dòng mã kiểm thử (31 tệp .mjs), chạy qua npm test kèm npm run typecheck và npm run build (điều hướng, trình phát, CineBot, GuestSyncDialog, đặt lại mật khẩu, pháp lý, di động)."],
            ["Tổng cộng Desktop JavaFX (Dippie Admin)", "5 lớp JUnit 5", "33 ca (33/33 đạt)", "529 dòng mã kiểm thử (kèm TestDatabase): AdminServiceTest (15), DaoTest (11), ReadServiceTest (3), CsvExportTest (2), DatabaseFactoryTest (2)."],
            ["Rà soát bảo mật theo OWASP", "30 phát hiện", "Sửa 6/6 nghiêm trọng", "6 mức nghiêm trọng (không fail-open demo, giới hạn tần suất và luồng CineBot, kiểm tra xuất bản phát media, giới hạn thân yêu cầu) và phần lớn trong 12 mức cao (đăng xuất phía máy chủ, header bảo mật, proxy tin cậy, PasswordPolicy, MySQL TLS, tắt Swagger ngoài demo, Flyway, UTC)."],
            ["Kiểm thử khói cuối (End-to-End Smoke)", "Bản dựng demo cuối", "36/36 phép đạt", "Kiểm tra các trang chính, nạp 6 bộ (806 tập) trong khoảng 7 giây, tìm lỗi gõ, gợi ý tâm trạng, CineBot, đăng ký kèm điều khoản, đăng nhập, chấm điểm, xóa lịch sử, đăng xuất thu hồi token, quản trị, báo lỗi phát, thư .eml đặt lại mật khẩu và chặn chat quá cỡ (413)."],
        ],
        col_widths_mm=[46, 24, 28, 62],
        font_size=9.3,
    )

    # =======================================================================
    # CHƯƠNG 5. TRIỂN KHAI THỬ NGHIỆM
    # =======================================================================
    rb.h1("Chương 5. Triển khai thử nghiệm", break_before=True)

    rb.h2("5.1. Chạy thử một cú nhấp và đóng gói phát hành")
    rb.p(
        "Để giảng viên hoặc người chấm mở nhanh toàn bộ hệ thống trên máy Windows 10/11 mà không phải tự cài Java, "
        "Node.js hay MySQL, nhóm đóng gói quy trình khởi chạy một cú nhấp bằng tệp CHAY_DIPPIE.bat (chuyển tiếp sang "
        "scripts/run-all.ps1), tài liệu hướng dẫn người dùng HUONG_DAN_CHAY.md và kịch bản nén gói nộp "
        "scripts/make-release-zip.ps1."
    )
    rb.code_block(
        "Khởi chạy toàn bộ Dippie (Web và Dippie Admin) hoặc đóng gói tệp ZIP phát hành",
        """# Nhấp đúp CHAY_DIPPIE.bat ở thư mục gốc (hoặc gọi từ dòng lệnh; macOS/Linux dùng: bash run-all.sh)
CHAY_DIPPIE.bat
CHAY_DIPPIE.bat -Stop     # Dừng cả backend và Dippie Admin
CHAY_DIPPIE.bat -Reset    # Xóa target\\dippie-java để nạp lại dữ liệu mẫu sạch

# Tạo gói phát hành đã build sẵn frontend\\dist (xuất ra dist-release\\Dippie-yyyyMMdd-HHmm.zip)
.\\scripts\\make-release-zip.ps1"""
    )
    rb.p(
        "Khi chạy CHAY_DIPPIE.bat, trình khởi chạy tự kiểm tra JDK 21 trên máy; nếu chưa có sẽ tự tải Eclipse "
        "Temurin JDK 21 di động vào thư mục .tools\\jdk ngay trong dự án (không cài vào Windows, không cần quyền "
        "Administrator). Tiếp theo script bật backend Spring Boot ở hồ sơ demo trên tệp H2 target\\dippie-java\\dippie "
        "(tự chọn cổng trống tiếp theo nếu cổng 3000 đang bận), tự nạp đủ 6 gói danh mục trong data/catalog-by-series/ "
        "(6 bộ phim, 806 tập), rồi tự mở trình duyệt web cùng cửa sổ ứng dụng desktop Dippie Admin. Giao diện web đã "
        "được scripts/make-release-zip.ps1 build sẵn trong frontend\\dist (lọc bỏ thư mục sinh tự động và tệp bí mật "
        "như .env, .mv.db), nên máy chấm bài không cần cài Node.js. Tài liệu HUONG_DAN_CHAY.md trình bày ngắn gọn "
        "3 bước giải nén, tài khoản mẫu và cách xử lý 5 lỗi thường gặp (SmartScreen, phần mềm diệt virus, trùng cổng, "
        "mất mạng lần đầu, tường lửa Windows)."
    )
    rb.p(
        "Trong quá trình phát triển, thành viên nhóm có sẵn JDK 21 và Node.js 22.12 trở lên vẫn có thể chạy riêng "
        "từng tiến trình bằng .\\start-java.ps1 (tự build frontend\\dist nếu thiếu và bật hồ sơ demo, nơi "
        "DemoAccountSeeder tạo sẵn admin và user dùng mật khẩu password123, còn DemoHistorySeeder gieo 3 mốc tiến độ "
        "xem, 3 mục xem sau, 2 mục yêu thích và 3 đánh giá sau khi nạp 6 gói danh mục bằng "
        "scripts/import_preview_catalog.ps1)."
    )

    rb.h2("5.2. Khởi chạy ứng dụng quản trị desktop Dippie Admin")
    rb.p(
        "Ứng dụng desktop Dippie Admin trong module desktop/ chạy độc lập bằng lệnh sau ở thư mục gốc:"
    )
    rb.code_block(
        "Lệnh khởi chạy ứng dụng quản trị Desktop Dippie Admin (JavaFX 21 và JDBC)",
        """.\\start-desktop.ps1"""
    )
    rb.p(
        "Script start-desktop.ps1 chuyển vào thư mục desktop/, trỏ MAVEN_USER_HOME tới thư mục .m2 của người dùng và "
        "chạy .\\mvnw.cmd -q compile javafx:run để mở lớp com.dippie.desktop.DippieAdminApp. Khi chưa có tệp "
        "desktop/desktop.properties, DatabaseFactory tự nối vào tệp H2 sẵn có tại <gốc-kho-mã>/target/dippie-java/"
        "dippie với tham số AUTO_SERVER=TRUE và IFEXISTS=TRUE (tài khoản sa, mật khẩu rỗng). Nhờ vậy quản trị viên "
        "đăng nhập bằng admin / password123 và thao tác trên 4 tab (Tổng quan, Danh mục, Người dùng, Đánh giá) đồng "
        "bộ ngay với web. Nếu dùng MySQL, chỉ cần sao chép desktop/desktop.properties.example thành "
        "desktop/desktop.properties và điền url (tiền tố jdbc:mysql://), user cùng password."
    )

    rb.h2("5.3. Triển khai bản chạy thật với Docker Compose và Caddy HTTPS")
    rb.p(
        "Để chạy thử như một dịch vụ web hoàn chỉnh bên ngoài máy cá nhân, nhóm chuẩn bị sẵn cấu hình container và "
        "sao lưu trong infrastructure/, hồ sơ prod của Spring Boot và các script trong scripts/:"
    )
    rb.bullet(
        "Dịch vụ mysql (mysql:8.4) trong infrastructure/docker-compose.yml đặt trên mạng nội bộ (internal: true, "
        "không mở cổng 3306 ra ngoài), giới hạn 1 vCPU và 768 MB RAM, innodb-buffer-pool-size=256M, lưu dữ liệu trên "
        "volume mysqldata và tự khởi tạo lần đầu theo thứ tự 01_init.sql, init_migrations.sh rồi "
        "init_database_objects.sh (tạo 3 tài khoản dippie_app, dippie_readonly và dippie_admin)."
    )
    rb.bullet(
        "Dịch vụ app dựng từ infrastructure/Dockerfile 3 giai đoạn (node:22-alpine, eclipse-temurin:21-jdk-jammy rồi "
        "eclipse-temurin:21-jre-alpine chạy user non-root dippie UID 1001, giới hạn 1 vCPU và 768 MB RAM), chạy hồ sơ "
        "application-prod.yml (SPRING_PROFILES_ACTIVE=prod): bật graceful shutdown 30 giây, "
        "forward-headers-strategy=native tin proxy nội bộ, kết nối MySQL bật TLS (sslMode=REQUIRED, tắt "
        "allowPublicKeyRetrieval) theo múi giờ UTC, Flyway migration (V1 đến V5), HikariCP phù hợp VPS nhỏ (tối đa "
        "10 kết nối, 2 nhàn rỗi), ddl-auto=validate, tắt H2 console, Swagger, stacktrace và dữ liệu demo, đồng thời "
        "bắt buộc APP_JWT_SECRET dạng Base64 từ 32 byte trở lên."
    )
    rb.bullet(
        "Dịch vụ caddy (caddy:2-alpine, 0,5 vCPU và 128 MB RAM) là container duy nhất mở cổng 80 và 443 ra ngoài. "
        "infrastructure/Caddyfile tự cấp chứng chỉ HTTPS (Let's Encrypt hoặc ZeroSSL) khi có biến DOMAIN, thêm bộ "
        "header bảo mật (HSTS, CSP cho YouTube và SSE, X-Frame-Options: DENY, nosniff, Permissions-Policy), tắt nén "
        "và đặt flush_interval -1 (timeout 180 giây) cho /api/chat và đặt giới hạn thân yêu cầu 25 MB cho "
        "/api/admin/catalog/*."
    )
    rb.bullet(
        "Ngoài bản production, infrastructure/docker-compose.demo.yml cho phép dựng nhanh bản demo H2 trên "
        "127.0.0.1:3000. Các script scripts/backup-mysql.{sh,ps1} và restore-mysql.{sh,ps1} sao lưu qua mysqldump "
        "(dùng MYSQL_PWD), nén .sql.gz, kiểm tra SHA-256, giữ 14 bản gần nhất và đòi xác nhận YES trước khi ghi đè. "
        "Tệp .github/workflows/ci.yml và .github/dependabot.yml chạy kiểm tra tự động và rà soát bản cập nhật thư "
        "viện hằng tuần."
    )

    rb.table(
        "Các tệp cấu hình chạy thử, đóng gói, triển khai, sao lưu và tự động hóa",
        ["Đường dẫn tệp", "Vai trò chính"],
        [
            ["CHAY_DIPPIE.bat và scripts/run-all.ps1 (run-all.sh)", "Khởi chạy 1 cú nhấp trên Windows (và macOS/Linux): tự tải JDK 21 di động nếu thiếu, bật demo, nạp 6 bộ phim, mở trình duyệt và Dippie Admin."],
            ["HUONG_DAN_CHAY.md", "Tài liệu hướng dẫn 3 bước chạy thử cho người mới, tài khoản mẫu, lệnh -Stop/-Reset và xử lý 5 lỗi thường gặp."],
            ["scripts/make-release-zip.ps1", "Build sẵn frontend/dist, kiểm tra tệp bắt buộc, lọc bỏ thư mục tạm và tệp bí mật, đóng gói ra dist-release/Dippie-*.zip."],
            ["infrastructure/Dockerfile", "Build 3 giai đoạn (Node 22, JDK 21, JRE 21 Alpine non-root dippie kèm HEALTHCHECK /api/health)."],
            ["infrastructure/docker-compose.yml", "Triển khai production 3 dịch vụ (caddy, app hồ sơ prod, mysql:8.4) tách mạng edge và internal, xoay vòng log json-file."],
            ["infrastructure/docker-compose.demo.yml", "Chạy nhanh chế độ demo H2 kèm dữ liệu mẫu, chỉ gắn 127.0.0.1:3000."],
            ["infrastructure/Caddyfile", "Reverse proxy Caddy 2 cấp HTTPS tự động, header bảo mật (HSTS, CSP), SSE flush_interval -1, giới hạn body 2 MB và 25 MB."],
            ["infrastructure/.env.example và init_*.sh", "Mẫu biến môi trường bí mật và kịch bản nạp migration, view, thủ tục, trigger, tài khoản MySQL lần đầu."],
            ["backend/.../resources/application-prod.yml và db/migration/V1..V5", "Cấu hình hồ sơ prod (MySQL TLS, Flyway V1 đến V5, UTC, graceful shutdown, HikariCP, ddl-auto=validate, tắt H2 console, Swagger và demo seed)."],
            ["scripts/backup-mysql.{sh,ps1} và restore-mysql.{sh,ps1}", "Sao lưu MySQL qua docker compose exec (MYSQL_PWD, nén gzip, SHA-256, giữ 14 bản) và khôi phục có xác nhận YES."],
            [".github/workflows/ci.yml và dependabot.yml", "CI 3 job (Backend Maven, Frontend typecheck/test/build, Docker build) và quét cập nhật phụ thuộc hằng tuần."],
        ],
        col_widths_mm=[62, 98],
        font_size=9.5,
    )

    rb.h2("5.4. Kịch bản và kết quả triển khai thử nghiệm")
    rb.p(
        "Nhóm kiểm chứng thực tế ở ba mức: chạy vòng kiểm thử khói cuối cùng từ đầu đến cuối trên bản dựng hoàn "
        "chỉnh đang chạy (36/36 phép kiểm tra đạt), đo thời gian phản hồi các API chính trên bản demo cục bộ (6 bộ "
        "phim, 806 tập, hơn 2.517 tài liệu chỉ mục, mỗi API gọi lặp 5 lần ở Vòng 3) và thử nghiệm đọc/ghi cơ sở dữ "
        "liệu khi phình lên 100.000 tập giả lập (1.000 bộ phim) trên MySQL 8.0.46 cục bộ."
    )
    rb.p(
        "Ở vòng kiểm thử khói cuối (36/36 bước đạt), hệ thống được khởi động từ trạng thái sạch và kiểm tra lần lượt: "
        "tải các trang giao diện chính, nạp đủ 6 gói danh mục (6 bộ phim, 806 tập) trong khoảng 7 giây, tìm kiếm chịu "
        "lỗi gõ, gợi ý theo tâm trạng, hỏi đáp CineBot, đăng ký tài khoản mới có xác nhận đồng ý điều khoản, đăng "
        "nhập, chấm điểm phim, xóa lịch sử xem, đăng xuất thu hồi ngay token cũ, chặn người dùng thường vào khu vực "
        "quản trị và mở đúng cho admin, gửi và cập nhật báo lỗi phát video, gửi yêu cầu đặt lại mật khẩu ghi đúng "
        "tệp .eml ra hộp thư cục bộ, và từ chối yêu cầu trò chuyện quá dung lượng với mã HTTP 413."
    )
    rb.table(
        "Thời gian phản hồi thực tế của các API chính (đo trên localhost, 5 lượt mỗi API ở Vòng 3)",
        ["Endpoint / Tác vụ kiểm thử", "Độ trễ trung bình (ms)", "Độ trễ tối đa (ms)", "Kết quả kiểm chứng"],
        [
            ["GET /api/catalog/series/cards (6 bộ phim)", "3 ms", "4 ms", "Đạt (HTTP 200, 0 lỗi)"],
            ["POST /api/chat (CineBot SSE, trạng thái ấm)", "5 ms", "7 ms", "Đạt (luồng SSE đầy đủ)"],
            ["GET /api/catalog/episodes/ep_doraemon_s05_0209", "6 ms", "8 ms", "Đạt (kèm phân đoạn và nguồn phát)"],
            ["GET /api/movies/genres (5 thể loại hợp lệ)", "6 ms", "8 ms", "Đạt"],
            ["GET /api/movies/browse?sort=rating&direction=desc", "13 ms", "38 ms", "Đạt (xếp hạng điểm cộng đồng thật)"],
            ["POST /api/recommend (moodPreset: bedtime)", "29 ms", "36 ms", "Đạt (6 tập phù hợp, 0 tập lỗi)"],
            ["GET /api/catalog/series/ser_doraemon (337 tập)", "32 ms", "40 ms", "Đạt"],
            ["GET /api/movies/browse?query=pepa (tìm lỗi gõ)", "37 ms", "42 ms", "Đạt (trả đủ 52 mục Heo Peppa)"],
            ["POST /api/recommend (limit=6, khách mới)", "43 ms", "51 ms", "Đạt (đa dạng 6 bộ phim)"],
        ],
        col_widths_mm=[68, 28, 26, 38],
        font_size=10,
    )

    rb.table(
        "Kết quả kiểm thử khả năng mở rộng cơ sở dữ liệu ở mốc 100.000 tập (MySQL 8.0.46, trung vị 7 lần đo)",
        ["Kịch bản truy vấn ở 100.000 tập", "Trước chỉnh sửa", "Sau chỉnh sửa (JPA Spec và Chỉ mục)", "Ghi nhận kỹ thuật"],
        [
            ["Duyệt kho hoạt hình, trang 12 kết quả", "2.864 ms", "1.642 ms", "Chỉ nạp 12 bản ghi thay vì tải cả bảng."],
            ["Duyệt kho theo thể loại, trang 12 kết quả", "2.710 ms", "1.782 ms", "Dùng truy vấn con EXISTS trên bảng nối movie_genres."],
            ["Tìm kiếm theo một tiêu đề tập", "2.768 ms", "1.940 ms", "Chỉ nạp đúng 1 bản ghi khớp."],
            ["Lấy chi tiết tập và danh sách tệp theo mã", "6,2 ms", "6,4 ms", "Ổn định ở cả mốc 10.000 và 100.000 tập."],
            ["Nhập gói mới 1.000 tập và 1.000 liên kết", "Không đo", "1,06 giây (1 giao dịch)", "Nhập lại gói cũ trả lũy đẳng trong 0,19 giây."],
        ],
        col_widths_mm=[54, 24, 34, 48],
        font_size=10,
    )

    rb.p(
        "Bên cạnh đo API và cơ sở dữ liệu, nhóm kiểm tra hiển thị trực tiếp trên trình duyệt ở hai khung hình là "
        "máy tính 1440x900 px và điện thoại 390x844 px. Hai ảnh sau ghi lại kết quả tìm từ khóa \"khủng long\" tại "
        "trang Kho phim (/search) trả đúng 9 tập Doraemon kèm bộ lọc thể loại, khoảng năm, và trang chủ trên điện "
        "thoại xếp dọc gọn, không bị tràn ngang."
    )
    if (SCREENSHOTS_DIR / "search-khung-long.png").exists():
        rb.figure(SCREENSHOTS_DIR / "search-khung-long.png", "Kiểm chứng thực tế trang Kho phim (/search): tìm 'khủng long' trả về 9 tập Doraemon cùng bộ lọc thể loại và khoảng năm", width_inches=5.9)
    if (SCREENSHOTS_DIR / "home-mobile.png").exists():
        rb.figure(SCREENSHOTS_DIR / "home-mobile.png", "Kiểm chứng giao diện di động trang chủ Dippie (390x844 px): bố cục dọc gọn, không tràn ngang", width_inches=2.45)

    rb.h2("5.5. Hạn chế hiện tại và hướng phát triển")
    rb.p(
        "Sau đợt rà soát bảo mật và kiểm thử trước khi đóng gói, hệ thống vẫn còn một số hạng mục mở và hạn chế dữ "
        "liệu cần tiếp tục xử lý:",
        keep_with_next=True
    )
    rb.bullet(
        "Phụ thuộc nguồn phát YouTube: 707/806 tập trong kho hiện phụ thuộc vào khung nhúng hoặc liên kết YouTube "
        "(trong đó 44/180 tập Công chúa Ori từ mùa 3 đến mùa 5 chỉ mở được trên YouTube gốc, và ngày 25/11/2026 nhóm "
        "đã phải gỡ Pokémon cùng Mr Bean do bị chặn tại Việt Nam). 99 tập Xin Chào Jadoo còn lại dùng tệp cục bộ "
        "hoặc Google Drive không đóng gói kèm kho mã vì dung lượng và bản quyền."
    )
    rb.bullet(
        "Chưa có hồ sơ trẻ em và mã PIN phụ huynh: hệ thống chưa tách hồ sơ riêng theo độ tuổi của từng bé trong gia "
        "đình, chưa có mã PIN khóa vùng cài đặt hoặc đặt giới hạn thời gian xem hằng ngày."
    )
    rb.bullet(
        "Chưa có phụ đề (subtitles): bảng catalog_text_tracks đã thiết kế sẵn trong cơ sở dữ liệu nhưng các tập phim "
        "hiện tại chưa có tệp phụ đề tiếng Việt hoặc song ngữ để hiển thị trên trình phát."
    )
    rb.bullet(
        "Dữ liệu JSON nguồn còn 519 tập Doraemon và Công chúa Ori thiếu trường release_year_start (nên không hiện khi "
        "lọc theo khoảng năm trên /search), 49/51 tựa tập Heo Peppa vẫn là tiếng Anh và còn 33 lỗi chính tả nhỏ ở "
        "tiêu đề gốc."
    )
    rb.bullet(
        "Khi người xem chuyển tập thật nhanh trên trình phát YouTube, đôi lúc vẫn có hai lệnh PUT lưu tiến độ gửi "
        "cách nhau khoảng 6 ms gây mã 409 Conflict (trình duyệt tự nạp lại trạng thái mới nhất nhưng vẫn nên chống "
        "gửi trùng sớm hơn ở phía client)."
    )

    rb.p("Ở các đợt hoàn thiện tiếp theo, nhóm dự kiến tập trung vào ba hướng:", keep_with_next=True)
    rb.bullet(
        "Thêm hồ sơ riêng cho từng bé kèm mã PIN phụ huynh, giới hạn thời gian xem mỗi ngày và thống kê lịch sử xem "
        "theo tuần."
    )
    rb.bullet(
        "Bổ sung phụ đề tiếng Việt và song ngữ trên bảng catalog_text_tracks, đồng thời điền đủ năm phát hành cho "
        "Doraemon, Công chúa Ori và Việt hóa 49 tiêu đề tập Heo Peppa."
    )
    rb.bullet(
        "Giảm phụ thuộc vào nguồn nhúng YouTube bằng phát video thích ứng HLS khi có nguồn nội dung được cấp phép "
        "bản quyền, kết hợp thử nghiệm tìm kiếm lai giữa BM25 từ vựng và tìm kiếm ngữ nghĩa bằng vector."
    )

    # =======================================================================
    # KẾT LUẬN
    # =======================================================================
    rb.h1("Kết luận", break_before=True)
    rb.p(
        "Qua bài tập lớn này, nhóm chúng em đã xây dựng được hệ thống Dippie chạy hoàn chỉnh gồm ứng dụng web cho gia "
        "đình (Spring Boot 3.5, Java 21, React 19) và ứng dụng quản trị desktop (JavaFX 21, JDBC thuần) dùng chung "
        "một cơ sở dữ liệu 32 bảng. Các chức năng chính từ duyệt 6 bộ hoạt hình (806 tập, 1.705 phân đoạn), tìm kiếm "
        "không dấu chịu lỗi gõ, xem phim lưu tiến độ với khóa lạc quan, xem và xóa lịch sử xem, thư viện cá nhân và "
        "chấm điểm 1 đến 10, gợi ý theo sở thích và tâm trạng, trợ lý CineBot phát luồng SSE nhảy đúng mốc cảnh, đến "
        "các luồng tài khoản và vận hành (đồng ý điều khoản, đăng xuất thu hồi token, đặt lại mật khẩu qua email, xóa "
        "tài khoản, báo lỗi phát video và quản trị 5 mục) đều đã chạy ổn định trên dữ liệu thật."
    )
    rb.p(
        "Về kiến thức môn Lập trình hướng đối tượng Java, nhóm đã áp dụng trực tiếp vào mã nguồn các phần đã học trên "
        "lớp: tính đóng gói, kế thừa, giao diện, lớp trừu tượng, đa hình, phân cấp ngoại lệ, generics, collections, lớp "
        "lồng nhau, luồng I/O, JDBC có giao dịch, giao diện JavaFX, kiến trúc MVC và các mẫu thiết kế GoF gồm "
        "Composite, Adapter, Facade, Proxy, Observer, Strategy, Command, State, Template Method, Singleton và Factory. "
        "Nhờ làm việc có phân nhánh Git, chốt trước hợp đồng API, chạy CI tự động với 589 ca kiểm thử backend (1 ca "
        "bỏ qua trên Windows do symlink), 286 ca frontend và 33 ca desktop, rà soát bảo mật theo OWASP (xử lý đủ 6/6 "
        "phát hiện nghiêm trọng và phần lớn mức cao trong 30 mục), kiểm thử khói cuối đạt 36/36 phép kiểm tra cùng "
        "3 vòng đánh giá UX nâng điểm từ 3,2/10 lên 8,2/10, hệ thống đã sẵn sàng để chạy thử bằng một cú nhấp chuột "
        "(CHAY_DIPPIE.bat) hoặc triển khai bằng Docker Compose cùng Caddy HTTPS."
    )

    # =======================================================================
    # TÀI LIỆU THAM KHẢO
    # =======================================================================
    rb.h1("Tài liệu tham khảo", break_before=True)
    refs = [
        "[1] Y. Daniel Liang, Introduction to Java Programming and Data Structures, Comprehensive Version (Giáo trình học phần Lập trình hướng đối tượng Java), Pearson.",
        "[2] E. Gamma, R. Helm, R. Johnson, J. Vlissides, Design Patterns: Elements of Reusable Object-Oriented Software, Addison-Wesley, 1995.",
        "[3] Oracle Corporation, Java SE 21 Documentation & Language Specifications, https://docs.oracle.com/en/java/javase/21/.",
        "[4] VMware / Broadcom, Spring Boot 3.5 Reference Documentation, Spring Security & Spring Data JPA, https://docs.spring.io/spring-boot/.",
        "[5] OpenJFX Community, JavaFX 21 Documentation, https://openjfx.io/.",
        "[6] Oracle Corporation, JDBC API & MySQL 8.0 / 8.4 Reference Manual (InnoDB Transactions, Stored Objects, mysqldump), https://dev.mysql.com/doc/refman/8.0/en/.",
        "[7] Meta Open Source, React 19 Documentation, https://react.dev/; TypeScript Handbook, https://www.typescriptlang.org/docs/; Vite & Tailwind CSS Documentation.",
        "[8] Google Developers, YouTube IFrame Player API Reference, https://developers.google.com/youtube/iframe_api_reference.",
        "[9] DeepSeek API Documentation (OpenAI-compatible Chat Completions SSE), https://api-docs.deepseek.com/.",
        "[10] Docker Inc., Docker Compose Documentation, https://docs.docker.com/compose/; Caddy Web Server Documentation (Automatic HTTPS), https://caddyserver.com/docs/.",
        "[11] TVmaze API Licensing (CC BY-SA, nguồn tham chiếu số mùa và tập bộ Heo Peppa), https://www.tvmaze.com/api#licensing.",
    ]
    for r in refs:
        rb.p(r, space_after=4)

    # =======================================================================
    # PHỤ LỤC
    # =======================================================================
    rb.h1("Phụ lục", break_before=True)

    rb.h2("Phụ lục A. Tài khoản mẫu, dữ liệu gieo sẵn và thống kê 6 bộ phim")
    rb.table(
        "Danh sách tài khoản mẫu trong hồ sơ demo (mật khẩu chung: password123)",
        ["Tên đăng nhập", "Email", "Vai trò", "Dữ liệu mẫu khởi tạo sẵn (DemoAccountSeeder và DemoHistorySeeder)"],
        [
            ["admin", "admin@dippie.com", "ROLE_ADMIN", "Tài khoản quản trị toàn quyền trên giao diện Web (/admin/*) và ứng dụng desktop Dippie Admin."],
            ["user", "user@dippie.com", "ROLE_USER", "3 mốc tiến độ xem (Heo Peppa T1 01:32, Doraemon T209 06:00, Larva T3 hoàn thành), 3 mục Xem sau (Jadoo T2, Ori T4, Trạng Quỳnh T1), 2 mục Yêu thích (Doraemon T209, Heo Peppa T3) và 3 đánh giá (8, 9, 7 điểm)."],
        ],
        col_widths_mm=[24, 38, 28, 70],
        font_size=10.5,
    )

    rb.table(
        "Thống kê chi tiết 6 gói danh mục phim hoạt hình trong data/catalog-by-series/",
        ["Bộ phim (Mã series)", "Mùa", "Tập", "Phân đoạn", "Nhân vật", "Nguồn phát chính"],
        [
            ["Doraemon (ser_doraemon)", "7", "337", "889", "6", "YouTube nhúng (các playlist POPS Kids mùa 5 đến 7 và 9 đến 12)"],
            ["Công chúa Ori (ser_ori)", "5", "180", "363", "12", "136 tập YouTube nhúng và 44 tập mở liên kết YouTube gốc"],
            ["Ấu trùng tinh nghịch Larva (ser_larva)", "1", "104", "2", "4", "YouTube nhúng (playlist Larva TUBA)"],
            ["Xin Chào Jadoo (ser_jadoo)", "4", "99", "296", "9", "Tệp cục bộ hoặc Google Drive (198 tài nguyên) và YouTube dự phòng"],
            ["Heo Peppa (ser_peppa_pig)", "2", "51", "100", "10", "YouTube nhúng (tham chiếu mùa và tập theo TVmaze)"],
            ["Trạng Quỳnh Thời Nhí Nhố (ser_trang_quynh)", "1", "35", "55", "10", "YouTube nhúng (playlist phát hành chính thức)"],
            ["Tổng cộng (6 bộ phim)", "20", "806", "1.705", "51", "762 tập nhúng trực tiếp, 44 tập mở ngoài, hơn 2.517 tài liệu chỉ mục"],
        ],
        col_widths_mm=[54, 14, 14, 20, 18, 40],
        font_size=10,
    )

    rb.h2("Phụ lục B. Danh sách các API REST và SSE chính của hệ thống Dippie")
    rb.table(
        "Tổng hợp các nhóm API chính phục vụ bởi backend Spring Boot 3.5 (25 Controller)",
        ["Nhóm chức năng", "Phương thức và Đường dẫn", "Quyền truy cập", "Mô tả ngắn"],
        [
            ["Sức khỏe và Trạng thái", "GET /api/health · GET /api/assistant/status", "Công khai", "Kiểm tra kết nối CSDL và trạng thái chỉ mục BM25, nhà cung cấp LLM."],
            ["Xác thực, Mật khẩu và Tài khoản", "POST /api/auth/{login, register, logout} · POST /api/auth/password-reset/{request, confirm} · GET /api/auth/me · PUT/DELETE /api/users/me · POST /api/users/me/password", "Công khai / Đăng nhập", "Đăng ký kèm đồng ý điều khoản, đăng nhập cấp JWT, đăng xuất thu hồi token, đặt lại mật khẩu qua email, sửa hồ sơ, đổi mật khẩu và tự xóa tài khoản."],
            ["Duyệt phim và Danh mục", "GET /api/movies, /browse, /genres, /{id}, /{id}/media-descriptor · GET /api/catalog/series, /series/cards, /series/{id}, /episodes/{id}, /search", "Công khai", "Duyệt phim bộ, lọc thể loại và năm, tìm kiếm chịu lỗi gõ, lấy chi tiết mùa, tập, phân đoạn và nguồn phát."],
            ["Thư viện và Đánh giá", "GET/POST/DELETE /api/users/{watchlist, favorites, ratings} [/{movieId}]", "Đăng nhập (401)", "Quản lý Xem sau, Yêu thích lũy đẳng và chấm điểm nguyên từ 1 đến 10."],
            ["Tiến độ, Lịch sử và Tập tiếp theo", "GET/PUT/DELETE /api/users/watch-progress [/{episodeId}] · GET /api/users/next-episode", "Đăng nhập (401)", "Lưu vị trí xem theo mili giây có khóa lạc quan, xóa sạch lịch sử xem (DELETE gốc) và tính tập tiếp theo."],
            ["Phát tệp Media và Báo lỗi", "POST /api/media/grant/{assetId} · GET/HEAD /api/media/stream/{assetId} · POST /api/reports/playback", "Theo PlaybackAccess / Công khai", "Cấp vé JWT 5 phút, phát video HTTP Byte Range (200, 206, 416) và gửi báo lỗi khi phát video gặp sự cố."],
            ["Gợi ý và Trợ lý CineBot", "POST /api/recommend · POST /api/chat (SSE) · POST /v1/{search, discover}", "Công khai / Tùy chọn JWT", "Gợi ý Affinity/Mood, hỏi đáp CineBot qua SSE có ChatStreamGuard và tìm kiếm ngữ nghĩa cục bộ."],
            ["Quản trị danh mục", "POST /api/admin/catalog/{validate, import, snapshots/{id}/retry} · GET /api/admin/catalog/{history, snapshots, export}", "ROLE_ADMIN (403)", "Kiểm tra hợp lệ, nhập/xuất gói dippie.catalog.v1 và quản lý snapshot chỉ mục."],
            ["Quản trị người dùng, Báo lỗi và Kiểm toán", "GET /api/admin/users · PATCH /api/admin/users/{id}/{role, status} · GET/PATCH /api/admin/reports [/{id}] · GET /api/admin/{stats, audit-events}", "ROLE_ADMIN (403)", "Quản trị tài khoản bảo vệ admin cuối cùng, xử lý báo lỗi phát video, xem thống kê và nhật ký kiểm toán."],
        ],
        col_widths_mm=[28, 60, 28, 44],
        font_size=9.3,
    )

    out_path = rb.save(OUT_DOCX)
    print(f"Đã tạo báo cáo: {out_path} ({out_path.stat().st_size:,} bytes)")
    print(f"Tổng số Hình: {len(rb.figures)}, Tổng số Bảng: {len(rb.tables)}")


if __name__ == "__main__":
    build_report()
