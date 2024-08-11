package com.atkach.ecoflow.dto.login;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class User {
    private String userId;
    private String email;
    private String name;
    private String icon;
    private int state;
    private String regtype;
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    private String destroyed;
    private String registerLang;
    private String source;
    private boolean administrator;
    private int appid;
    private String countryCode;
}
