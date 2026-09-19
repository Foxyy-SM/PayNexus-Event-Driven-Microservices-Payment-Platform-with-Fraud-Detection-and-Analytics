resource "aws_vpc" "paynexus" {
  cidr_block           = "10.20.0.0/16"
  enable_dns_hostnames = true
  tags                 = { Name = "${var.project}-vpc" }
}

resource "aws_subnet" "public_a" {
  vpc_id                  = aws_vpc.paynexus.id
  cidr_block              = "10.20.1.0/24"
  availability_zone       = "${var.region}a"
  map_public_ip_on_launch = true
}

resource "aws_subnet" "public_b" {
  vpc_id                  = aws_vpc.paynexus.id
  cidr_block              = "10.20.2.0/24"
  availability_zone       = "${var.region}b"
  map_public_ip_on_launch = true
}

resource "aws_iam_role" "eks" {
  name = "${var.project}-eks"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = { Service = "eks.amazonaws.com" }
    }]
  })
}

resource "aws_eks_cluster" "paynexus" {
  name     = "${var.project}-eks"
  role_arn = aws_iam_role.eks.arn
  vpc_config {
    subnet_ids = [aws_subnet.public_a.id, aws_subnet.public_b.id]
  }
}

resource "aws_db_instance" "postgres" {
  identifier             = "${var.project}-rds"
  engine                 = "postgres"
  engine_version         = "16.4"
  instance_class         = "db.t4g.micro"
  allocated_storage      = 20
  username               = "paynexus"
  password               = var.database_password
  skip_final_snapshot    = true
  publicly_accessible    = false
}

resource "aws_secretsmanager_secret" "database" {
  name                    = "${var.project}/database"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "database" {
  secret_id = aws_secretsmanager_secret.database.id
  secret_string = jsonencode({
    host     = aws_db_instance.postgres.address
    port     = aws_db_instance.postgres.port
    username = aws_db_instance.postgres.username
    password = var.database_password
  })
}

resource "aws_secretsmanager_secret" "keycloak" {
  name                    = "${var.project}/keycloak"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "keycloak" {
  secret_id = aws_secretsmanager_secret.keycloak.id
  secret_string = jsonencode({
    adminUsername       = var.keycloak_admin_username
    adminPassword       = var.keycloak_admin_password
    paymentClientSecret = var.keycloak_payment_client_secret
  })
}

resource "aws_secretsmanager_secret" "observability" {
  name                    = "${var.project}/observability"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "observability" {
  secret_id = aws_secretsmanager_secret.observability.id
  secret_string = jsonencode({
    grafanaAdminUsername = var.grafana_admin_username
    grafanaAdminPassword = var.grafana_admin_password
  })
}

resource "aws_elasticache_cluster" "redis" {
  cluster_id           = "${var.project}-redis"
  engine               = "redis"
  node_type            = "cache.t4g.micro"
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
}

resource "aws_s3_bucket" "artifacts" {
  bucket = "${var.project}-artifacts-${var.region}"
}
