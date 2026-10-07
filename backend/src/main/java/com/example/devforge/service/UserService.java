package com.example.devforge.service;

import com.example.devforge.dto.UserCreationDto;
import com.example.devforge.dto.UserResponseDto;
import com.example.devforge.dto.UserUpdateDto;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;

public interface UserService {
  UserResponseDto registerUser(UserCreationDto dto);

  UserResponseDto getOrCreateCurrentUser(Jwt jwt);

  UserResponseDto getUserById(UUID id);

  UserResponseDto updateUser(UUID actualKeycloakId, UUID id, UserUpdateDto dto);

  void deleteUser(UUID id);

  Page<UserResponseDto> listUsers(Pageable pageable);
}
