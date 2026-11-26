package com.springbloom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Per-IP limits on the public chat endpoints and the cart checkout, under florabelle.rate-limit. */
@Component
@ConfigurationProperties(prefix = "florabelle.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int maxKeys = 10_000;
    private Rule messages = new Rule(10, 10);
    private Rule messagesPerDay = new Rule(200, 200.0 / 1440);
    private Rule vision = new Rule(20, 20);
    private Rule quotations = new Rule(20, 20);
    private Rule history = new Rule(60, 60);
    /** Each checkout writes a customer, a quotation, an order and a Stripe session. */
    private Rule checkout = new Rule(5, 2);

    public static class Rule {

        private int capacity;
        private double refillPerMinute;

        public Rule() {
        }

        public Rule(int capacity, double refillPerMinute) {
            this.capacity = capacity;
            this.refillPerMinute = refillPerMinute;
        }

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public double getRefillPerMinute() { return refillPerMinute; }
        public void setRefillPerMinute(double refillPerMinute) { this.refillPerMinute = refillPerMinute; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxKeys() { return maxKeys; }
    public void setMaxKeys(int maxKeys) { this.maxKeys = maxKeys; }
    public Rule getMessages() { return messages; }
    public void setMessages(Rule messages) { this.messages = messages; }
    public Rule getMessagesPerDay() { return messagesPerDay; }
    public void setMessagesPerDay(Rule messagesPerDay) { this.messagesPerDay = messagesPerDay; }
    public Rule getVision() { return vision; }
    public void setVision(Rule vision) { this.vision = vision; }
    public Rule getQuotations() { return quotations; }
    public void setQuotations(Rule quotations) { this.quotations = quotations; }
    public Rule getHistory() { return history; }
    public void setHistory(Rule history) { this.history = history; }
    public Rule getCheckout() { return checkout; }
    public void setCheckout(Rule checkout) { this.checkout = checkout; }
}
