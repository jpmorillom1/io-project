output "instance_id" {
  value = aws_instance.this.id
}

output "public_ip" {
  description = "IP elástica — estable entre reinicios/redeploys de la instancia"
  value       = aws_eip.this.public_ip
}

output "private_key_path" {
  description = "Ruta local del .pem generado para conectarse por SSH"
  value       = local_sensitive_file.private_key.filename
}

output "ssh_command" {
  value = "ssh -i ${local_sensitive_file.private_key.filename} ubuntu@${aws_eip.this.public_ip}"
}

output "app_url" {
  description = "URL pública de la app, servida vía Cloudflare Tunnel (HTTPS real)"
  value       = "https://${local.hostname}"
}

output "cloudflare_tunnel_token" {
  description = "Token para el contenedor cloudflared (TUNNEL_TOKEN en .env / secret CLOUDFLARE_TUNNEL_TOKEN en GitHub)"
  value       = data.cloudflare_zero_trust_tunnel_cloudflared_token.this.token
  sensitive   = true
}
