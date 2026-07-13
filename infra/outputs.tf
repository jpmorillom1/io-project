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
