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
  createdAt: string;
  templateVersionId?: string;
  stableKey?: string;
}

export type TemplateVariableType = "STRING" | "ENUM" | "BOOLEAN";

export interface TemplateVariable {
  name: string;
  label: string;
  type: TemplateVariableType;
  required: boolean;
  defaultValue: string | null;
  options: string[];
  pattern: string | null;
}

export interface TemplateDependency {
  group: string | null;
  artifact: string;
  version: string | null;
  scope: string | null;
}

export interface TemplateFeatureOption {
  id: string;
  label: string;
  description: string | null;
  /** Option or group ids that must also be selected. */
  requires: string[];
  /** Option or group ids that must not be selected. */
  conflicts: string[];
  dependencies: TemplateDependency[];
}

export interface TemplateFeatureGroup {
  id: string;
  label: string;
  /** CHOICE: at most one option (exactly one when required). MULTI: any subset. */
  kind: "CHOICE" | "MULTI";
  required: boolean;
  options: TemplateFeatureOption[];
}

export interface TemplateOptionsResponse {
  templateId: string;
  templateVersionId: string;
  version: string;
  contentHash: string | null;
  schemaVersion: number;
  variables: TemplateVariable[];
  features: TemplateFeatureGroup[];
}

export interface ApplicationServiceInput {
  name: string;
  description?: string;
  templateVersionId: string;
  variables?: Record<string, string>;
  features?: Record<string, string[]>;
}

export interface ApplicationCreationDto {
  name: string;
  description?: string;
  workspaceId?: string;
  services: ApplicationServiceInput[];
}

export interface ApplicationServiceSummary {
  id: string;
  name: string;
  path: string;
  port: number | null;
  status: ServiceStatus | string;
  templateName: string;
  templateVersion: string;
  repositoryUrl: string | null;
}

export interface ApplicationResponse {
  id: string;
  name: string;
  description: string | null;
  status: ServiceStatus | string;
  repositoryUrl: string | null;
  repositoryOwner: string | null;
  failureReason: string | null;
  workspace: { id: string; name: string; slug: string; type: string };
  createdAt: string;
  updatedAt: string;
  services: ApplicationServiceSummary[];
}

export interface PreviewFileEntry {
  path: string;
  /** "base", "devforge" or "feature:<optionId>". */
  origin: string;
  size: number;
  binary: boolean;
  omitted: boolean;
}

export interface ServicePreviewResponse {
  previewId: string;
  templateName: string;
  templateVersion: string;
  fileCount: number;
  totalBytes: number;
  files: PreviewFileEntry[];
}

export interface PreviewFileResponse extends PreviewFileEntry {
  content: string | null;
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
  /** Values for the template's manifest variables. */
  variables?: Record<string, string>;
  /** Feature group id -> selected option ids, e.g. { database: ["postgresql"] }. */
  features?: Record<string, string[]>;
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
