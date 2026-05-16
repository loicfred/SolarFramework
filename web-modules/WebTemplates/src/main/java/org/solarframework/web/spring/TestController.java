package org.solarframework.web.spring;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/test")
public class TestController {


    @GetMapping("/testa")
    public String test(Model model) {
        model.addAttribute("test", "HELLOOOO");
        return "web/mypage";
    }
}
