package com.sanad.platform.security.notification;

public interface SecurityNotificationGateway {
    void deliver(SecurityMessage message);
}
