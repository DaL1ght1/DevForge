package com.example.devforge.dto;

import lombok.Builder;

import java.io.Serializable;

@Builder
public record TemplateSynchronizationResult(int scanned,
                                            int synchronizedTemplates,
                                            int unchanged,
                                            int failed)
implements Serializable {
}
