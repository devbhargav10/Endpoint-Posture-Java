variable "name_prefix" {
  type = string
}
variable "vpc_cidr" {
  type = string
}
variable "availability_zones" {
  type = list(string)

  validation {
    condition     = length(var.availability_zones) == 2
    error_message = "This foundation expects exactly two availability zones."
  }
}
variable "public_subnet_cidrs" {
  type = list(string)
}
variable "application_subnet_cidrs" {
  type = list(string)
}
variable "data_subnet_cidrs" {
  type = list(string)
}
variable "edge_ingress_cidrs" {
  type = list(string)
}
variable "nat_gateway_enabled" {
  type = bool
}
variable "nat_gateway_per_az" {
  type = bool
}
variable "tags" {
  type = map(string)
}
