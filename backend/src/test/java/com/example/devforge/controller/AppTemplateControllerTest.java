package com.example.devforge.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.devforge.dto.AppTemplateResponse;
import com.example.devforge.entity.AppTemplate;
import com.example.devforge.entity.BuildTool;
import com.example.devforge.entity.TemplateFramework;
import com.example.devforge.entity.TemplateLanguage;
import com.example.devforge.mapper.AppTemplateMapper;
import com.example.devforge.service.AppTemplateService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AppTemplateControllerTest {

  @Mock private AppTemplateService appTemplateService;

  @Mock private AppTemplateMapper appTemplateMapper;

  @InjectMocks private AppTemplateController controller;

  private MockMvc mockMvc;
  private AppTemplateResponse response;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    response =
        new AppTemplateResponse(
            UUID.randomUUID(),
            "Spring Boot",
            TemplateLanguage.JAVA,
            TemplateFramework.SPRING_BOOT,
            BuildTool.MAVEN,
            "POSTGRESQL",
            null);
  }

  @Test
  void listsTemplatesWithPagination() throws Exception {
    when(appTemplateService.listTemplates(0, 10)).thenReturn(new PageImpl<>(List.of()));

    controller.getAllTemplates(0, 10);

    verify(appTemplateService).listTemplates(0, 10);
  }

  @Test
  void filtersTemplatesByLanguage() {
    AppTemplate template =
        AppTemplate.builder()
            .name("Spring Boot")
            .stableKey("spring_Boot_Maven")
            .language(TemplateLanguage.JAVA)
            .framework(TemplateFramework.SPRING_BOOT)
            .buildTool(BuildTool.MAVEN)
            .build();
    when(appTemplateService.getTemplatesByLanguages(any(), any()))
        .thenReturn(new PageImpl<>(List.of(template)));
    when(appTemplateMapper.toResponse(template)).thenReturn(response);

    var page = controller.getTemplatesByLanguages(TemplateLanguage.JAVA, PageRequest.of(0, 5));

    verify(appTemplateService)
        .getTemplatesByLanguages(org.mockito.ArgumentMatchers.eq(TemplateLanguage.JAVA), any());
    org.assertj.core.api.Assertions.assertThat(page.getContent()).containsExactly(response);
  }

  @Test
  void rejectsUnknownLanguage() throws Exception {
    mockMvc
        .perform(get("/templates/by-languages/not-a-language"))
        .andExpect(status().isBadRequest());
  }
}
