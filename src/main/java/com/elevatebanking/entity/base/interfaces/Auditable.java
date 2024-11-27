package com.elevatebanking.entity.base.interfaces;

public interface Auditable {
    String getCreatedBy();
    void setCreatedBy(String createdBy);
    String getUpdatedBy();
    void setUpdatedBy(String updatedBy);
    
}
