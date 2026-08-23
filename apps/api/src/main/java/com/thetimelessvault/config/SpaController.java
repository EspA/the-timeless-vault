package com.thetimelessvault.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({
            "/",
            "/login",
            "/inventory",
            "/inventory/**",
            "/watches",
            "/watches/**",
            "/market",
            "/market/**",
            "/scans",
            "/scan-logs",
            "/alerts",
            "/buying-opportunities",
            "/listing-logs",
            "/sales",
            "/orders",
            "/orders/**",
            "/sales-ledger",
            "/settings"
    })
    public String spa() {
        return "forward:/index.html";
    }
}
