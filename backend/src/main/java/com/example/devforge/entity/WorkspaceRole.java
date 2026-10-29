package com.example.devforge.entity;

public enum WorkspaceRole {
  OWNER,
  ADMIN,
  MEMBER;

  public boolean atLeast(WorkspaceRole required) {
    return ordinal() <= required.ordinal();
  }
}
