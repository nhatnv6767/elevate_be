package com.elevatebanking.entity.base;

public class EntityConstants {
    // Common
    public static final String UUID_TYPE = "VARCHAR(36)";

    // Validation Messages
    public static final String REQUIRED_FIELD = "This field is required";
    public static final String INVALID_EMAIL = "Invalid email format";

    // Length Constants
    public static final int NAME_MAX_LENGTH = 100;
    public static final int DESCRIPTION_MAX_LENGTH = 500;
    public static final int PHONE_MAX_LENGTH = 15;

    // Regex Patterns
    public static final String PHONE_REGEX = "^\\+?[0-9]{10,15}$";
    public static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@(.+)$";
}
