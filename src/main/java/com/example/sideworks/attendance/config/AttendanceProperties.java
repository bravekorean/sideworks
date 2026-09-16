package com.example.sideworks.attendance.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.attendance")
@Getter
@Setter
public class AttendanceProperties {

    private boolean checkInDeadlineEnabled;
}
