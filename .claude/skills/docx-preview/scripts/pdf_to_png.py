"""PDF를 쪽마다 PNG로 저장한다(PyMuPDF).

사용법: py pdf_to_png.py in.pdf 출력폴더 [dpi=110]
결과: 출력폴더/{pdf이름}-p{쪽}.png
"""
import sys
from pathlib import Path

import pymupdf


def main() -> None:
    pdf = Path(sys.argv[1])
    out = Path(sys.argv[2])
    dpi = int(sys.argv[3]) if len(sys.argv) > 3 else 110
    out.mkdir(parents=True, exist_ok=True)
    with pymupdf.open(pdf) as doc:
        for i, page in enumerate(doc, start=1):
            target = out / f"{pdf.stem}-p{i}.png"
            page.get_pixmap(dpi=dpi).save(target)
            print(f"PNG: {target}")


if __name__ == "__main__":
    main()
