package com.example.devforge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.devforge.dto.WorkspaceCreationDto;
import com.example.devforge.dto.WorkspaceMemberAddDto;
import com.example.devforge.entity.User;
import com.example.devforge.entity.Workspace;
import com.example.devforge.entity.WorkspaceMember;
import com.example.devforge.entity.WorkspaceRole;
import com.example.devforge.entity.WorkspaceType;
import com.example.devforge.exception.ConflictException;
import com.example.devforge.exception.UnauthorizedAccessException;
import com.example.devforge.exception.WorkspaceNotFoundException;
import com.example.devforge.mapper.WorkspaceMapper;
import com.example.devforge.repository.UserRepository;
import com.example.devforge.repository.WorkspaceMemberRepository;
import com.example.devforge.repository.WorkspaceRepository;
import com.example.devforge.service.implementation.WorkspaceServiceImplementation;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkspaceServiceImplementationTest {

  private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
  private final WorkspaceMemberRepository memberRepository = mock(WorkspaceMemberRepository.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private final WorkspaceServiceImplementation service =
      new WorkspaceServiceImplementation(
          workspaceRepository, memberRepository, userRepository, mock(WorkspaceMapper.class));

  private final UUID workspaceId = UUID.randomUUID();
  private final UUID callerKeycloakId = UUID.randomUUID();
  private final Workspace org =
      Workspace.builder().id(workspaceId).type(WorkspaceType.ORGANIZATION).build();

  @Test
  void personalWorkspaceIsCreatedOnceWithUserAsOwner() {
    User user = User.builder().id(UUID.randomUUID()).username("alice").build();
    when(workspaceRepository.findByTypeAndPersonalOwnerId(WorkspaceType.PERSONAL, user.getId()))
        .thenReturn(Optional.empty());
    when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Workspace created = service.getOrCreatePersonal(user);

    assertThat(created.getType()).isEqualTo(WorkspaceType.PERSONAL);
    assertThat(created.getSlug()).isEqualTo("alice");
    ArgumentCaptor<WorkspaceMember> member = ArgumentCaptor.forClass(WorkspaceMember.class);
    verify(memberRepository).save(member.capture());
    assertThat(member.getValue().getRole()).isEqualTo(WorkspaceRole.OWNER);
    assertThat(member.getValue().getUser()).isSameAs(user);
  }

  @Test
  void organizationCreatorBecomesOwner() {
    User user = User.builder().id(UUID.randomUUID()).keycloakId(callerKeycloakId).build();
    when(userRepository.findByKeycloakId(callerKeycloakId)).thenReturn(Optional.of(user));
    when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.createOrganization(callerKeycloakId, new WorkspaceCreationDto("  Acme Corp "));

    ArgumentCaptor<Workspace> workspace = ArgumentCaptor.forClass(Workspace.class);
    verify(workspaceRepository).save(workspace.capture());
    assertThat(workspace.getValue().getType()).isEqualTo(WorkspaceType.ORGANIZATION);
    assertThat(workspace.getValue().getSlug()).isEqualTo("acme-corp");
    ArgumentCaptor<WorkspaceMember> member = ArgumentCaptor.forClass(WorkspaceMember.class);
    verify(memberRepository).save(member.capture());
    assertThat(member.getValue().getRole()).isEqualTo(WorkspaceRole.OWNER);
  }

  @Test
  void nonMemberGetsNotFound() {
    when(memberRepository.findByWorkspaceIdAndUserKeycloakId(workspaceId, callerKeycloakId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.requireRole(workspaceId, callerKeycloakId, WorkspaceRole.MEMBER))
        .isInstanceOf(WorkspaceNotFoundException.class);
  }

  @Test
  void memberLacksAdminRole() {
    stubCaller(WorkspaceRole.MEMBER);

    assertThatThrownBy(
            () -> service.requireRole(workspaceId, callerKeycloakId, WorkspaceRole.ADMIN))
        .isInstanceOf(UnauthorizedAccessException.class);
  }

  @Test
  void adminCannotAddAnotherAdmin() {
    stubCaller(WorkspaceRole.ADMIN);

    assertThatThrownBy(
            () ->
                service.addMember(
                    workspaceId,
                    callerKeycloakId,
                    new WorkspaceMemberAddDto("bob", WorkspaceRole.ADMIN)))
        .isInstanceOf(UnauthorizedAccessException.class);
    verify(memberRepository, never()).save(any());
  }

  @Test
  void nobodyCanBeAddedAsOwner() {
    stubCaller(WorkspaceRole.OWNER);

    assertThatThrownBy(
            () ->
                service.addMember(
                    workspaceId,
                    callerKeycloakId,
                    new WorkspaceMemberAddDto("bob", WorkspaceRole.OWNER)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void personalWorkspaceCannotHaveMembers() {
    Workspace personal = Workspace.builder().id(workspaceId).type(WorkspaceType.PERSONAL).build();
    when(memberRepository.findByWorkspaceIdAndUserKeycloakId(workspaceId, callerKeycloakId))
        .thenReturn(
            Optional.of(
                WorkspaceMember.builder().workspace(personal).role(WorkspaceRole.OWNER).build()));

    assertThatThrownBy(
            () ->
                service.addMember(
                    workspaceId,
                    callerKeycloakId,
                    new WorkspaceMemberAddDto("bob", WorkspaceRole.MEMBER)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void lastOwnerCannotLeave() {
    WorkspaceMember caller = stubCaller(WorkspaceRole.OWNER);
    UUID userId = UUID.randomUUID();
    when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
        .thenReturn(Optional.of(caller));
    when(memberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER))
        .thenReturn(1L);

    assertThatThrownBy(() -> service.removeMember(workspaceId, callerKeycloakId, userId))
        .isInstanceOf(ConflictException.class);
    verify(memberRepository, never()).delete(any());
  }

  @Test
  void adminCannotRemoveAnotherAdmin() {
    stubCaller(WorkspaceRole.ADMIN);
    UUID userId = UUID.randomUUID();
    when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
        .thenReturn(
            Optional.of(
                WorkspaceMember.builder()
                    .id(UUID.randomUUID())
                    .workspace(org)
                    .role(WorkspaceRole.ADMIN)
                    .build()));

    assertThatThrownBy(() -> service.removeMember(workspaceId, callerKeycloakId, userId))
        .isInstanceOf(UnauthorizedAccessException.class);
    verify(memberRepository, never()).delete(any());
  }

  @Test
  void memberCanLeave() {
    WorkspaceMember caller = stubCaller(WorkspaceRole.MEMBER);
    UUID userId = UUID.randomUUID();
    when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
        .thenReturn(Optional.of(caller));

    service.removeMember(workspaceId, callerKeycloakId, userId);

    verify(memberRepository).delete(caller);
  }

  private WorkspaceMember stubCaller(WorkspaceRole role) {
    WorkspaceMember caller =
        WorkspaceMember.builder().id(UUID.randomUUID()).workspace(org).role(role).build();
    when(memberRepository.findByWorkspaceIdAndUserKeycloakId(workspaceId, callerKeycloakId))
        .thenReturn(Optional.of(caller));
    return caller;
  }
}
