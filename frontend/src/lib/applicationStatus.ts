export type StatusVariant = "success" | "warning" | "danger" | "default";

export function statusBadge(status: string): {
  label: string;
  variant: StatusVariant;
} {
  switch (status) {
    case "PUSHED":
      return { label: "REPOSITORY PUSHED", variant: "success" };
    case "DEPLOYED":
      return { label: "DEPLOYED", variant: "success" };
    case "PENDING":
    case "CREATING":
      return { label: "PROVISIONING", variant: "warning" };
    case "FAILED":
      return { label: "FAILED", variant: "danger" };
    default:
      return { label: status, variant: "default" };
  }
}

export function isInProgress(status: string): boolean {
  return status === "CREATING" || status === "PENDING";
}
