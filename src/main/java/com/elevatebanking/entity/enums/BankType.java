package com.elevatebanking.entity.enums;

public enum BankType {
    FERVES("001"),
    NIMRONTH("002"),
    EVIL("003"),
    GREKET("004");

    private final String branchCode;

    BankType(String branchCode) {
        this.branchCode = branchCode;
    }

    public String getBranchCode() {
        return branchCode;
    }

}
