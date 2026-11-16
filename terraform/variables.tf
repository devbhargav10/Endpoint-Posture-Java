variable "project_name" {
  description = "Short lowercase project prefix used in resource names."
  type        = string
  default     = "endpoint-posture"
}

variable "environment" {
  description = "Isolated deployment environment name."
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be dev, staging, or prod."
  }
}

variable "aws_region" {
  description = "AWS region selected for this environment."
  type        = string
}

variable "vpc_cidr" {
  description = "Non-overlapping VPC CIDR, coordinated with endpoint-site networks."
  type        = string
}

variable "public_subnet_cidrs" {
  description = "Two public edge subnet CIDRs, one per selected availability zone."
  type        = list(string)
}

variable "application_subnet_cidrs" {
  description = "Two private application and worker subnet CIDRs."
  type        = list(string)
}

variable "data_subnet_cidrs" {
  description = "Two isolated database subnet CIDRs."
  type        = list(string)
}

variable "edge_ingress_cidrs" {
  description = "Approved client ingress CIDRs for HTTPS. Empty means no public edge ingress rule."
  type        = list(string)
  default     = []
}

variable "nat_gateway_enabled" {
  description = "Create NAT gateway egress for private application subnets. Adds hourly and data charges."
  type        = bool
  default     = false
}

variable "nat_gateway_per_az" {
  description = "When NAT is enabled, create one gateway per AZ for availability at additional cost."
  type        = bool
  default     = false
}

variable "database_name" {
  description = "Initial PostgreSQL database name."
  type        = string
  default     = "endpoint_posture"
}

variable "database_username" {
  description = "RDS-managed master username; its password is managed by RDS in Secrets Manager."
  type        = string
  default     = "ep_admin"
}

variable "postgres_engine_version" {
  description = "Exact PostgreSQL 16 minor version supported in the selected region. Confirm before deployment."
  type        = string
}

variable "postgres_instance_class" {
  description = "RDS PostgreSQL instance class sized for this environment."
  type        = string
}

variable "postgres_allocated_storage" {
  description = "Initial encrypted RDS storage size in GiB."
  type        = number
  default     = 20
}

variable "postgres_max_allocated_storage" {
  description = "Maximum autoscaled storage in GiB."
  type        = number
  default     = 100
}

variable "postgres_backup_retention_days" {
  description = "Automated backup retention; production should use an approved recovery policy."
  type        = number
  default     = 7
}

variable "postgres_multi_az" {
  description = "Enable synchronous Multi-AZ standby for production availability."
  type        = bool
  default     = false
}

variable "postgres_deletion_protection" {
  description = "Protect the database from accidental deletion."
  type        = bool
  default     = true
}

variable "postgres_skip_final_snapshot" {
  description = "Skip the final snapshot only for disposable environments."
  type        = bool
  default     = false
}

variable "tags" {
  description = "Additional organization-required resource tags."
  type        = map(string)
  default     = {}
}
