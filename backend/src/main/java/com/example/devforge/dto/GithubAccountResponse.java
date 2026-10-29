package com.example.devforge.dto;

import java.io.Serializable;

public record GithubAccountResponse(boolean connected, String login) implements Serializable {}
