package com.springbloom.adapter.in.web;

import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller's address. Cloudflare sets CF-Connecting-IP on every proxied
 * request; it is only trustworthy while the origin accepts traffic from
 * Cloudflare alone. Anything that is not an IP literal is ignored, so a junk
 * header never becomes a bucket key.
 */
public final class ClientIp {

    static final String HEADER = "CF-Connecting-IP";

    private static final Pattern IPV4 = Pattern.compile(
            "(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {

        String header = request.getHeader(HEADER);
        if (header != null) {
            String candidate = header.trim();
            if (isLiteral(candidate)) {
                return candidate.toLowerCase();
            }
        }
        return request.getRemoteAddr();
    }

    static boolean isLiteral(String value) {
        return IPV4.matcher(value).matches()
                || (value.contains(":") && IPV6.matcher(value).matches());
    }
}
