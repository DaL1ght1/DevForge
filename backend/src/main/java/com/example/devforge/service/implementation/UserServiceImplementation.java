package com.example.devforge.service.implementation;

import com.example.devforge.config.keycloak.KeycloakAdminClient;
import com.example.devforge.dto.UserCreationDto;
import com.example.devforge.dto.UserResponseDto;
import com.example.devforge.dto.UserUpdateDto;
import com.example.devforge.entity.User;
import com.example.devforge.entity.UserRole;
import com.example.devforge.exception.UnauthorizedAccessException;
import com.example.devforge.exception.UserAlreadyExistsException;
import com.example.devforge.exception.UserNotFoundException;
import com.example.devforge.mapper.UserMapper;
import com.example.devforge.repository.UserRepository;
import com.example.devforge.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImplementation implements UserService {

    private final UserRepository userRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final UserMapper userMapper;

    @Override
    public UserResponseDto registerUser(UserCreationDto dto) {

        String username = dto.username().toLowerCase(Locale.ROOT);
        String email = dto.email().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Username or email already registered");
        }
        UUID keycloakId = keycloakAdminClient.createUser(dto);
        UserRole role = UserRole.DEVELOPER;
        try {
            keycloakAdminClient.assignRealmRole(keycloakId, role.name());
            User saved = userRepository.saveAndFlush(User.builder()
                    .keycloakId(keycloakId)
                    .username(username)
                    .email(email)
                    .firstName(dto.firstName().trim())
                    .lastName(dto.lastName().trim())
                    .role(role)
                    .build());
            return userMapper.toResponseDto(saved);
        } catch (Exception e) {
            log.error("Registration failed after Keycloak user {} was created - rolling back", keycloakId, e);
            keycloakAdminClient.deleteUser(keycloakId);
            throw e;
        }
    }

    @Override
    public UserResponseDto getOrCreateCurrentUser(Jwt jwt) {
        UUID keycloakId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
        return userRepository.findByKeycloakId(keycloakId)
                .map(userMapper::toResponseDto)
                .orElseGet(() -> createFromToken(keycloakId, jwt));
    }


    @Override
    public UserResponseDto getUserById(UUID id) {
        return userRepository.findById(id)
                .map(userMapper::toResponseDto)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }

    @Override
    public UserResponseDto updateUser(UUID actualKeycloakId, UUID id, UserUpdateDto dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        if (!(user.getKeycloakId().equals(actualKeycloakId))) {
            throw new UnauthorizedAccessException("You are not authorized to update this user");
        }

        String username = dto.username().trim().toLowerCase(Locale.ROOT);
        String email = dto.email().trim().toLowerCase(Locale.ROOT);
        String firstName = dto.firstName().trim();
        String lastName = dto.lastName().trim();

        if (!username.equals(user.getUsername()) && userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("Username already taken");
        }
        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email already taken");
        }
        keycloakAdminClient.updateUser(user.getKeycloakId(), username, email, firstName, lastName);

        if (dto.password() != null && !dto.password().isBlank()) {
            keycloakAdminClient.resetPassword(user.getKeycloakId(), dto.password());
        }

        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);

        try {
            return userMapper.toResponseDto(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            log.error("Local DB update failed after Keycloak update succeeded for user {}", id, e);
            throw e;
        }
    }

    @Override
    @Transactional
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        keycloakAdminClient.deleteUser(user.getKeycloakId());
        userRepository.delete(user);

    }

    @Override
    public Page<UserResponseDto> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(userMapper::toResponseDto);
    }

    private UserResponseDto createFromToken(UUID keycloakId, Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        String username = jwt.getClaimAsString("preferred_username");
        User user = User.builder()
                .keycloakId(keycloakId)
                .username(username != null ? username : email)
                .email(email)
                .firstName(orEmpty(jwt.getClaimAsString("given_name")))
                .lastName(orEmpty(jwt.getClaimAsString("family_name")))
                .role(roleFrom(jwt))
                .build();
        try {
            return userMapper.toResponseDto(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            log.error("Failed to create user from token: {}", keycloakId, e);
            return userRepository.findByKeycloakId(keycloakId)
                    .map(userMapper::toResponseDto)
                    .orElseThrow(() -> e);
        }
    }

    private UserRole roleFrom(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
            for (UserRole r : UserRole.values()) {
                if (roles.contains(r.name())) {
                    return r;
                }
            }
        }
        return UserRole.DEVELOPER;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}


