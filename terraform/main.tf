locals {
  name_prefix = "${var.project_name}-${var.environment}"
  common_tags = merge(var.tags, {
    Application = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  })
}

data "aws_availability_zones" "available" {
  state = "available"
}

module "network" {
  source = "./modules/network"

  name_prefix             = local.name_prefix
  vpc_cidr                = var.vpc_cidr
  availability_zones      = slice(data.aws_availability_zones.available.names, 0, 2)
  public_subnet_cidrs     = var.public_subnet_cidrs
  application_subnet_cidrs  = var.application_subnet_cidrs
  data_subnet_cidrs         = var.data_subnet_cidrs
  edge_ingress_cidrs        = var.edge_ingress_cidrs
  nat_gateway_enabled       = var.nat_gateway_enabled
  nat_gateway_per_az        = var.nat_gateway_per_az
  tags                      = local.common_tags
}

module "postgres" {
  source = "./modules/postgres"

  name_prefix             = local.name_prefix
  vpc_id                  = module.network.vpc_id
  data_subnet_ids         = module.network.data_subnet_ids
  application_security_group_id = module.network.application_security_group_id
  database_name                 = var.database_name
  database_username             = var.database_username
  engine_version                = var.postgres_engine_version
  instance_class                = var.postgres_instance_class
  allocated_storage             = var.postgres_allocated_storage
  max_allocated_storage         = var.postgres_max_allocated_storage
  backup_retention_days         = var.postgres_backup_retention_days
  multi_az                      = var.postgres_multi_az
  deletion_protection           = var.postgres_deletion_protection
  skip_final_snapshot           = var.postgres_skip_final_snapshot
  tags                          = local.common_tags
}
