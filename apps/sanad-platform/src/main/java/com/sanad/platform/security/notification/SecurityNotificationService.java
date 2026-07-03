package com.sanad.platform.security.notification;

import com.sanad.platform.user.domain.User;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class SecurityNotificationService {
    private final SecurityNotificationGateway gateway;
    private final SecurityNotificationProperties properties;

    public SecurityNotificationService(
            SecurityNotificationGateway gateway,
            SecurityNotificationProperties properties
    ) {
        this.gateway = gateway;
        this.properties = properties;
    }

    public void deliverResetLink(User user, String rawToken, String locale, boolean administrative) {
        String language = normalizeLocale(locale);
        String separator = properties.getApplicationBaseUrl().contains("?") ? "&" : "?";
        String resetUrl = properties.getApplicationBaseUrl()
                + "/reset-password"
                + separator
                + "token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        boolean arabic = "ar".equals(language);
        String subject = arabic
                ? (administrative ? "إعداد كلمة مرور جديدة لحساب سند" : "إعادة تعيين كلمة مرور سند")
                : (administrative ? "Set a new password for your SNAD account" : "Reset your SNAD password");
        String text = arabic
                ? "استخدم الرابط أحادي الاستخدام التالي خلال 30 دقيقة: " + resetUrl
                : "Use this single-use link within 30 minutes: " + resetUrl;
        String html = arabic
                ? "<p dir=\"rtl\">استخدم رابط إعادة التعيين أحادي الاستخدام خلال 30 دقيقة:</p><p><a href=\"" + resetUrl + "\">إعداد كلمة مرور جديدة</a></p>"
                : "<p>Use this single-use reset link within 30 minutes:</p><p><a href=\"" + resetUrl + "\">Set a new password</a></p>";

        gateway.deliver(new SecurityMessage(user.getEmail(), subject, text, html));
    }

    public void deliverPasswordChanged(User user, String locale) {
        boolean arabic = "ar".equals(normalizeLocale(locale));
        String subject = arabic ? "تم تغيير كلمة مرور حساب سند" : "Your SNAD password was changed";
        String text = arabic
                ? "تم تغيير كلمة مرور حسابك وإنهاء الجلسات النشطة. تواصل مع الدعم فورًا إذا لم تنفذ هذا الإجراء."
                : "Your password was changed and active sessions were revoked. Contact support immediately if this was not you.";
        String html = arabic
                ? "<p dir=\"rtl\">تم تغيير كلمة مرور حسابك وإنهاء الجلسات النشطة.</p><p dir=\"rtl\">تواصل مع الدعم فورًا إذا لم تنفذ هذا الإجراء.</p>"
                : "<p>Your password was changed and active sessions were revoked.</p><p>Contact support immediately if this was not you.</p>";
        gateway.deliver(new SecurityMessage(user.getEmail(), subject, text, html));
    }

    private String normalizeLocale(String locale) {
        return locale != null && locale.toLowerCase().startsWith("en") ? "en" : "ar";
    }
}
