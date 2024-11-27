package com.elevatebanking.entity.base.interfaces;

import jakarta.transaction.Status;

public interface Statusable {
    Status getStatus();

    void setStatus(Status status);
}
