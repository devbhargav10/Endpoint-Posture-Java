output "endpoint" {
  description = "Private RDS hostname and port."
  value       = aws_db_instance.this.endpoint
}

output "database_name" {
  value = aws_db_instance.this.db_name
}

output "master_user_secret_arn" {
  description = "ARN of the RDS-managed master credential secret; no secret value is exposed."
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}
