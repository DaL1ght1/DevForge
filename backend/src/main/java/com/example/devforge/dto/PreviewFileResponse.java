package com.example.devforge.dto;

import java.io.Serializable;

public record PreviewFileResponse(
    String path, String origin, int size, boolean binary, boolean omitted, String content)
    implements Serializable {}
