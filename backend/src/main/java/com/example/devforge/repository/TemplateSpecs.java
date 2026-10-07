package com.example.devforge.repository;

import com.example.devforge.dto.TemplateFilter;
import com.example.devforge.entity.AppTemplate;
import org.springframework.data.jpa.domain.Specification;

public final class TemplateSpecs {
  public static Specification<AppTemplate> from(TemplateFilter filter) {
    return Specification.where(equal("framework", filter.framework()))
        .and(equal("buildTool", filter.buildTool()));
  }

  private static <T> Specification<AppTemplate> equal(String field, T value) {
    return (root, _, cb) -> value == null ? null : cb.equal(root.get(field), value);
  }
}
