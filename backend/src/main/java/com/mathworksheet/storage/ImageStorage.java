package com.mathworksheet.storage;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * 이미지 저장소. 나중에 클라우드로 옮길 때 이 구현만 바꾼다(3장 "확장 대비").
 * DB에는 여기서 돌려준 상대 경로만 저장한다(6장).
 */
public interface ImageStorage {

    /** 등록한 사진 사본 저장. 반환값은 저장소 기준 상대 경로(예: originals/2026/10/123.jpg) */
    String saveOriginal(long sourceImageId, String extension, InputStream content);

    /** 잘라낸 그림 저장. 반환값은 상대 경로(예: figures/12/1234_1.png) */
    String saveFigure(long problemId, int order, InputStream content);

    Path resolve(String relativePath);
}
