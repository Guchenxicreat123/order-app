#!/usr/bin/env bash
# ============================================
# 点餐小程序 - 一键构建 + 部署脚本
# 用法:
#   bash deploy.sh               # 完整构建 + 重启
#   bash deploy.sh --no-build    # 不构建，只重启
#   bash deploy.sh --migrate     # 强制手动跑数据库迁移
# ============================================

set -e
cd "$(dirname "$0")"

PROJECT_ROOT="$(pwd)"
NO_BUILD=false
RUN_MIGRATE=false
for arg in "$@"; do
  case $arg in
    --no-build) NO_BUILD=true ;;
    --migrate) RUN_MIGRATE=true ;;
  esac
done

echo "============================================"
echo "  点餐小程序 - 构建 & 部署"
echo "============================================"

# ---- 1. 重建 admin-web dist ----
if [ "$NO_BUILD" = false ]; then
  echo "[1/5] 构建 admin-web..."
  (cd admin-web && npm ci 2>/dev/null || npm install)
  (cd admin-web && npm run build)
  if [ $? -ne 0 ]; then echo "FAIL: admin-web build"; exit 1; fi
  echo "  OK admin-web/dist generated"
fi

# ---- 2. .env 存在性 ----
if [ ! -f .env ]; then
  echo "FAIL: .env not found (cp .env.example .env)"
  exit 1
fi

# ---- 3. 数据库迁移 ----
echo "[2/5] 应用数据库迁移..."
if docker compose ps mysql 2>/dev/null | grep -q "Up"; then
  for sql in backend/migrations/*.sql; do
    [ -f "$sql" ] || continue
    name=$(basename "$sql")
    ROOT_PW=$(grep '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2 | tr -d '\"\r')
    [ -z "$ROOT_PW" ] && ROOT_PW=<ROOT_PASSWORD>
    EXISTS=$(docker compose exec -T mysql mysql -uroot -p"$ROOT_PW" -N -B order_app -e \
      "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='order_app' AND table_name='__migration_log'" 2>/dev/null || echo 0)
    if [ "$EXISTS" = "0" ]; then
      docker compose exec -T mysql mysql -uroot -p"$ROOT_PW" order_app \
        -e "CREATE TABLE __migration_log (name VARCHAR(64) PRIMARY KEY, run_at DATETIME DEFAULT CURRENT_TIMESTAMP)" 2>/dev/null || true
    fi
    ALREADY=$(docker compose exec -T mysql mysql -uroot -p"$ROOT_PW" -N -B order_app -e \
      "SELECT COUNT(*) FROM __migration_log WHERE name='$name'" 2>/dev/null | tr -d '[:space:]' || echo 0)
    if [ "$ALREADY" = "0" ] || [ "$RUN_MIGRATE" = true ]; then
      echo "  -> run: $name"
      docker compose exec -T mysql mysql -uroot -p"$ROOT_PW" order_app < "$sql"
      docker compose exec -T mysql mysql -uroot -p"$ROOT_PW" order_app \
        -e "INSERT INTO __migration_log (name) VALUES ('$name')" 2>/dev/null || true
    else
      echo "  skip (already run): $name"
    fi
  done
else
  echo "  warn: mysql container not running, skip migration (first start runs init.sql automatically)"
fi

# ---- 4. 重建镜像 ----
if [ "$NO_BUILD" = false ]; then
  echo "[3/5] 构建 Docker 镜像..."
  docker compose build --no-cache app admin-api admin-web
fi

# ---- 5. 重启 ----
echo "[4/5] 重启服务..."
docker compose up -d --remove-orphans

# ---- 6. 健康检查 ----
echo "[5/5] 健康检查..."
sleep 5
echo "---- 容器状态 ----"
docker compose ps
echo "---- 后端日志 ----"
docker compose logs --tail=15 app 2>&1 | tail -15
echo ""
echo "============================================"
echo "  DEPLOY DONE"
echo "  API  : http://localhost:8006/api/..."
echo "  Admin: http://localhost:8007  (独立管理服务 admin-api:8008，仅内网)"
echo "============================================"
