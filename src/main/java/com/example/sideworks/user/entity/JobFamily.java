package com.example.sideworks.user.entity;

public enum JobFamily {
    TECHNICAL("TC"),
    CORPORATE("CP");

    private final String employeeNumberPrefix;

    JobFamily(String employeeNumberPrefix) {
        this.employeeNumberPrefix = employeeNumberPrefix;
    }

    public String getEmployeeNumberPrefix() {
        return employeeNumberPrefix;
    }
}
