export type ServiceStatus = "CREATING" | "PENDING" | "DEPLOYED" | "FAILED";

export type DeploymentStatus = "DEPLOYING" | "PENDING" | "DEPLOYED" | "FAILED";

export type UserRole = "DEVELOPER" | "ADMIN";

export type TemplateLanguage = "JAVA" | "GO" | "PYTHON" | "TYPESCRIPT";

export type TemplateFramework = "SPRING_BOOT" | "GIN" | "FASTAPI" | "NEXT_JS";

export type BuildTool = "MAVEN" | "GRADLE" | "GO_MODULES" | "PIP";

export interface UserResponseDto {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  role: UserRole;
  createdAt: string;
}

export interface UserCreationDto {
  username: string;
  email: string;
  password: string;
  firstName: string;
  lastName: string;
}

export interface UserUpdateDto {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  password?: string;
}

export interface TemplateVersionResponse {
  id: string;
  version: string;
  sourcePath: string;
  active: boolean;
  createdAt: string;
}

export interface AppTemplateResponse {
  id: string;
  name: string;
  language: TemplateLanguage;
  framework: TemplateFramework;
  buildTool: BuildTool;
  databaseType: string;
  createdAt: string;
}

export interface AppServiceCreationDto {
  name: string;
  description?: string;
  templateVersionId: string;
  databaseType: string;
}

export interface AppServiceResponse {
  id: string;
  name: string;
  description?: string;
  repositoryUrl?: string;
  status: ServiceStatus;
  templateVersion: TemplateVersionResponse;
  owner: UserResponseDto;
  createdAt: string;
  updatedAt: string;
}

export interface AppDeploymentResponse {
  id: string;
  serviceId: string;
  environment: string;
  version: string;
  status: DeploymentStatus;
  deployedAt: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  user?: UserResponseDto | null;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  numberOfElements: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface ApiErrorResponse {
  timestamp?: string;
  status: number;
  error: string;
  message: string;
  path?: string;
  validationErrors?: Record<string, string>;
}
