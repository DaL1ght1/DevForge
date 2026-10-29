package com.example.devforge.entity;

public enum GithubAccountType {
  USER,
  ORGANIZATION;

  public static GithubAccountType fromGithub(String type) {
    return "Organization".equalsIgnoreCase(type) ? ORGANIZATION : USER;
  }
}
