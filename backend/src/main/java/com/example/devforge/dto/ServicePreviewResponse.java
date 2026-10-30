package com.example.devforge.dto;

import java.util.List;

public record ServicePreviewResponse(
    String previewId,
    String templateName,
    String templateVersion,
    int fileCount,
    long totalBytes,
    List<PreviewFileEntry> files) {

  public record PreviewFileEntry(
      String path, String origin, int size, boolean binary, boolean omitted) {}
}
