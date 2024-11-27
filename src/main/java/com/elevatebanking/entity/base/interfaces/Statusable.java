package com.elevatebanking.entity.base.interfaces;


import com.elevatebanking.entity.Status;

public interface Statusable {
    Status getStatus();

    void setStatus(Status status);
}
