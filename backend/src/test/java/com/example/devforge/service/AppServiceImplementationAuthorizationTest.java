package com.example.devforge.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.devforge.entity.AppService;
import com.example.devforge.entity.User;
import com.example.devforge.exception.AppServiceNotFoundException;
import com.example.devforge.exception.UnauthorizedAccessException;
import com.example.devforge.repository.AppServiceRepository;
import com.example.devforge.repository.ProvisioningJobRepository;
import com.example.devforge.service.implementation.AppServiceImplementation;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppServiceImplementationAuthorizationTest {

  private final AppServiceRepository repository = mock(AppServiceRepository.class);
  private final AppServiceImplementation service =
      new AppServiceImplementation(
          repository, null, null, null, null, null, mock(ProvisioningJobRepository.class));

  @Test
  void userCannotReadAnotherUsersService() {
    UUID serviceId = UUID.randomUUID();
    UUID userA = UUID.randomUUID();
    when(repository.findByIdAndOwnerKeycloakId(serviceId, userA)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getService(serviceId, userA, false))
        .isInstanceOf(AppServiceNotFoundException.class);
    verify(repository).findByIdAndOwnerKeycloakId(serviceId, userA);
    verify(repository, never()).findById(serviceId);
  }

  @Test
  void userCannotDeleteAnotherUsersService() {
    UUID serviceId = UUID.randomUUID();
    UUID userA = UUID.randomUUID();
    UUID userB = UUID.randomUUID();
    AppService ownedByB =
        AppService.builder().id(serviceId).owner(User.builder().keycloakId(userB).build()).build();
    when(repository.findById(serviceId)).thenReturn(Optional.of(ownedByB));

    assertThatThrownBy(() -> service.deleteService(userA, serviceId))
        .isInstanceOf(UnauthorizedAccessException.class);
    verify(repository, never()).delete(any(AppService.class));
  }
}
