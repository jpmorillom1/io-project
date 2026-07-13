# Infra — EC2 + Cloudflare Tunnel para io-platform

Terraform que levanta **una** instancia EC2 (Ubuntu 22.04) lista para correr
`docker-compose.yml` de la raíz del repo: Security Group (solo SSH por
defecto), key pair SSH generado localmente, IP elástica, Docker + Docker
Compose ya instalados vía `user_data`, y un **Cloudflare Tunnel** que expone
la app en `https://<subdominio>.<tu-dominio>` sin abrir 80/443 a internet.

El deploy continuo (push a `master`) lo maneja
`.github/workflows/deploy.yml` — este README cubre solo la infraestructura
y el primer arranque manual.

## 1. Prerrequisitos

- Terraform ≥ 1.5
- Credenciales de AWS configuradas (`aws configure` o variables `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`)
- Permisos IAM para EC2, VPC (security groups), y Elastic IP
- Un dominio dado de alta en Cloudflare (nameservers apuntando ahí)
- Un API Token de Cloudflare (dashboard → My Profile → API Tokens → Create
  Token) con permisos:
  - `Account` → `Cloudflare Tunnel:Edit`
  - `Zone` → `DNS:Edit` (sobre la zona de tu dominio)
- El `Account ID` y el `Zone ID` de tu dominio (dashboard de Cloudflare,
  barra lateral derecha de la zona)

## 2. Provisionar

```bash
cd infra
cp terraform.tfvars.example terraform.tfvars
# editá terraform.tfvars: ssh_ingress_cidr, cloudflare_api_token,
# cloudflare_account_id, cloudflare_zone_id, root_domain, subdomain

terraform init
terraform plan
terraform apply
```

`terraform output` muestra:
- `public_ip` / `ssh_command` — para entrar por SSH
- `app_url` — `https://<subdominio>.<tu-dominio>`, ya con HTTPS real vía Cloudflare
- `cloudflare_tunnel_token` (sensible) — `terraform output -raw cloudflare_tunnel_token`

La clave privada SSH queda en `infra/io-platform-key.pem` (gitignoreada).

## 3. Primer deploy manual

```bash
# desde la raíz del repo (no infra/)
IP=$(terraform -chdir=infra output -raw public_ip)
KEY=infra/io-platform-key.pem

rsync -avz --exclude node_modules --exclude .git --exclude 'io-api/build' \
  --exclude infra \
  -e "ssh -i $KEY" ./ ubuntu@$IP:~/io-project/

ssh -i $KEY ubuntu@$IP
```

Ya en la instancia:

```bash
cd ~/io-project
cp .env.example .env
nano .env
# agregá, además de GROQ_API_KEY/GROQ_MODEL_NAME:
# TUNNEL_TOKEN=<salida de: terraform output -raw cloudflare_tunnel_token>
# CORS_ALLOWED_ORIGINS=<salida de: terraform output -raw app_url>

docker compose up -d --build
```

`CORS_ALLOWED_ORIGINS` tiene que ser el `app_url` real (ej. `https://pivot.jpap.dev`).
El navegador manda el header `Origin` incluso en requests same-origin detrás
del proxy de nginx; si no matchea esta lista, Spring responde **403** a
`/api/**` (ver `WebConfig.java`) aunque front y back estén bajo el mismo dominio.

`RAG_REINGESTAR` viene en `true` por defecto en `docker-compose.yml`: cada
`docker compose up --build` reingesta el corpus en Chroma. Si en algún
momento se vuelve muy lento y no hace falta reingestar en cada deploy,
pasar `RAG_REINGESTAR=false` como variable de entorno antes del comando.

La app queda en la `app_url` de Terraform (`https://<subdominio>.<tu-dominio>`).
El contenedor `cloudflared` abre una conexión saliente hacia el borde de
Cloudflare y reenvía todo al contenedor `ui` (nginx), que sirve el frontend
y reenvía `/api/*` al backend por la red interna de Docker. Postgres y
ChromaDB solo escuchan en `127.0.0.1` dentro de la instancia — no son
alcanzables desde internet, y con `enable_direct_http_access = false` (el
default) tampoco lo es el puerto 80 directo por IP: todo el tráfico entra
por el túnel.

## 4. Deploy continuo (GitHub Actions)

`.github/workflows/deploy.yml` corre en cada push a `master`. Necesita estos
secrets en el repo (Settings → Secrets and variables → Actions):

| Secret | Valor |
|---|---|
| `EC2_HOST` | `terraform output -raw public_ip` |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | contenido completo de `infra/io-platform-key.pem` |
| `GROQ_API_KEY` | tu API key real de Groq |
| `GROQ_MODEL_NAME` | ej. `llama-3.3-70b-versatile` |
| `CLOUDFLARE_TUNNEL_TOKEN` | `terraform output -raw cloudflare_tunnel_token` |
| `APP_URL` | `terraform output -raw app_url` (ej. `https://pivot.jpap.dev`) — usado como `CORS_ALLOWED_ORIGINS` |

## 5. Redeploy manual (sin GitHub Actions)

```bash
rsync -avz --exclude node_modules --exclude .git --exclude 'io-api/build' --exclude infra \
  -e "ssh -i infra/io-platform-key.pem" ./ ubuntu@$IP:~/io-project/
ssh -i infra/io-platform-key.pem ubuntu@$IP "cd ~/io-project && docker compose up -d --build"
```

## 6. Destruir

```bash
cd infra
terraform destroy
```

Esto borra la instancia, el security group, el key pair, la IP elástica y
el túnel + registro DNS en Cloudflare. Los datos de Postgres/Chroma viven en
volúmenes Docker *dentro* de la instancia — se pierden junto con ella.

## Notas

- `instance_type` por defecto es `t3.small` (2GB RAM) — el build de Gradle +
  npm dentro de `docker compose up --build` necesita memoria; el `user_data`
  ya agrega 4GB de swap como colchón. Si el build se cuelga o el proceso muere
  (OOM), subí a `t3.medium`.
- El security group solo abre SSH por defecto. `enable_direct_http_access = true`
  agrega 80/443 si además querés poder entrar por `http://<public_ip>` sin pasar
  por el túnel (útil para debug, no hace falta para que la app funcione).
- `ssh_ingress_cidr` viene en `0.0.0.0/0` por defecto para simplicidad;
  restringilo a tu IP en `terraform.tfvars` antes de aplicar en un entorno real.
- El navegador exige un "contexto seguro" (HTTPS o `localhost`) para APIs como
  `crypto.randomUUID()` que usa el frontend — por eso hace falta el túnel
  (o cualquier otra fuente de HTTPS real) y no alcanza con la IP pública en HTTP.
