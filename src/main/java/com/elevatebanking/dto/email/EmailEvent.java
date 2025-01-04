package com.elevatebanking.dto.email;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class EmailEvent {
    private String to;
    private String subject;
    private String content;
    private Map<String, Object> templateData;
}
