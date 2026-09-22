package com.sanad.platform.tenancy.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.Locale;

/**
 * External ownership verifier for tenant-controlled custom domains.
 *
 * <p>Echoing a browser-visible verification token is not an ownership proof.
 * The expected challenge must be observable in public DNS before a domain can
 * become VERIFIED. HTTP verification intentionally fails closed here because
 * fetching an arbitrary tenant hostname would otherwise introduce an SSRF
 * primitive; callers should use DNS_TXT or DNS_CNAME.</p>
 */
@Service
public class DomainOwnershipVerifier {

    private static final Logger log = LoggerFactory.getLogger(DomainOwnershipVerifier.class);
    public static final String CNAME_TARGET = "snad-verify.vercel-dns.com";

    public enum Method {
        DNS_TXT,
        DNS_CNAME,
        HTTP
    }

    public boolean verify(String hostname, Method method, String expectedToken) {
        if (hostname == null || hostname.isBlank() || method == null
                || expectedToken == null || expectedToken.isBlank()) {
            return false;
        }
        return switch (method) {
            case DNS_TXT -> txtContains("_snad-verify." + hostname, expectedToken);
            case DNS_CNAME -> cnameMatches(hostname, CNAME_TARGET);
            case HTTP -> false;
        };
    }

    private boolean txtContains(String recordName, String expectedToken) {
        try {
            Attribute txt = attributes(recordName, "TXT").get("TXT");
            if (txt == null) return false;
            NamingEnumeration<?> values = txt.getAll();
            while (values.hasMore()) {
                String value = String.valueOf(values.next()).replace("\"", "").trim();
                if (value.equals(expectedToken)) return true;
            }
            return false;
        } catch (Exception error) {
            log.info("Domain TXT verification not satisfied for {}", recordName);
            return false;
        }
    }

    private boolean cnameMatches(String hostname, String expectedTarget) {
        try {
            Attribute cname = attributes(hostname, "CNAME").get("CNAME");
            if (cname == null) return false;
            NamingEnumeration<?> values = cname.getAll();
            String expected = canonicalDnsName(expectedTarget);
            while (values.hasMore()) {
                if (canonicalDnsName(String.valueOf(values.next())).equals(expected)) return true;
            }
            return false;
        } catch (Exception error) {
            log.info("Domain CNAME verification not satisfied for {}", hostname);
            return false;
        }
    }

    private Attributes attributes(String name, String type) throws Exception {
        Hashtable<String, String> environment = new Hashtable<>();
        environment.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        environment.put("com.sun.jndi.dns.timeout.initial", "2000");
        environment.put("com.sun.jndi.dns.timeout.retries", "1");
        DirContext context = new InitialDirContext(environment);
        try {
            return context.getAttributes(name, new String[]{type});
        } finally {
            context.close();
        }
    }

    private static String canonicalDnsName(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
