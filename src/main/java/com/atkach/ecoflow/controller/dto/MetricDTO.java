package com.atkach.ecoflow.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricDTO {
    private String name;
    private List<TagDTO> tags;
    private double value;
}
