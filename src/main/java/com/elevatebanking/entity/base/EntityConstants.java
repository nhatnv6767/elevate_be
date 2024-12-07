package com.elevatebanking.entity.base;

public class EntityConstants {
    private EntityConstants() {
    }

    // Database Types
    public static final String UUID_TYPE = "VARCHAR(36)";
    public static final String JSON_TYPE = "jsonb";

    // Field Lengths
    public static final int NAME_MIN_LENGTH = 2;
    public static final int NAME_MAX_LENGTH = 100;
    public static final int USERNAME_MIN_LENGTH = 3;
    public static final int USERNAME_MAX_LENGTH = 50;
    public static final int DESCRIPTION_MAX_LENGTH = 500;
    public static final int PHONE_MAX_LENGTH = 15;

    // Validation Patterns
    public static final String USERNAME_PATTERN = "^[a-zA-Z0-9._-]{3,50}$";
    public static final String PHONE_PATTERN = "^\\+?[0-9]{10,15}$";
    public static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@(.+)$";
    public static final String ACCOUNT_NUMBER_PATTERN = "^[0-9]{10,20}$";
    public static final String IDENTITY_NUMBER_PATTERN = "^[0-9]{9,12}$";

    // Validation Messages
    public static final String REQUIRED_FIELD = "This field is required";
    public static final String INVALID_EMAIL = "Invalid email format";
    public static final String INVALID_PHONE = "Invalid phone number format";
    public static final String INVALID_USERNAME = "Username can only contain letters, numbers, dots, underscores and hyphens";

    public static final String PAST_DATE_MESSAGE = "Date must be in the past";
    public static final String INVALID_POINTS = "Invalid points calculation";
    public static final String INVALID_IDENTITY = "Invalid identity number format";

    //    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*])[A-Za-z\\d!@#$%^&*]{8,}$";
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$";

    public static final String INVALID_PASSWORD = "Password must contain at minimum eight characters, at least one letter, one number and one special character";

    public static final String INVALID_TIER_STATUS = "Invalid tier status";
    public static final String INVALID_ACCOUNT_STATUS = "Invalid account status";
    public static final String INVALID_POINTS_CALCULATION = "Total points must equal earned minus spent points";
}
