output "vpc_id" {
  description = "VPC identifier for application and runner deployment."
  value       = module.network.vpc_id
}

output "public_edge_subnet_ids" {
  description = "Public subnet IDs reserved for a future TLS ingress load balancer."
  value       = module.network.public_subnet_ids
}

output "application_subnet_ids" {
  description = "Private subnet IDs for Linux application and centrally hosted Windows runners."
  value       = module.network.application_subnet_ids
}

output "worker_security_group_id" {
  description = "Security group for a future Windows worker fleet."
  value       = module.network.worker_security_group_id
}

output "application_security_group_id" {
  description = "Security group allowed to access PostgreSQL."
  value       = module.network.application_security_group_id
}

output "database_endpoint" {
  description = "Private RDS endpoint hostname and port."
  value       = module.postgres.endpoint
}

output "database_name" {
  description = "Configured database name."
  value       = module.postgres.database_name
}

output "database_master_secret_arn" {
  description = "Secrets Manager ARN created by RDS for its managed master credential."
  value       = module.postgres.master_user_secret_arn
}
