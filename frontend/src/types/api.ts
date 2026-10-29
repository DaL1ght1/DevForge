export type ServiceStatus =
  "CREATING" | "PENDING" | "PUSHED" | "DEPLOYED" | "FAILED";

export type DeploymentStatus = "DEPLOYING" | "PENDING" | "DEPLOYED" | "FAILED";

export type UserRole = "DEVELOPER" | "ADMIN";

export type TemplateLanguage = "JAVA" | "GO" | "PYTHON" | "TYPESCRIPT" | string;

export type TemplateFramework =
  "SPRING_BOOT" | "GIN" | "FASTAPI" | "NEXT_JS" | string;

export type BuildTool = "MAVEN" | "GRADLE" | "GO_MODULES" | "PIP" | string;

export type SocialProvider = "google" | "github";

export interface TemplateFilter {
  framework?: TemplateFramework;
  buildTool?: BuildTool;
}

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
  language: TemplateLanguage | string;
  framework: TemplateFramework | string;
  buildTool: BuildTool | string;
  databaseType: string;
  createdAt: string;
  templateVersionId?: string;
  stableKey?: string;
}

export interface TemplateSynchronizationResult {
  scanned: number;
  synchronizedTemplates: number;
  unchanged: number;
  failed: number;
}

export interface AppServiceCreationDto {
  name: string;
  description?: string;
  templateVersionId: string;
  databaseType: string;
  language?: string;
  framework?: string;
  buildTool?: string;
  packageIdentifier?: string;
  port?: number;
  /** Target workspace; omitted means the caller's personal workspace. */
  workspaceId?: string;
}

export interface AppServiceResponse {
  id: string;
  name: string;
  description?: string;
  repositoryUrl?: string;
  repositoryOwner?: string;
  failureReason?: string;
  status: ServiceStatus;
  templateVersion: TemplateVersionResponse;
  owner: UserResponseDto;
  workspace?: WorkspaceSummaryResponse;
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

export interface SignupPendingResponse {
  email: string;
  username: string;
  firstName: string;
  lastName: string;
}
export interface CompleteSignupRequest {
  signupToken: string;
  username: string;
  firstName: string;
  lastName: string;
  password: string;
}
export interface SessionResponse {
  user: UserResponseDto | null;
  expiresIn: number;
}

export type WorkspaceType = "PERSONAL" | "ORGANIZATION";

export type WorkspaceRole = "OWNER" | "ADMIN" | "MEMBER";

export interface WorkspaceSummaryResponse {
  id: string;
  name: string;
  slug: string;
  type: WorkspaceType;
}

/** A workspace as seen by the current user, including their role in it. */
export interface WorkspaceResponse extends WorkspaceSummaryResponse {
  role: WorkspaceRole;
  createdAt: string;
}

export interface WorkspaceMemberResponse {
  userId: string;
  username: string;
  email: string;
  role: WorkspaceRole;
  joinedAt: string;
}

export interface WorkspaceMemberAddDto {
  usernameOrEmail: string;
  role: Exclude<WorkspaceRole, "OWNER">;
}

export type GithubAccountType = "USER" | "ORGANIZATION";

export type GithubInstallationStatus = "ACTIVE" | "SUSPENDED" | "REMOVED";

export interface GithubAccountResponse {
  connected: boolean;
  login?: string;
}

export interface GithubInstallationResponse {
  id: string;
  installationId: number;
  accountLogin: string;
  accountType: GithubAccountType;
  repositorySelection?: string;
  status: GithubInstallationStatus;
  workspaceId: string;
  createdAt: string;
}

export interface GithubInstallationCandidateResponse {
  installationId: number;
  accountLogin: string;
  accountType: GithubAccountType;
  repositorySelection?: string;
  linked: boolean;
  available: boolean;
}

export interface GithubRedirectResponse {
  url: string;
}
