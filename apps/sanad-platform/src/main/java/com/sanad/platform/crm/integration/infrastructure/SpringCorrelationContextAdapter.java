package com.sanad.platform.crm.integration.infrastructure;

import com.sanad.platform.crm.integration.domain.CorrelationContextPort;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.UUID;

@Component
public class SpringCorrelationContextAdapter implements CorrelationContextPort {
    @Override
    public String currentCorrelationId() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String header = request.getHeader("X-Correlation-ID");
                if (header != null && !header.isBlank()) return header;
            }
        } catch (Exception ignored) { }
        return UUID.randomUUID().toString();
    }
}
