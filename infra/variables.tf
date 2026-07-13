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
  description = "CIDR permitido para HTTP/HTTPS (80/443) si se habilita enable_direct_http_access"
  type        = string
  default     = "0.0.0.0/0"
}

variable "enable_direct_http_access" {
  description = "Abre 80/443 en el security group además del Cloudflare Tunnel (para poder entrar directo por la IP mientras se depura). Con el túnel activo no hace falta."
  type        = bool
  default     = false
}

# ── Cloudflare Tunnel ──────────────────────────────────────────────────────────
# El dominio debe estar dado de alta en Cloudflare (nameservers apuntando ahí).
# account_id y zone_id salen del dashboard de Cloudflare (barra lateral derecha
# de la zona). El api_token necesita permisos "Cloudflare Tunnel:Edit" y
# "DNS:Edit" sobre esa zona (User API Tokens > Create Token).

variable "cloudflare_api_token" {
  description = "API token de Cloudflare (permisos: Account > Cloudflare Tunnel:Edit, Zone > DNS:Edit)"
  type        = string
  sensitive   = true
}

variable "cloudflare_account_id" {
  description = "Account ID de Cloudflare (dashboard > barra lateral derecha)"
  type        = string
}

variable "cloudflare_zone_id" {
  description = "Zone ID del dominio en Cloudflare (dashboard de la zona > barra lateral derecha)"
  type        = string
}

variable "root_domain" {
  description = "Dominio raíz dado de alta en Cloudflare, ej. \"midominio.com\""
  type        = string
}

variable "subdomain" {
  description = "Subdominio donde queda publicada la app, ej. \"app\" -> app.midominio.com. Dejar vacío (\"\") para usar el dominio raíz."
  type        = string
  default     = "app"
}
