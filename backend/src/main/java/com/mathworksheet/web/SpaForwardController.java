package com.mathworksheet.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * React 라우터 경로(/bank, /review 등)를 새로고침해도 index.html을 돌려준다.
 * /api와 파일 경로(점이 있는 경로)는 대상이 아니다.
 */
@Controller
public class SpaForwardController {

    @GetMapping({"/{path:^(?!api$)[^.]*}", "/{path:^(?!api$)[^.]*}/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
