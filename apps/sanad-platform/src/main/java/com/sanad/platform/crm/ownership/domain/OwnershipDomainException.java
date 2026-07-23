package com.sanad.platform.crm.ownership.domain;

/** Base exception for all ownership domain errors. */
public class OwnershipDomainException extends RuntimeException {
    public OwnershipDomainException(String message) { super(message); }
    public OwnershipDomainException(String message, Throwable cause) { super(message, cause); }
}
