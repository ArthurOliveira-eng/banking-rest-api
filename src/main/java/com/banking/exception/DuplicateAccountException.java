package com.banking.exception;

public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException(String field, String value) {
        super("Account already exists with " + field + ": " + value);
    }
}
