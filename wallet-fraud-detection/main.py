import asyncio
import json
import os
from contextlib import asynccontextmanager
from datetime import datetime
from aiokafka import AIOKafkaConsumer
from fastapi import FastAPI
from pydantic import BaseModel
from typing import List
import redis.asyncio as redis

# --- Configuration & Constants from Environment ---
KAFKA_BROKER = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9094")
KAFKA_TOPIC = os.getenv("KAFKA_TOPIC", "transaction-events")
REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", None) or None
REDIS_DB = int(os.getenv("REDIS_DB", "1"))

# Risk Thresholds
HIGH_VALUE_THRESHOLD = float(os.getenv("HIGH_VALUE_THRESHOLD", "50000000"))
MAX_TX_PER_MINUTE = int(os.getenv("MAX_TX_PER_MINUTE", "5"))

# Khởi tạo Redis Client Async
redis_client = redis.Redis(
    host=REDIS_HOST,
    port=REDIS_PORT,
    password=REDIS_PASSWORD,
    db=REDIS_DB,
    decode_responses=True
)


async def consume_kafka():
    """Tiến trình nền lắng nghe sự kiện giao dịch từ Kafka và đánh giá quy tắc rủi ro."""
    consumer = AIOKafkaConsumer(
        KAFKA_TOPIC,
        bootstrap_servers=KAFKA_BROKER,
        group_id="fraud-detection-group",
        value_deserializer=lambda m: json.loads(m.decode("utf-8")),
    )

    await consumer.start()
    print(f"[Phat hien gian lan] Da ket noi Kafka {KAFKA_BROKER}. Dang giam sat su kien giao dich...")

    try:
        async for msg in consumer:
            payload = msg.value
            account_id = payload.get("accountId")
            amount = float(payload.get("amount", 0))
            tx_type = payload.get("type")

            if not account_id:
                continue

            print(f"[Phat hien gian lan] Soi giao dich: {tx_type} - {amount:,.0f} VND tu vi {account_id}")

            detected_alerts = []

            # ==========================================================
            # RULE 1: Phát hiện giao dịch số tiền lớn đột biến (High-Value)
            # ==========================================================
            if amount >= HIGH_VALUE_THRESHOLD:
                alert_msg = f"Giao dịch giá trị lớn bất thường: {amount:,.0f} VND (Vượt ngưỡng {HIGH_VALUE_THRESHOLD:,.0f} VND)"
                print(f"[CANH BAO GIAN LAN] {alert_msg}")
                detected_alerts.append({"rule": "HIGH_VALUE", "message": alert_msg})

            # ==========================================================
            # RULE 2: Phát hiện spam giao dịch tần suất cao (Velocity)
            # ==========================================================
            redis_key = f"fraud:tx_count:{account_id}"
            count = await redis_client.incr(redis_key)
            if count == 1:
                await redis_client.expire(redis_key, 60)

            if count > MAX_TX_PER_MINUTE:
                alert_msg = f"Tài khoản {account_id} spam giao dịch liên tục ({count} lần/phút)!"
                print(f"[CANH BAO GIAN LAN] {alert_msg}")
                detected_alerts.append({"rule": "VELOCITY_SPAM", "message": alert_msg})

            # ==========================================================
            # LƯU VẾT CẢNH BÁO VÀO REDIS CHO DASHBOARD
            # ==========================================================
            for alert in detected_alerts:
                record = {
                    "accountId": account_id,
                    "amount": amount,
                    "type": tx_type,
                    "rule": alert["rule"],
                    "message": alert["message"],
                    "timestamp": datetime.now().isoformat()
                }
                await redis_client.lpush("fraud:alerts", json.dumps(record))
                await redis_client.ltrim("fraud:alerts", 0, 99)

    except Exception as e:
        print(f"Lỗi Kafka Consumer: {e}")
    finally:
        await consumer.stop()


@asynccontextmanager
async def lifespan(app: FastAPI):
    kafka_task = asyncio.create_task(consume_kafka())
    yield
    kafka_task.cancel()
    await redis_client.close()


# --- Schema định nghĩa DTO cho Swagger OpenAPI ---
class FraudAlertItem(BaseModel):
    accountId: str
    amount: float
    type: str
    rule: str
    message: str
    timestamp: str


class FraudAlertResponse(BaseModel):
    total: int
    alerts: List[FraudAlertItem]


# --- Khởi tạo FastAPI App với Swagger Metadata ---
app = FastAPI(
    title="07. Fraud Detection & Risk Management API (Phát hiện Gian lận)",
    description="Phân hệ giám sát an ninh tài chính thời gian thực bằng Python FastAPI qua luồng sự kiện Apache Kafka",
    version="1.0.0",
    lifespan=lifespan
)


@app.get(
    "/alerts",
    response_model=FraudAlertResponse,
    tags=["07. Fraud Detection API"],
    summary="31. Tra cứu danh sách cảnh báo gian lận thời gian thực",
    description="Chỉ dành cho Quản trị viên (ROLE_ADMIN) và Kiểm toán viên (ROLE_AUDITOR). Trả về tối đa 100 cảnh báo mới nhất từ Redis"
)
async def get_fraud_alerts():
    """Lấy danh sách các cảnh báo gian lận gần nhất"""
    raw_alerts = await redis_client.lrange("fraud:alerts", 0, -1)
    return {
        "total": len(raw_alerts),
        "alerts": [json.loads(item) for item in raw_alerts]
    }


@app.get("/", tags=["07. Fraud Detection API"], summary="Kiểm tra trạng thái Service (Health Check)")
def health_check():
    return {"status": "ok", "service": "Dịch vụ phát hiện gian lận đang hoạt động"}