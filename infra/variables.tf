variable "aws_region" {
  description = "Región de AWS donde se despliega la instancia"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Prefijo usado para nombrar todos los recursos"
  type        = string
  default     = "io-platform"
}

variable "instance_type" {
  description = "Tipo de instancia EC2. El build de Gradle (backend) + npm (frontend) dentro de docker compose necesita al menos 2GB de RAM."
  type        = string
  default     = "t3.small"
}

variable "root_volume_size" {
  description = "Tamaño (GB) del volumen raíz — imágenes Docker + caché de build"
  type        = number
  default     = 30
}

variable "ssh_ingress_cidr" {
  description = "CIDR permitido para SSH (22). Restringilo a tu IP en producción, ej. \"1.2.3.4/32\"."
  type        = string
  default     = "0.0.0.0/0"
}

variable "http_ingress_cidr" {
  description = "CIDR permitido para HTTP/HTTPS (80/443), donde nginx sirve el frontend y reenvía /api"
  type        = string
  default     = "0.0.0.0/0"
}
