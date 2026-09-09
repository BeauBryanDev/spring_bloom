package com.springbloom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The shop's public contact details, in one place.
 *
 * A bean rather than a model attribute or a Thymeleaf fragment: the footer is on
 * every page including the error page, which is rendered by Spring's own
 * BasicErrorController and never sees a model this application built. Templates
 * reach it as ${@contactDetails.email}, so nothing has to be plumbed through a
 * controller and no page can carry a stale copy of a phone number.
 */
@Component("contactDetails")
@ConfigurationProperties(prefix = "springbloom.contact")
public class ContactDetails {

    private String email = "hola@springbloom.com";
    private String wholesaleEmail = "mayoristas@springbloom.com";
    private String phone = "+57 300 000 0000";
    private String hours = "Lunes a sabado, 8:00 a.m. - 6:00 p.m.";

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getWholesaleEmail() {
        return wholesaleEmail;
    }

    public void setWholesaleEmail(String wholesaleEmail) {
        this.wholesaleEmail = wholesaleEmail;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getHours() {
        return hours;
    }

    public void setHours(String hours) {
        this.hours = hours;
    }
}
