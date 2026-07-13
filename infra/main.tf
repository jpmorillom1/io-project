# Ubuntu 22.04 LTS (última AMI oficial de Canonical)
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# Generar clave RSA 4096
resource "tls_private_key" "this" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

# Subir la clave pública a AWS
resource "aws_key_pair" "this" {
  key_name   = "${var.project_name}-key"
  public_key = tls_private_key.this.public_key_openssh
}

# Guardar la clave privada en disco (permisos 0600) para poder hacer ssh de inmediato
resource "local_sensitive_file" "private_key" {
  content         = tls_private_key.this.private_key_pem
  filename        = "${path.module}/${var.project_name}-key.pem"
  file_permission = "0600"
}

resource "aws_security_group" "this" {
  # name_prefix (no name fijo): con create_before_destroy, Terraform crea el SG
  # nuevo *antes* de borrar el viejo, y dos SG no pueden compartir nombre en la VPC.
  name_prefix = "${var.project_name}-sg-"
  description = "SSH (+ HTTP/HTTPS opcional) para ${var.project_name}"

  lifecycle {
    create_before_destroy = true
  }

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_ingress_cidr]
  }

  # Cloudflare Tunnel (cloudflared) es saliente: no hace falta abrir 80/443 para
  # que la app sea accesible por https://<subdominio>.<dominio>. Estas reglas solo
  # existen si querés ademas acceso HTTP directo por la IP (enable_direct_http_access).
  dynamic "ingress" {
    for_each = var.enable_direct_http_access ? [80, 443] : []
    content {
      description = "HTTP/HTTPS directo (acceso opcional por IP, sin el tunel)"
      from_port   = ingress.value
      to_port     = ingress.value
      protocol    = "tcp"
      cidr_blocks = [var.http_ingress_cidr]
    }
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-sg"
  }
}

resource "aws_instance" "this" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  key_name               = aws_key_pair.this.key_name
  vpc_security_group_ids = [aws_security_group.this.id]

  root_block_device {
    volume_size = var.root_volume_size
    volume_type = "gp3"
  }

  # Postgres/Chroma quedan en 127.0.0.1 dentro del contenedor (ver docker-compose.yml
  # en la raíz del repo). El tráfico web entra por Cloudflare Tunnel (saliente desde
  # el contenedor cloudflared), no por el security group.
  user_data = <<-EOF
    #!/bin/bash
    set -e

    # Swap: el build de Gradle + npm dentro de docker compose se queda sin RAM en instancias chicas
    fallocate -l 4G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab

    apt-get update -y
    apt-get install -y ca-certificates curl git
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
      https://download.docker.com/linux/ubuntu \
      $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
      | tee /etc/apt/sources.list.d/docker.list > /dev/null
    apt-get update -y
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    usermod -aG docker ubuntu
    systemctl enable docker
    systemctl start docker
  EOF

  tags = {
    Name = var.project_name
  }
}

resource "aws_eip" "this" {
  instance = aws_instance.this.id
  domain   = "vpc"

  tags = {
    Name = "${var.project_name}-eip"
  }
}
