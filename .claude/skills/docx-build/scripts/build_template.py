#!/usr/bin/env python3
"""내신형 2단 형식 파일(worksheet/templates/naesin-2col.docx)을 만든다.

설계서 8장 "형식 파일 규격"을 그대로 코드로 옮긴 것이다.
- 시작점: `pandoc -o x.docx --print-default-data-file reference.docx`
- A4, 여백 1.8cm, 2단(간격 1cm, 구분선), 첫 쪽 머리말 따로(titlePg)
- 필수 스타일 13개(이름 = ID, 영문 고정)
- 빈칸 {{학원명}} {{시험제목}} {{날짜}} {{문제지번호}}는 각각 run 하나에 통째로 넣는다.

표준 라이브러리(zipfile, re)만 쓴다. 실행:
    py .claude/skills/docx-build/scripts/build_template.py [출력경로]
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
DEFAULT_OUT = ROOT / "worksheet" / "templates" / "naesin-2col.docx"

# ---------------------------------------------------------------------------
# 치수(twip = 1/1440 inch, 1cm = 567twip). Lua 필터의 상수와 맞춰야 한다.
# ---------------------------------------------------------------------------
PAGE_W, PAGE_H = 11906, 16838          # A4
MARGIN = 1020                          # 1.8cm
COL_SPACE = 567                        # 단 간격 1cm
BODY_W = PAGE_W - 2 * MARGIN           # 9866 = 17.4cm
COL_W = (BODY_W - COL_SPACE) // 2      # 4649 = 8.2cm
NUM_IND = 425                          # 번호 칸(내어쓰기) 0.75cm
CHOICE_W = COL_W - NUM_IND             # 보기가 놓이는 너비

FONT_LATIN = "Times New Roman"
FONT_HANGUL = "바탕"
FONT_HEAD = "맑은 고딕"
RED = "E00000"

W_NS = 'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" ' \
       'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"'


def tabs(*stops: tuple[str, int]) -> str:
    return "<w:tabs>" + "".join(f'<w:tab w:val="{v}" w:pos="{p}"/>' for v, p in stops) + "</w:tabs>"


def equal_tabs(n: int) -> str:
    """보기 n개가 한 줄에 같은 간격으로 놓이도록 탭 위치를 정한다.

    첫 보기는 왼쪽 들여쓰기(NUM_IND)에서 시작하고, k번째 보기는 NUM_IND + k*w.
    마지막 탭(k=n)은 단 오른쪽 끝으로, 보기 칸 n개의 경계를 모두 표시한다.
    """
    w = CHOICE_W / n
    return tabs(*[("left", round(NUM_IND + k * w)) for k in range(1, n + 1)])


# ---------------------------------------------------------------------------
# 스타일
# ---------------------------------------------------------------------------
def pstyle(sid: str, ppr: str = "", rpr: str = "", based: str = "Normal", nxt: str | None = None) -> str:
    nx = f'<w:next w:val="{nxt}"/>' if nxt else ""
    return (f'<w:style w:type="paragraph" w:customStyle="1" w:styleId="{sid}">'
            f'<w:name w:val="{sid}"/><w:basedOn w:val="{based}"/>{nx}<w:qFormat/>'
            f'<w:pPr>{ppr}</w:pPr><w:rPr>{rpr}</w:rPr></w:style>')


def cstyle(sid: str, rpr: str) -> str:
    return (f'<w:style w:type="character" w:customStyle="1" w:styleId="{sid}">'
            f'<w:name w:val="{sid}"/><w:basedOn w:val="DefaultParagraphFont"/><w:qFormat/>'
            f'<w:rPr>{rpr}</w:rPr></w:style>')


BORDERS_ALL = ('<w:top w:val="single" w:sz="4" w:space="0" w:color="000000"/>'
               '<w:left w:val="single" w:sz="4" w:space="0" w:color="000000"/>'
               '<w:bottom w:val="single" w:sz="4" w:space="0" w:color="000000"/>'
               '<w:right w:val="single" w:sz="4" w:space="0" w:color="000000"/>'
               '<w:insideH w:val="single" w:sz="4" w:space="0" w:color="000000"/>'
               '<w:insideV w:val="single" w:sz="4" w:space="0" w:color="000000"/>')


def tstyle(sid: str, extra: str = "", default: bool = False) -> str:
    d = ' w:default="1"' if default else ""
    return (f'<w:style w:type="table"{d} w:styleId="{sid}">'
            f'<w:name w:val="{sid}"/><w:basedOn w:val="TableNormal"/><w:qFormat/>'
            '<w:pPr><w:spacing w:before="0" w:after="0" w:line="240" w:lineRule="auto"/>'
            '<w:jc w:val="center"/></w:pPr>'
            '<w:tblPr><w:jc w:val="center"/><w:tblInd w:w="0" w:type="dxa"/>'
            f'<w:tblBorders>{BORDERS_ALL}</w:tblBorders>'
            '<w:tblCellMar><w:top w:w="28" w:type="dxa"/><w:left w:w="85" w:type="dxa"/>'
            '<w:bottom w:w="28" w:type="dxa"/><w:right w:w="85" w:type="dxa"/></w:tblCellMar>'
            '</w:tblPr><w:trPr><w:jc w:val="center"/></w:trPr>'
            f'<w:tcPr><w:vAlign w:val="center"/></w:tcPr>{extra}</w:style>')


KEEP = "<w:keepNext/><w:keepLines/>"
RED_RPR = f'<w:color w:val="{RED}"/>'

REQUIRED_STYLES = [
    # 단락: 문제 본문. 내어쓰기(번호 칸), 쪼개지지 않게, 보기·그림과 함께
    pstyle("Problem", KEEP + '<w:spacing w:before="120" w:after="60"/>'
           f'<w:ind w:left="{NUM_IND}" w:hanging="{NUM_IND}"/>'),
    cstyle("ProblemNumber", "<w:b/><w:bCs/>"),
    pstyle("GroupStem", KEEP + '<w:spacing w:before="160" w:after="60"/>'),
    pstyle("Choices5", KEEP + equal_tabs(5) + '<w:spacing w:before="60" w:after="60"/>'
           f'<w:ind w:left="{NUM_IND}"/><w:contextualSpacing/>'),
    pstyle("Choices3", KEEP + equal_tabs(3) + '<w:spacing w:before="60" w:after="60"/>'
           f'<w:ind w:left="{NUM_IND}"/><w:contextualSpacing/>'),
    pstyle("Choices1", KEEP + '<w:spacing w:before="60" w:after="60"/>'
           f'<w:ind w:left="{NUM_IND}"/><w:contextualSpacing/>'),
    pstyle("BogiTitle", KEEP + '<w:spacing w:before="80" w:after="20"/>'
           f'<w:ind w:left="{NUM_IND}"/><w:jc w:val="center"/>', "<w:spacing w:val=\"20\"/>"),
    # 테두리가 같은 연속 단락은 Word가 한 상자로 합친다.
    pstyle("BogiBox", KEEP + '<w:pBdr>'
           '<w:top w:val="single" w:sz="4" w:space="3" w:color="000000"/>'
           '<w:left w:val="single" w:sz="4" w:space="4" w:color="000000"/>'
           '<w:bottom w:val="single" w:sz="4" w:space="3" w:color="000000"/>'
           '<w:right w:val="single" w:sz="4" w:space="4" w:color="000000"/></w:pBdr>'
           '<w:spacing w:before="0" w:after="0" w:line="300" w:lineRule="auto"/>'
           f'<w:ind w:left="{NUM_IND + 113}" w:right="113"/>'),
    pstyle("Figure", KEEP + '<w:spacing w:before="60" w:after="60"/><w:jc w:val="center"/>'),
    pstyle("Answer", KEEP + '<w:spacing w:before="40" w:after="40"/>'
           f'<w:ind w:left="{NUM_IND}"/>', RED_RPR + "<w:b/><w:bCs/>"),
    cstyle("CorrectChoice", RED_RPR + "<w:b/><w:bCs/>"),
    tstyle("AnswerTable", '<w:tblStylePr w:type="firstRow"><w:rPr><w:b/></w:rPr>'
           '<w:tcPr><w:shd w:val="clear" w:color="auto" w:fill="E7E6E6"/></w:tcPr></w:tblStylePr>'),
]
# "Table"은 Pandoc이 모든 표에 붙이는 기본 표 스타일이라 기존 정의를 바꿔 끼운다.
TABLE_STYLE = tstyle("Table", default=True)


def patch_styles(xml: str) -> str:
    def drop(sid: str, s: str) -> str:
        return re.sub(r'<w:style\b[^>]*w:styleId="%s"[^>]*>.*?</w:style>\s*' % re.escape(sid), "", s,
                      count=1, flags=re.S)

    for sid in ["Table", "Figure"]:
        xml = drop(sid, xml)

    # 기본 글꼴·크기·언어·줄간격
    xml = re.sub(r"<w:docDefaults>.*?</w:docDefaults>", (
        "<w:docDefaults><w:rPrDefault><w:rPr>"
        f'<w:rFonts w:ascii="{FONT_LATIN}" w:hAnsi="{FONT_LATIN}" w:eastAsia="{FONT_HANGUL}" w:cs="{FONT_LATIN}"/>'
        '<w:sz w:val="20"/><w:szCs w:val="20"/>'
        '<w:lang w:val="en-US" w:eastAsia="ko-KR" w:bidi="ar-SA"/>'
        "</w:rPr></w:rPrDefault><w:pPrDefault><w:pPr>"
        '<w:autoSpaceDE w:val="0"/><w:autoSpaceDN w:val="0"/>'
        '<w:spacing w:after="0" w:line="264" w:lineRule="auto"/>'
        "</w:pPr></w:pPrDefault></w:docDefaults>"), xml, count=1, flags=re.S)

    # Pandoc 기본 본문 스타일(표 칸 안 Compact 등) 간격을 문제지에 맞게 줄인다.
    xml = re.sub(r'(<w:style\b[^>]*w:styleId="BodyText"[^>]*>.*?<w:spacing )w:before="180" w:after="180"',
                 r'\1w:before="0" w:after="60"', xml, count=1, flags=re.S)
    xml = re.sub(r'(<w:style\b[^>]*w:styleId="Compact"[^>]*>.*?<w:spacing )w:before="36" w:after="36"',
                 r'\1w:before="0" w:after="0"', xml, count=1, flags=re.S)

    return xml.replace("</w:styles>", TABLE_STYLE + "".join(REQUIRED_STYLES) + "</w:styles>")


# ---------------------------------------------------------------------------
# 머리말·꼬리말. 빈칸은 run 하나(<w:t>)에 통째로 넣는다.
# ---------------------------------------------------------------------------
def run(text: str, rpr: str = "") -> str:
    return f'<w:r><w:rPr>{rpr}</w:rPr><w:t xml:space="preserve">{text}</w:t></w:r>'


TAB_RUN = "<w:r><w:tab/></w:r>"
HEAD_FONT = f'<w:rFonts w:ascii="{FONT_HEAD}" w:hAnsi="{FONT_HEAD}" w:eastAsia="{FONT_HEAD}"/>'


def part(root: str, body: str) -> str:
    return f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<w:{root} {W_NS}>{body}</w:{root}>'


def header_first() -> str:
    small = HEAD_FONT + '<w:sz w:val="18"/>'
    p1 = ("<w:p><w:pPr>" + tabs(("right", BODY_W)) + "</w:pPr>"
          + run("{{학원명}}", small) + TAB_RUN + run("{{날짜}}", small) + "</w:p>")
    p2 = ('<w:p><w:pPr><w:spacing w:before="60" w:after="60"/><w:jc w:val="center"/></w:pPr>'
          + run("{{시험제목}}", HEAD_FONT + '<w:b/><w:sz w:val="32"/>') + "</w:p>")
    # 이름 칸: 밑줄 친 탭(오른쪽 끝까지). 밑줄 친 공백은 줄 끝에서 그려지지 않아 탭을 쓴다.
    p3 = ('<w:p><w:pPr><w:pBdr><w:bottom w:val="thickThinSmallGap" w:sz="18" w:space="4" w:color="000000"/></w:pBdr>'
          + tabs(("left", BODY_W - 3000), ("right", BODY_W)) + '<w:spacing w:after="120"/></w:pPr>'
          + TAB_RUN + run("이름", HEAD_FONT + '<w:sz w:val="20"/>')
          + '<w:r><w:rPr><w:sz w:val="20"/><w:u w:val="single"/></w:rPr><w:tab/></w:r>' + "</w:p>")
    return part("hdr", p1 + p2 + p3)


def header_rest() -> str:
    small = HEAD_FONT + '<w:sz w:val="17"/>'
    p = ('<w:p><w:pPr><w:pBdr><w:bottom w:val="single" w:sz="6" w:space="3" w:color="000000"/></w:pBdr>'
         + tabs(("right", BODY_W)) + '<w:spacing w:after="120"/></w:pPr>'
         + run("{{시험제목}}", small) + TAB_RUN + run("{{학원명}}", small) + "</w:p>")
    return part("hdr", p)


def footer() -> str:
    small = HEAD_FONT + '<w:sz w:val="17"/>'
    p = ("<w:p><w:pPr>" + tabs(("center", BODY_W // 2), ("right", BODY_W)) + "</w:pPr>"
         + TAB_RUN + run("- ", small)
         + f'<w:fldSimple w:instr=" PAGE "><w:r><w:rPr>{small}</w:rPr><w:t>1</w:t></w:r></w:fldSimple>'
         + run(" -", small) + TAB_RUN + run("{{문제지번호}}", small) + "</w:p>")
    return part("ftr", p)


# ---------------------------------------------------------------------------
# 본문: 스타일 견본만 둔다(문제지에 쓰이지 않음). 마지막에 구역 설정.
# ---------------------------------------------------------------------------
def para(style: str, inner: str) -> str:
    return f'<w:p><w:pPr><w:pStyle w:val="{style}"/></w:pPr>{inner}</w:p>'


def sample_table(style: str, cells: list[list[str]]) -> str:
    ncol = len(cells[0])
    cw = round(BODY_W * 0.44 / ncol)
    grid = "".join(f'<w:gridCol w:w="{cw}"/>' for _ in range(ncol))
    rows = "".join("<w:tr>" + "".join(
        f'<w:tc><w:tcPr><w:tcW w:w="{cw}" w:type="dxa"/></w:tcPr>{para("Compact", run(c))}</w:tc>' for c in r)
        + "</w:tr>" for r in cells)
    return (f'<w:tbl><w:tblPr><w:tblStyle w:val="{style}"/><w:tblW w:w="{cw * ncol}" w:type="dxa"/>'
            f'<w:tblLook w:val="0020" w:firstRow="1"/></w:tblPr><w:tblGrid>{grid}</w:tblGrid>{rows}</w:tbl>')


def document(rid: dict[str, str]) -> str:
    num = '<w:r><w:rPr><w:rStyle w:val="ProblemNumber"/></w:rPr><w:t>1.</w:t></w:r>' + TAB_RUN
    body = "".join([
        para("Problem", num + run("Problem: 문제 본문. 번호 뒤 Word 탭, 내어쓰기.")),
        para("Choices5", run("① 1") + TAB_RUN + run("② 2") + TAB_RUN + run("③ 3") + TAB_RUN
             + run("④ 4") + TAB_RUN + run("⑤ 5")),
        para("Choices3", run("① Choices3") + TAB_RUN + run("② 둘째") + TAB_RUN + run("③ 셋째")),
        para("Choices1", run("① Choices1: 보기 한 줄에 하나")),
        para("GroupStem", run("[3~4] GroupStem: 묶음 문제 공통 지문")),
        para("BogiTitle", run("<보 기>".replace("<", "&lt;").replace(">", "&gt;"))),
        para("BogiBox", run("ㄱ. BogiBox 첫째 줄")),
        para("BogiBox", run("ㄴ. 연속 단락은 한 상자")),
        para("Figure", run("Figure: 그림 단락(가운데)")),
        sample_table("Table", [["Table", "표"], ["1", "2"]]),
        para("Answer", run("Answer: 정답 ③")),
        para("Choices5", run("① 1") + TAB_RUN
             + '<w:r><w:rPr><w:rStyle w:val="CorrectChoice"/></w:rPr><w:t xml:space="preserve">② CorrectChoice</w:t></w:r>'),
        sample_table("AnswerTable", [["번호", "정답"], ["1", "③"]]),
    ])
    sect = (f'<w:sectPr>'
            f'<w:headerReference w:type="default" r:id="{rid["hdr2"]}"/>'
            f'<w:headerReference w:type="first" r:id="{rid["hdr1"]}"/>'
            f'<w:footerReference w:type="default" r:id="{rid["ftr2"]}"/>'
            f'<w:footerReference w:type="first" r:id="{rid["ftr1"]}"/>'
            '<w:footnotePr><w:numRestart w:val="eachSect"/></w:footnotePr>'
            f'<w:pgSz w:w="{PAGE_W}" w:h="{PAGE_H}"/>'
            f'<w:pgMar w:top="{MARGIN}" w:right="{MARGIN}" w:bottom="{MARGIN}" w:left="{MARGIN}" '
            'w:header="567" w:footer="510" w:gutter="0"/>'
            f'<w:cols w:num="2" w:space="{COL_SPACE}" w:sep="1"/>'
            '<w:titlePg/></w:sectPr>')
    ns = ('xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" '
          'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" '
          'xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math"')
    return (f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
            f'<w:document {ns}><w:body>{body}{sect}</w:body></w:document>')


def patch_settings(xml: str) -> str:
    xml = xml.replace('<w:defaultTabStop w:val="720" />', '<w:defaultTabStop w:val="400" />')
    xml = xml.replace('<w:themeFontLang w:val="en-US" />', '<w:themeFontLang w:val="en-US" w:eastAsia="ko-KR" />')
    # 최신 Word 배치 규칙(호환 모드 해제)
    compat = ('<w:compat><w:compatSetting w:name="compatibilityMode" '
              'w:uri="http://schemas.microsoft.com/office/word" w:val="15"/></w:compat>')
    if "<w:compat>" not in xml:
        xml = xml.replace("<w:rsids>", compat + "<w:rsids>", 1)
    return xml


HDR_T = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/header"
FTR_T = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer"
HDR_CT = "application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"
FTR_CT = "application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"


def build(out: Path) -> None:
    with tempfile.TemporaryDirectory() as td:
        base = Path(td) / "reference.docx"
        subprocess.run(["pandoc", "-o", str(base), "--print-default-data-file", "reference.docx"], check=True)
        src = zipfile.ZipFile(base)
        files = {n: src.read(n) for n in src.namelist()}
        src.close()

    rid = {"hdr1": "rIdWsHdrFirst", "hdr2": "rIdWsHdrDefault", "ftr1": "rIdWsFtrFirst", "ftr2": "rIdWsFtrDefault"}
    parts = {"word/header1.xml": header_first(), "word/header2.xml": header_rest(),
             "word/footer1.xml": footer(), "word/footer2.xml": footer()}

    files["word/styles.xml"] = patch_styles(files["word/styles.xml"].decode("utf-8")).encode("utf-8")
    files["word/settings.xml"] = patch_settings(files["word/settings.xml"].decode("utf-8")).encode("utf-8")
    files["word/document.xml"] = document(rid).encode("utf-8")
    for n, x in parts.items():
        files[n] = x.encode("utf-8")

    rels = files["word/_rels/document.xml.rels"].decode("utf-8")
    rels = rels.replace("</Relationships>", "".join([
        f'<Relationship Type="{HDR_T}" Id="{rid["hdr1"]}" Target="header1.xml"/>',
        f'<Relationship Type="{HDR_T}" Id="{rid["hdr2"]}" Target="header2.xml"/>',
        f'<Relationship Type="{FTR_T}" Id="{rid["ftr1"]}" Target="footer1.xml"/>',
        f'<Relationship Type="{FTR_T}" Id="{rid["ftr2"]}" Target="footer2.xml"/>',
    ]) + "</Relationships>")
    files["word/_rels/document.xml.rels"] = rels.encode("utf-8")

    ct = files["[Content_Types].xml"].decode("utf-8")
    ct = ct.replace("</Types>", "".join(
        f'<Override PartName="/{n}" ContentType="{HDR_CT if "header" in n else FTR_CT}"/>' for n in parts)
        + "</Types>")
    files["[Content_Types].xml"] = ct.encode("utf-8")

    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_suffix(".tmp")
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
        # [Content_Types].xml을 맨 앞에 둔다.
        for n in ["[Content_Types].xml"] + [n for n in files if n != "[Content_Types].xml"]:
            z.writestr(n, files[n])
    os.replace(tmp, out)
    print(f"형식 파일 생성: {out}")


if __name__ == "__main__":
    build(Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_OUT)
