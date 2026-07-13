# Infra — EC2 para io-platform

Terraform que levanta **una** instancia EC2 (Ubuntu 22.04) lista para correr
`docker-compose.yml` de la raíz del repo: Security Group (22/80/443), key pair
SSH generado localmente, IP elástica y Docker + Docker Compose ya instalados
vía `user_data`.

No incluye CI/CD ni copia el código automáticamente — eso queda en el paso
"deploy" de más abajo.

## 1. Prerrequisitos

- Terraform ≥ 1.5
- Credenciales de AWS configuradas (`aws configure` o variables `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`)
- Permisos IAM para EC2, VPC (security groups), y Elastic IP

## 2. Provisionar

```bash
cd infra
cp terraform.tfvars.example terraform.tfvars
# editá terraform.tfvars: al menos restringí ssh_ingress_cidr a tu IP

terraform init
terraform plan
terraform apply
```

Al terminar, `terraform output` muestra `public_ip` y `ssh_command`. La clave
privada queda guardada en `infra/io-platform-key.pem` (gitignoreada — no se
commitea).

## 3. Primer deploy

```bash
# desde la raíz del repo (no infra/)
IP=$(terraform -chdir=infra output -raw public_ip)
KEY=infra/io-platform-key.pem

# copiar el proyecto (o hacé `git clone` de tu remoto directo en la instancia)
rsync -avz --exclude node_modules --exclude .git --exclude 'io-api/build' \
  -e "ssh -i $KEY" ./ ubuntu@$IP:~/io-project/

ssh -i $KEY ubuntu@$IP
```

Ya en la instancia:

```bash
cd ~/io-project
cp .env.example .env
nano .env   # GROQ_API_KEY real, etc.

docker compose up -d --build
```

`RAG_REINGESTAR` viene en `true` por defecto en `docker-compose.yml`: cada
`docker compose up --build` reingesta el corpus en Chroma. Si en algún
momento se vuelve muy lento y no hace falta reingestar en cada deploy,
pasar `RAG_REINGESTAR=false` como variable de entorno antes del comando.

La app queda en `http://<public_ip>/` — nginx sirve el frontend y reenvía
`/api/*` al backend por la red interna de Docker. Postgres y ChromaDB solo
escuchan en `127.0.0.1` dentro de la instancia (ver `docker-compose.yml`),
no son alcanzables desde internet.

## 4. Redeploys posteriores

```bash
rsync -avz --exclude node_modules --exclude .git --exclude 'io-api/build' \
  -e "ssh -i infra/io-platform-key.pem" ./ ubuntu@$IP:~/io-project/
ssh -i infra/io-platform-key.pem ubuntu@$IP "cd ~/io-project && docker compose up -d --build"
```

## 5. Destruir

```bash
cd infra
terraform destroy
```

Esto borra la instancia, el security group, el key pair y la IP elástica.
Los datos de Postgres/Chroma viven en volúmenes Docker *dentro* de la
instancia — se pierden junto con ella.

## Notas

- `instance_type` por defecto es `t3.small` (2GB RAM) — el build de Gradle +
  npm dentro de `docker compose up --build` necesita memoria; el `user_data`
  ya agrega 4GB de swap como colchón. Si el build se cuelga o el proceso muere
  (OOM), subí a `t3.medium`.
- HTTPS (443) está abierto en el security group para cuando agregues TLS
  (ej. certbot + nginx), pero por ahora el `ui` del compose solo escucha 80.
- `ssh_ingress_cidr` viene en `0.0.0.0/0` por defecto para simplicidad;
  restringilo a tu IP en `terraform.tfvars` antes de aplicar en un entorno real.
