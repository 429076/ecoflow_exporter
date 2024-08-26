package com.atkach.ecoflow.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Device {
    private String name;
    private String sn;
    private LocalDateTime lastMessage;

    public Device(String name, String sn) {
        this.name = name;
        this.sn = sn;
        this.lastMessage = LocalDateTime.now();
    }
}
