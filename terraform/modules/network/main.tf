resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(var.tags, { Name = "${var.name_prefix}-vpc" })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
  tags   = merge(var.tags, { Name = "${var.name_prefix}-igw" })
}

resource "aws_subnet" "public" {
  count                   = length(var.availability_zones)
  vpc_id                  = aws_vpc.this.id
  availability_zone       = var.availability_zones[count.index]
  cidr_block              = var.public_subnet_cidrs[count.index]
  map_public_ip_on_launch = false
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-edge-${count.index + 1}"
    Tier = "edge"
  })
}

resource "aws_subnet" "application" {
  count                   = length(var.availability_zones)
  vpc_id                  = aws_vpc.this.id
  availability_zone       = var.availability_zones[count.index]
  cidr_block              = var.application_subnet_cidrs[count.index]
  map_public_ip_on_launch = false
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-application-${count.index + 1}"
    Tier = "application"
  })
}

resource "aws_subnet" "data" {
  count                   = length(var.availability_zones)
  vpc_id                  = aws_vpc.this.id
  availability_zone       = var.availability_zones[count.index]
  cidr_block              = var.data_subnet_cidrs[count.index]
  map_public_ip_on_launch = false
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-data-${count.index + 1}"
    Tier = "data"
  })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }
  tags = merge(var.tags, { Name = "${var.name_prefix}-edge-rt" })
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_eip" "nat" {
  count  = var.nat_gateway_enabled ? (var.nat_gateway_per_az ? length(var.availability_zones) : 1) : 0
  domain = "vpc"
  tags   = merge(var.tags, { Name = "${var.name_prefix}-nat-eip-${count.index + 1}" })
}

resource "aws_nat_gateway" "this" {
  count         = length(aws_eip.nat)
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id
  depends_on    = [aws_internet_gateway.this]
  tags          = merge(var.tags, { Name = "${var.name_prefix}-nat-${count.index + 1}" })
}

resource "aws_route_table" "application" {
  count  = length(var.availability_zones)
  vpc_id = aws_vpc.this.id
  dynamic "route" {
    for_each = var.nat_gateway_enabled ? [1] : []
    content {
      cidr_block     = "0.0.0.0/0"
      nat_gateway_id = aws_nat_gateway.this[var.nat_gateway_per_az ? count.index : 0].id
    }
  }
  tags = merge(var.tags, { Name = "${var.name_prefix}-application-rt-${count.index + 1}" })
}

resource "aws_route_table_association" "application" {
  count          = length(aws_subnet.application)
  subnet_id      = aws_subnet.application[count.index].id
  route_table_id = aws_route_table.application[count.index].id
}

resource "aws_route_table" "data" {
  vpc_id = aws_vpc.this.id
  tags   = merge(var.tags, { Name = "${var.name_prefix}-data-rt" })
}

resource "aws_route_table_association" "data" {
  count          = length(aws_subnet.data)
  subnet_id      = aws_subnet.data[count.index].id
  route_table_id = aws_route_table.data.id
}

resource "aws_security_group" "edge" {
  name        = "${var.name_prefix}-edge"
  description = "TLS ingress for an explicitly approved edge load balancer."
  vpc_id      = aws_vpc.this.id

  dynamic "ingress" {
    for_each = var.edge_ingress_cidrs
    content {
      description = "HTTPS from approved client range"
      from_port   = 443
      to_port     = 443
      protocol    = "tcp"
      cidr_blocks = [ingress.value]
    }
  }
  tags = merge(var.tags, { Name = "${var.name_prefix}-edge-sg" })
}

resource "aws_security_group" "worker" {
  name        = "${var.name_prefix}-worker"
  description = "Centrally hosted Windows runner; no inbound rules."
  vpc_id      = aws_vpc.this.id
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = merge(var.tags, { Name = "${var.name_prefix}-worker-sg" })
}

resource "aws_security_group" "application" {
  name        = "${var.name_prefix}-application"
  description = "Linux backend ingress only from edge and enrolled worker fleet."
  vpc_id      = aws_vpc.this.id
  ingress {
    description     = "HTTP from internal TLS proxy/load balancer"
    from_port       = 8090
    to_port         = 8090
    protocol        = "tcp"
    security_groups = [aws_security_group.edge.id]
  }
  ingress {
    description     = "Worker API calls from central Windows runner fleet"
    from_port       = 8090
    to_port         = 8090
    protocol        = "tcp"
    security_groups = [aws_security_group.worker.id]
  }
  tags = merge(var.tags, { Name = "${var.name_prefix}-application-sg" })
}

resource "aws_security_group" "database" {
  name        = "${var.name_prefix}-database"
  description = "PostgreSQL access only from the backend application security group."
  vpc_id      = aws_vpc.this.id
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.application.id]
  }
  tags = merge(var.tags, { Name = "${var.name_prefix}-database-sg" })
}
