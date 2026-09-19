terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.region
}

variable "region" {
  type    = string
  default = "ap-south-1"
}

variable "project" {
  type    = string
  default = "paynexus"
}

variable "database_password" {
  type        = string
  sensitive   = true
  description = "Initial RDS password. Supply with TF_VAR_database_password or a secure CI secret."
}

variable "keycloak_admin_username" {
  type        = string
  default     = "admin"
  description = "Keycloak bootstrap administrator username."
}

variable "keycloak_admin_password" {
  type        = string
  sensitive   = true
  description = "Keycloak bootstrap administrator password."
}

variable "keycloak_payment_client_secret" {
  type        = string
  sensitive   = true
  description = "Confidential Keycloak payment client secret."
}

variable "grafana_admin_username" {
  type        = string
  default     = "admin"
  description = "Grafana administrator username."
}

variable "grafana_admin_password" {
  type        = string
  sensitive   = true
  description = "Grafana administrator password."
}

output "secret_arns" {
  description = "Secrets Manager ARNs consumed by External Secrets."
  value = {
    database      = aws_secretsmanager_secret.database.arn
    keycloak      = aws_secretsmanager_secret.keycloak.arn
    observability = aws_secretsmanager_secret.observability.arn
  }
}
