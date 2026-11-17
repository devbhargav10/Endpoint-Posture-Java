resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-postgres"
  subnet_ids = var.data_subnet_ids
  tags       = merge(var.tags, { Name = "${var.name_prefix}-postgres-subnets" })
}

resource "aws_security_group" "this" {
  name        = "${var.name_prefix}-postgres"
  description = "Private PostgreSQL ingress from backend application only."
  vpc_id      = var.vpc_id
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.application_security_group_id]
  }
  tags = merge(var.tags, { Name = "${var.name_prefix}-postgres-sg" })
}

resource "aws_db_instance" "this" {
  identifier                  = "${var.name_prefix}-postgres"
  engine                      = "postgres"
  engine_version              = var.engine_version
  instance_class              = var.instance_class
  allocated_storage           = var.allocated_storage
  max_allocated_storage       = var.max_allocated_storage
  storage_type                = "gp3"
  storage_encrypted           = true
  db_name                     = var.database_name
  username                    = var.database_username
  manage_master_user_password = true
  port                        = 5432
  db_subnet_group_name        = aws_db_subnet_group.this.name
  vpc_security_group_ids      = [aws_security_group.this.id]
  publicly_accessible         = false
  multi_az                    = var.multi_az
  backup_retention_period     = var.backup_retention_days
  auto_minor_version_upgrade  = true
  deletion_protection         = var.deletion_protection
  skip_final_snapshot         = var.skip_final_snapshot
  final_snapshot_identifier   = var.skip_final_snapshot ? null : "${var.name_prefix}-postgres-final"
  copy_tags_to_snapshot       = true
  apply_immediately           = false
  tags                        = merge(var.tags, { Name = "${var.name_prefix}-postgres" })
}
