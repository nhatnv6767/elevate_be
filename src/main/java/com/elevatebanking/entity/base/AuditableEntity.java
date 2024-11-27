package com.elevatebanking.entity.base;

import com.elevatebanking.entity.base.interfaces.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class AuditableEntity extends BaseEntity implements Auditable {
    @Column(name = "created_by")
    private String createdBy;
    
    @Column(name = "updated_by")
    private String updatedBy;
    
}
