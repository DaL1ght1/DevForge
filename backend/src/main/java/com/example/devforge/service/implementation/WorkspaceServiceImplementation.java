package com.example.devforge.service.implementation;

import com.example.devforge.dto.WorkspaceCreationDto;
import com.example.devforge.dto.WorkspaceMemberAddDto;
import com.example.devforge.dto.WorkspaceMemberResponse;
import com.example.devforge.dto.WorkspaceResponse;
import com.example.devforge.entity.User;
import com.example.devforge.entity.Workspace;
import com.example.devforge.entity.WorkspaceMember;
import com.example.devforge.entity.WorkspaceRole;
import com.example.devforge.entity.WorkspaceType;
import com.example.devforge.exception.ConflictException;
import com.example.devforge.exception.ResourceNotFoundException;
import com.example.devforge.exception.UnauthorizedAccessException;
import com.example.devforge.exception.UserNotFoundException;
import com.example.devforge.exception.WorkspaceNotFoundException;
import com.example.devforge.mapper.WorkspaceMapper;
import com.example.devforge.repository.UserRepository;
import com.example.devforge.repository.WorkspaceMemberRepository;
import com.example.devforge.repository.WorkspaceRepository;
import com.example.devforge.service.WorkspaceService;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceServiceImplementation implements WorkspaceService {

  private static final int MAX_SLUG_BASE_LENGTH = 100;

  private final WorkspaceRepository workspaceRepository;
  private final WorkspaceMemberRepository memberRepository;
  private final UserRepository userRepository;
  private final WorkspaceMapper workspaceMapper;

  @Override
  @Transactional
  public Workspace getOrCreatePersonal(User user) {
    return workspaceRepository
        .findByTypeAndPersonalOwnerId(WorkspaceType.PERSONAL, user.getId())
        .orElseGet(
            () -> {
              Workspace workspace =
                  workspaceRepository.save(
                      Workspace.builder()
                          .type(WorkspaceType.PERSONAL)
                          .name(user.getUsername())
                          .slug(uniqueSlug(user.getUsername()))
                          .personalOwner(user)
                          .build());
              memberRepository.save(
                  WorkspaceMember.builder()
                      .workspace(workspace)
                      .user(user)
                      .role(WorkspaceRole.OWNER)
                      .build());
              log.info(
                  "Created personal workspace {} for user {}", workspace.getId(), user.getId());
              return workspace;
            });
  }

  @Override
  @Transactional(readOnly = true)
  public WorkspaceMember requireRole(UUID workspaceId, UUID keycloakId, WorkspaceRole minRole) {
    WorkspaceMember membership =
        memberRepository
            .findByWorkspaceIdAndUserKeycloakId(workspaceId, keycloakId)
            .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
    if (!membership.getRole().atLeast(minRole)) {
      throw new UnauthorizedAccessException(
          "This action requires the " + minRole + " role in the workspace");
    }
    return membership;
  }

  @Override
  @Transactional
  public WorkspaceResponse createOrganization(UUID keycloakId, WorkspaceCreationDto dto) {
    User user = requireUser(keycloakId);
    String name = dto.name().trim();
    Workspace workspace =
        workspaceRepository.save(
            Workspace.builder()
                .type(WorkspaceType.ORGANIZATION)
                .name(name)
                .slug(uniqueSlug(name))
                .build());
    WorkspaceMember owner =
        memberRepository.save(
            WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .build());
    log.info("User {} created organization workspace {}", user.getId(), workspace.getId());
    return workspaceMapper.toResponse(owner);
  }

  @Override
  @Transactional
  public List<WorkspaceResponse> listForUser(UUID keycloakId) {
    getOrCreatePersonal(requireUser(keycloakId));
    return memberRepository.findAllByUserKeycloakIdOrderByCreatedAtAsc(keycloakId).stream()
        .map(workspaceMapper::toResponse)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public WorkspaceResponse getWorkspace(UUID workspaceId, UUID keycloakId) {
    return workspaceMapper.toResponse(requireRole(workspaceId, keycloakId, WorkspaceRole.MEMBER));
  }

  @Override
  @Transactional(readOnly = true)
  public List<WorkspaceMemberResponse> listMembers(UUID workspaceId, UUID keycloakId) {
    requireRole(workspaceId, keycloakId, WorkspaceRole.MEMBER);
    return memberRepository.findAllByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
        .map(workspaceMapper::toMemberResponse)
        .toList();
  }

  @Override
  @Transactional
  public WorkspaceMemberResponse addMember(
      UUID workspaceId, UUID keycloakId, WorkspaceMemberAddDto dto) {
    WorkspaceMember caller = requireRole(workspaceId, keycloakId, WorkspaceRole.ADMIN);
    requireOrganization(caller.getWorkspace());
    if (dto.role() == WorkspaceRole.OWNER) {
      throw new IllegalArgumentException("New members can only be added as ADMIN or MEMBER");
    }
    if (dto.role() == WorkspaceRole.ADMIN && caller.getRole() != WorkspaceRole.OWNER) {
      throw new UnauthorizedAccessException("Only an owner can add admins");
    }
    String key = dto.usernameOrEmail().trim().toLowerCase(Locale.ROOT);
    User user =
        userRepository
            .findByUsername(key)
            .or(() -> userRepository.findByEmail(key))
            .orElseThrow(() -> new UserNotFoundException("No user found for " + key));
    if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, user.getId())) {
      throw new ConflictException("User is already a member of this workspace");
    }
    WorkspaceMember member =
        memberRepository.save(
            WorkspaceMember.builder()
                .workspace(caller.getWorkspace())
                .user(user)
                .role(dto.role())
                .build());
    return workspaceMapper.toMemberResponse(member);
  }

  @Override
  @Transactional
  public void removeMember(UUID workspaceId, UUID keycloakId, UUID userId) {
    WorkspaceMember caller = requireRole(workspaceId, keycloakId, WorkspaceRole.MEMBER);
    requireOrganization(caller.getWorkspace());
    WorkspaceMember target =
        memberRepository
            .findByWorkspaceIdAndUserId(workspaceId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found in workspace"));

    boolean leavingSelf = target.getId().equals(caller.getId());
    if (!leavingSelf) {
      boolean allowed =
          caller.getRole() == WorkspaceRole.OWNER
              || (caller.getRole() == WorkspaceRole.ADMIN
                  && target.getRole() == WorkspaceRole.MEMBER);
      if (!allowed) {
        throw new UnauthorizedAccessException("You cannot remove this member");
      }
    }
    if (target.getRole() == WorkspaceRole.OWNER
        && memberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER) <= 1) {
      throw new ConflictException("A workspace must keep at least one owner");
    }
    memberRepository.delete(target);
  }

  private User requireUser(UUID keycloakId) {
    return userRepository
        .findByKeycloakId(keycloakId)
        .orElseThrow(
            () -> new UserNotFoundException("User not found with KeycloakId " + keycloakId));
  }

  private void requireOrganization(Workspace workspace) {
    if (workspace.getType() != WorkspaceType.ORGANIZATION) {
      throw new IllegalArgumentException("Personal workspaces do not have other members");
    }
  }

  private String uniqueSlug(String source) {
    String base = slugify(source);
    String slug = base;
    while (workspaceRepository.existsBySlug(slug)) {
      byte[] suffix = new byte[3];
      ThreadLocalRandom.current().nextBytes(suffix);
      slug = base + "-" + HexFormat.of().formatHex(suffix);
    }
    return slug;
  }

  static String slugify(String source) {
    String slug =
        source.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    if (slug.length() > MAX_SLUG_BASE_LENGTH) {
      slug = slug.substring(0, MAX_SLUG_BASE_LENGTH).replaceAll("-+$", "");
    }
    return slug.isEmpty() ? "workspace" : slug;
  }
}
