locals {
  hostname = var.subdomain != "" ? "${var.subdomain}.${var.root_domain}" : var.root_domain
}

resource "cloudflare_zero_trust_tunnel_cloudflared" "this" {
  account_id = var.cloudflare_account_id
  name       = "${var.project_name}-tunnel"
  config_src = "cloudflare"
}

# Ingress gestionado desde la API de Cloudflare (config_src = "cloudflare"): el
# contenedor cloudflared no necesita un config.yml local, solo el token del túnel.
resource "cloudflare_zero_trust_tunnel_cloudflared_config" "this" {
  account_id = var.cloudflare_account_id
  tunnel_id  = cloudflare_zero_trust_tunnel_cloudflared.this.id

  config = {
    ingress = [
      {
        hostname = local.hostname
        # nginx del contenedor `ui` (ver docker-compose.yml) sirve el front y
        # reenvia /api/* al backend — cloudflared solo necesita apuntar ahí.
        service = "http://ui:80"
      },
      {
        service = "http_status:404"
      }
    ]
  }
}

resource "cloudflare_dns_record" "this" {
  zone_id = var.cloudflare_zone_id
  name    = var.subdomain != "" ? var.subdomain : "@"
  type    = "CNAME"
  content = "${cloudflare_zero_trust_tunnel_cloudflared.this.id}.cfargotunnel.com"
  ttl     = 1 # "Auto" — requerido cuando proxied = true
  proxied = true
}

# Token que usa `cloudflared tunnel run` (vía env var TUNNEL_TOKEN) para
# autenticarse y levantar la conexión saliente hacia el borde de Cloudflare.
data "cloudflare_zero_trust_tunnel_cloudflared_token" "this" {
  account_id = var.cloudflare_account_id
  tunnel_id  = cloudflare_zero_trust_tunnel_cloudflared.this.id
}
