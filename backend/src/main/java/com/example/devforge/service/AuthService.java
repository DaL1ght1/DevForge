package com.example.devforge.service;

import com.example.devforge.dto.AuthResponse;
import com.example.devforge.dto.LoginRequest;
import com.example.devforge.dto.RefreshTokenRequest;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshTokenRequest request);
}
