package com.springbloom.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpTest {

    private static MockHttpServletRequest request(String header) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        if (header != null) {
            request.addHeader("CF-Connecting-IP", header);
        }
        return request;
    }

    @Test
    @DisplayName("uses the Cloudflare header when it is an IP literal")
    void usesHeader() {
        assertThat(ClientIp.of(request("203.0.113.7"))).isEqualTo("203.0.113.7");
        assertThat(ClientIp.of(request(" 2001:DB8::1 "))).isEqualTo("2001:db8::1");
    }

    @Test
    @DisplayName("falls back to the socket when the header is missing")
    void fallsBackWithoutHeader() {
        assertThat(ClientIp.of(request(null))).isEqualTo("10.0.0.5");
    }

    @Test
    @DisplayName("ignores junk so it never becomes a bucket key")
    void ignoresJunk() {
        assertThat(ClientIp.of(request("<script>alert(1)</script>"))).isEqualTo("10.0.0.5");
        assertThat(ClientIp.of(request("999.1.1.1"))).isEqualTo("10.0.0.5");
        assertThat(ClientIp.of(request("1.2.3.4, 5.6.7.8"))).isEqualTo("10.0.0.5");
        assertThat(ClientIp.of(request(""))).isEqualTo("10.0.0.5");
    }
}
