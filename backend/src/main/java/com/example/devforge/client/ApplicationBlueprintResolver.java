package com.example.devforge.client;

import com.example.devforge.client.ServiceBlueprintResolver.Blueprint;
import com.example.devforge.dto.ApplicationCreationDto;
import com.example.devforge.exception.InvalidTemplateSelectionException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationBlueprintResolver {

  public static final int FIRST_PORT = 8080;

  private final ServiceBlueprintResolver services;

  public record ServiceEntry(String name, int port, Blueprint blueprint) {}

  public record ApplicationBlueprint(String name, String description, List<ServiceEntry> services) {

    public ProvisionerClient.RenderRequest renderRequest() {
      return new ProvisionerClient.RenderRequest(
          name, null, null, null, null, null, description, null, null, specs(_ -> null));
    }

    public List<ProvisionerClient.ApplicationServiceSpec> specs(IntFunction<UUID> serviceIds) {
      List<ProvisionerClient.ApplicationServiceSpec> specs = new ArrayList<>();
      for (int i = 0; i < services.size(); i++) {
        ServiceEntry entry = services.get(i);
        specs.add(entry.blueprint().applicationSpec(serviceIds.apply(i), entry.port()));
      }
      return specs;
    }
  }

  public ApplicationBlueprint resolve(ApplicationCreationDto dto) {
    if (dto.services() == null || dto.services().isEmpty()) {
      throw new InvalidTemplateSelectionException("An application needs at least one service");
    }
    Set<String> names = new HashSet<>();
    List<ServiceEntry> entries = new ArrayList<>();
    for (ApplicationCreationDto.Service service : dto.services()) {
      Blueprint blueprint = services.resolve(service.asServiceDto(dto.workspaceId()));
      String name = blueprint.context().serviceName();
      if (!names.add(name)) {
        throw new InvalidTemplateSelectionException("Service name " + name + " is used twice");
      }
      entries.add(new ServiceEntry(name, FIRST_PORT + entries.size(), blueprint));
    }
    return new ApplicationBlueprint(dto.name().trim(), dto.description(), List.copyOf(entries));
  }
}
