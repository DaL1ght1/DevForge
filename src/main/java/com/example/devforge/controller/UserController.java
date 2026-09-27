package com.example.devforge.controller;


import com.example.devforge.dto.UserCreationDto;
import com.example.devforge.dto.UserResponseDto;
import com.example.devforge.dto.UserUpdateDto;
import com.example.devforge.service.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @SecurityRequirements
    @PostMapping("/register")
    public UserResponseDto registerUser(@RequestBody @Valid UserCreationDto dto) {
        return userService.registerUser(dto);
    }

    @PostMapping("/current")
    public UserResponseDto getOrCreateCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return userService.getOrCreateCurrentUser(jwt);
    }

    @GetMapping("/{id}")
    public UserResponseDto getUserById(@PathVariable UUID id) {
        return userService.getUserById(id);
    }

    @PutMapping("/{id}")
    public UserResponseDto updateUser(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID id,
                                      @RequestBody @Valid UserUpdateDto dto) {
        UUID actualKeycloakId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
        return userService.updateUser(actualKeycloakId,id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
    }

    @GetMapping
    public Page<UserResponseDto> listUsers(@PageableDefault(sort = "createdAt") Pageable pageable) {
        return userService.listUsers(pageable);
    }
}
