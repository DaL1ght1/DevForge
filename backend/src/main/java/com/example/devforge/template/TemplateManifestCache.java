package com.example.devforge.template;

import com.example.devforge.entity.TemplateVersion;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TemplateManifestCache {

  private static final int MAX_ENTRIES = 512;

  private final TemplateManifestParser parser;
  private final Map<String, TemplateManifest> byHash = new ConcurrentHashMap<>();

  public TemplateManifest get(TemplateVersion version) {
    String hash = version.getContentHash();
    if (hash == null) return parser.parse(version.getManifest());
    TemplateManifest cached = byHash.get(hash);
    if (cached != null) return cached;
    TemplateManifest parsed = parser.parse(version.getManifest());
    if (byHash.size() >= MAX_ENTRIES) byHash.clear();
    byHash.put(hash, parsed);
    return parsed;
  }
}
