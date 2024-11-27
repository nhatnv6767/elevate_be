package com.elevatebanking.entity.base.interfaces;

import com.elevatebanking.entity.enums.UserStatus;

public interface Statusable {
    UserStatus getStatus();

    void setStatus(UserStatus status);
}
