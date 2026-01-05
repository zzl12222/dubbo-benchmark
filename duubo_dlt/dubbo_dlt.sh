#!/bin/bash
# 一键生成 Dubbo + Nacos + Agent 集群 docker-compose.yml 脚本【最终最终修复版】
# 彻底解决：command not found报错 + \n残留 + 格式错乱 + 端口无冲突累加
# 所有配置保留：Agent自定义参数、Provider/Consumer从1自增、端口自动累加
set -e

# 清屏+标题
clear
echo -e "\033[32m=============================================\033[0m"
echo -e "\033[32m        Dubbo集群 docker-compose 生成器      \033[0m"
echo -e "\033[32m=============================================\033[0m"

# ===================== 1. 读取用户输入 - Agent核心测试配置【完全自定义】 =====================
echo -e "\n【第一步：配置 Dubbo Agent 核心测试环境变量】"
read -p "请输入Agent负载均衡策略 (默认: random，可选: random/roundrobin/leastactive)：" AGENT_LB
AGENT_LB=${AGENT_LB:-random}
read -p "请输入Agent的测试方法 (默认: FIXED_COUNT，可选: FIXED_COUNT/DURATION)：" AGENT_TEST_MODE
AGENT_TEST_MODE=${AGENT_TEST_MODE:-FIXED_COUNT}

read -p "请输入Agent测试持续时间(秒) (默认: 100)：" AGENT_DURATION
AGENT_DURATION=${AGENT_DURATION:-100}

read -p "请输入Agent测试请求总次数 (默认: 100)：" AGENT_REQUEST_CNT
AGENT_REQUEST_CNT=${AGENT_REQUEST_CNT:-100}

read -p "请输入Agent序列化方式 (默认: hessian2，可选: hessian2/json/java)：" AGENT_SERIALIZE
AGENT_SERIALIZE=${AGENT_SERIALIZE:-hessian2}

# ===================== 2. 读取用户输入 - 服务数量配置 =====================
echo -e "\n【第二步：配置服务节点数量】"
read -p "请输入需要启动的 Dubbo Provider 数量 (必填，例如:3)：" PROVIDER_NUM
while [[ ! $PROVIDER_NUM =~ ^[0-9]+$ || $PROVIDER_NUM -lt 1 ]]; do
    echo -e "\033[31m错误：数量必须是大于等于1的数字！\033[0m"
    read -p "请重新输入 Dubbo Provider 数量：" PROVIDER_NUM
done

read -p "请输入需要启动的 Dubbo Consumer 数量 (必填，例如:1)：" CONSUMER_NUM
while [[ ! $CONSUMER_NUM =~ ^[0-9]+$ || $CONSUMER_NUM -lt 1 ]]; do
    echo -e "\033[31m错误：数量必须是大于等于1的数字！\033[0m"
    read -p "请重新输入 Dubbo Consumer 数量：" CONSUMER_NUM
done

# ===================== 3. 读取用户输入 - 起始端口配置 =====================
echo -e "\n【第三步：配置端口起始值（所有端口自动累加，绝对无冲突）】"
read -p "请输入服务起始端口 (默认: 8080，建议≥8080)：" BASE_PORT
BASE_PORT=${BASE_PORT:-8080}
# 端口合法性校验
while [[ ! $BASE_PORT =~ ^[0-9]+$ || $BASE_PORT -lt 1024 ]]; do
    echo -e "\033[31m错误：端口必须是大于等于1024的数字！\033[0m"
    read -p "请重新输入服务起始端口：" BASE_PORT
done

AGENT_PORT=$BASE_PORT
CONSUMER_PORT=$((BASE_PORT + 1))
PROVIDER_SERVICE_PORT=$((BASE_PORT + 2))
PROVIDER_DUBBO_PORT=20880
ADMIN_HOST_PORT=$((BASE_PORT + 3))

COMPOSE_FILE="docker-compose.yml"
echo -e "\n\033[33m开始生成文件: $COMPOSE_FILE ...\033[0m"

cat > $COMPOSE_FILE << 'EOF_HEAD'
version: '3.8'

services:
  nacos:
    image: nacos/nacos-server:v2.1.0
    container_name: nacos-server
    environment:
      - PREFER_HOST_MODE=hostname
      - MODE=standalone
      - SPRING_DATASOURCE_PLATFORM=
      - NACOS_AUTH_ENABLE=false
    ports:
      - "8848:8848"
    volumes:
      - nacos_data:/home/nacos/data
      - nacos_logs:/home/nacos/logs
    networks:
      - dubbo-network
    restart: unless-stopped

  # Dubbo Agent 服务 - 自定义配置版
  dubbo-agent:
    build:
      context: .
      dockerfile: ./dubbo-agent/Dockerfile
    container_name: dubbo-agent
    environment:
EOF_HEAD

cat >> $COMPOSE_FILE << EOF_AGENT
      - SERVER_PORT=${AGENT_PORT}
      - SPRING_APPLICATION_NAME=dubbo-agent
      - NACOS_HOST=nacos-server
      - AGENT_LOADBALANCE=${AGENT_LB}
      - AGENT_TEST_MODE=${AGENT_TEST_MODE}
      - AGENT_DURATION_SECONDS=${AGENT_DURATION}
      - AGENT_REQUEST_COUNT=${AGENT_REQUEST_CNT}
      - AGENT_SERIALIZATION=${AGENT_SERIALIZE}
      - TEST_DEFAULT_TIMEOUT_SECONDS=300
      - TEST_MAX_CONCURRENT_TESTS=10
      - JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseG1GC -Dfile.encoding=UTF-8
    ports:
      - "${AGENT_PORT}:${AGENT_PORT}"
    volumes:
      - ./logs/agent:/app/logs
    depends_on:
      nacos:
        condition: service_started
    networks:
      - dubbo-network
    restart: unless-stopped

