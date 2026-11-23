package com.springbloom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Knobs for the agent guardrails, under florabelle.guardrails. */
@Component
@ConfigurationProperties(prefix = "florabelle.guardrails")
public class GuardrailProperties {

    private boolean enabled = true;
    private int maxToolCalls = 8;
    private int maxOutputTokens = 700;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }
}
