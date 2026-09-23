package com.springbloom.adapter.in.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;

/**
 * Renders the admin login form. Spring Security itself handles the POST to
 * /admin/login; this controller only serves the page formLogin redirects to.
 */
@Controller
public class AdminAuthController {

    @GetMapping("/admin/login")
    public String loginForm(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            Model model) {

        model.addAttribute("error", error != null);
        model.addAttribute("loggedOut", logout != null);
        return "admin/login";
    }
}
