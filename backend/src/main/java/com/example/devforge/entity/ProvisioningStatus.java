package com.example.devforge.entity;

public enum ProvisioningStatus {
  PENDING,
  GENERATING,
  CREATING_REPOSITORY,
  CONFIGURING_CI,
  BUILDING,
  DEPLOYING,
  COMPLETED,
  FAILED
}