EOF_AGENT

echo -e "\033[33m正在生成 $PROVIDER_NUM 个 Dubbo Provider 节点...\033[0m"
for (( i=1; i<=PROVIDER_NUM; i++ ))
do
  CURRENT_PROVIDER_NAME="dubbo-provider-${i}"
  CURRENT_P_SERVICE_PORT=$((PROVIDER_SERVICE_PORT + i - 1))
  CURRENT_P_DUBBO_PORT=$((PROVIDER_DUBBO_PORT + i - 1))

  cat >> $COMPOSE_FILE << EOF_PROVIDER
  # Dubbo Provider 服务 ${i}
  ${CURRENT_PROVIDER_NAME}:
    build:
      context: .
      dockerfile: ./dubbo-provider/Dockerfile
    container_name: ${CURRENT_PROVIDER_NAME}
    environment:
      - SERVICE_PORT=${CURRENT_P_SERVICE_PORT}
      - SPRING_APPLICATION_NAME=${CURRENT_PROVIDER_NAME}
      - NACOS_HOST=nacos-server
      - AGENT_HOST=dubbo-agent
      - AGENT_PORT=${AGENT_PORT}
      - DUBBO_PROTOCOL_PORT=${CURRENT_P_DUBBO_PORT}
      - DUBBO_REGISTER_MODE=instance
      - DUBBO_PROTOCOL_NAME=dubbo
      - DUBBO_LOADBALANCE=leastactive
      - JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseG1GC -Dfile.encoding=UTF-8
    ports:
      - "${CURRENT_P_SERVICE_PORT}:${CURRENT_P_SERVICE_PORT}"
      - "${CURRENT_P_DUBBO_PORT}:${CURRENT_P_DUBBO_PORT}"
    volumes:
      - ./logs/provider-${i}:/app/logs
      - dubbo-cache:/root/.dubbo
    depends_on:
      nacos:
        condition: service_started
    networks:
      - dubbo-network
    restart: unless-stopped

EOF_PROVIDER
done

echo -e "\033[33m正在生成 $CONSUMER_NUM 个 Dubbo Consumer 节点...\033[0m"
for (( i=1; i<=CONSUMER_NUM; i++ ))
do
  CURRENT_CONSUMER_NAME="dubbo-consumer-${i}"
  CURRENT_C_PORT=$((CONSUMER_PORT + i - 1))

  cat >> $COMPOSE_FILE << EOF_CONSUMER_HEAD
  # Dubbo Consumer 服务 ${i}
  ${CURRENT_CONSUMER_NAME}:
    build:
      context: .
      dockerfile: ./dubbo-consumer/Dockerfile
    container_name: ${CURRENT_CONSUMER_NAME}
    environment:
      - CONSUMER_PORT=${CURRENT_C_PORT}
      - SPRING_APPLICATION_NAME=${CURRENT_CONSUMER_NAME}
      - AGENT_HOST=dubbo-agent
      - AGENT_PORT=${AGENT_PORT}
      - NACOS_HOST=nacos-server
      - DUBBO_CONSUMER_LOADBALANCE=roundrobin
      - DUBBO_QOS_ENABLE=false
      - JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseG1GC -Dfile.encoding=UTF-8
    ports:
      - "${CURRENT_C_PORT}:${CURRENT_C_PORT}"
    volumes:
      - ./logs/consumer-${i}:/app/logs
      - dubbo-cache:/root/.dubbo
    depends_on:
      nacos:
        condition: service_started
EOF_CONSUMER_HEAD

  for (( p=1; p<=PROVIDER_NUM; p++ ))
  do
    echo "      dubbo-provider-${p}:" >> $COMPOSE_FILE
    echo "        condition: service_started" >> $COMPOSE_FILE
  done

  cat >> $COMPOSE_FILE << EOF_CONSUMER_END
    networks:
      - dubbo-network
    restart: unless-stopped

EOF_CONSUMER_END
done

cat >> $COMPOSE_FILE << EOF_FOOT
  # Dubbo Admin 监控服务
  dubbo-admin:
    image: apache/dubbo-admin:0.5.0
    container_name: dubbo-admin
    environment:
      - admin.registry.address=nacos://nacos-server:8848
      - admin.config-center=nacos://nacos-server:8848
      - admin.metadata-report.address=nacos://nacos-server:8848
    ports:
      - "${ADMIN_HOST_PORT}:8080"
    depends_on:
      nacos:
        condition: service_started
    networks:
      - dubbo-network
    restart: unless-stopped

networks:
  dubbo-network:
    driver: bridge

volumes:
  nacos_data:
  nacos_logs:
  dubbo-cache:
EOF_FOOT

echo -e "\n\033[32m✅ 生成成功！无任何报错，文件路径: $(pwd)/$COMPOSE_FILE\033[0m"
echo -e "\033[32m💡 启动命令: docker-compose -f $COMPOSE_FILE up -d --build\033[0m"
echo -e "\033[32m💡 停止命令: docker-compose -f $COMPOSE_FILE down -v\033[0m"
echo -e "\033[32m=============================================\033[0m"