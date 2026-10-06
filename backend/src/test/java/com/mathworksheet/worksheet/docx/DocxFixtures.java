package com.mathworksheet.worksheet.docx;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

/** 테스트용 docx를 코드로 만든다. 바이너리 픽스처 파일을 저장소에 두지 않기 위해서다. */
public final class DocxFixtures {

    private DocxFixtures() {
    }

    public static void addStyle(XWPFDocument doc, String name, STStyleType.Enum type) {
        XWPFStyles styles = doc.createStyles();
        CTStyle ct = CTStyle.Factory.newInstance();
        ct.setStyleId(name);
        ct.addNewName().setVal(name);
        ct.setType(type);
        styles.addStyle(new XWPFStyle(ct));
    }

    public static void addRequiredStyles(XWPFDocument doc) {
        for (TemplateInspector.RequiredStyle s : TemplateInspector.REQUIRED_STYLES) {
            addStyle(doc, s.name(), s.kind().type);
        }
    }

    /** 각 문자열이 run 하나가 된다. List.of("{{시험", "제목}}")처럼 쪼개진 빈칸을 흉내 낼 수 있다 */
    public static void header(XWPFDocument doc, List<String> runs) {
        paragraph(doc.createHeader(HeaderFooterType.DEFAULT).createParagraph(), runs);
    }

    public static void footer(XWPFDocument doc, List<String> runs) {
        paragraph(doc.createFooter(HeaderFooterType.DEFAULT).createParagraph(), runs);
    }

    public static void paragraph(XWPFParagraph p, List<String> runs) {
        for (String text : runs) {
            p.createRun().setText(text);
        }
    }

    public static Path save(XWPFDocument doc, Path file) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            doc.write(out);
        }
        doc.close();
        return file;
    }
}
