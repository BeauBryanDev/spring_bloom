package com.springbloom.adapter.in.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.springbloom.domain.port.in.AgentUsageQueryUseCase;

import lombok.RequiredArgsConstructor;

/** What the chatbot costs in tokens. Behind the /admin/** rule. */
@Controller
@RequiredArgsConstructor
public class AgentUsageController {

    private final AgentUsageQueryUseCase agentUsage;

    @GetMapping("/admin/agent")
    public String agent(Model model) {
        model.addAttribute("summary", agentUsage.summarize());
        return "admin/agent";
    }
}
