from fastapi import FastAPI
from pydantic import BaseModel
import numpy as np
from sklearn.ensemble import IsolationForest
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Fraud ML Service", version="1.0.0")

# ---------------------------------------------------------------------------
# Status encoding
# ---------------------------------------------------------------------------
STATUS_ENCODING = {
    "CAPTURED":   0,
    "AUTHORIZED": 1,
    "FAILED":     2,
    "REFUNDED":   3,
}

# ---------------------------------------------------------------------------
# Train model at startup on synthetic data
# ---------------------------------------------------------------------------
def train_model() -> IsolationForest:
    np.random.seed(42)
    n = 1000

    amounts         = np.random.exponential(scale=50_000, size=n)
    retry_counts    = np.random.choice([0, 1, 2, 3], size=n, p=[0.85, 0.10, 0.03, 0.02])
    time_diffs      = np.random.exponential(scale=30, size=n)
    status_encoded  = np.random.choice([0, 1, 2, 3], size=n, p=[0.70, 0.15, 0.10, 0.05])

    X = np.column_stack([amounts, retry_counts, time_diffs, status_encoded])

    model = IsolationForest(
        n_estimators=100,
        contamination=0.05,
        random_state=42,
    )
    model.fit(X)
    logger.info("IsolationForest model trained on %d synthetic samples", n)
    return model

model: IsolationForest = train_model()

# ---------------------------------------------------------------------------
# Schemas
# ---------------------------------------------------------------------------
class ScoreRequest(BaseModel):
    amount: float
    retry_count: int
    time_diff_seconds: float
    status: str

class ScoreResponse(BaseModel):
    fraud_score: float
    is_anomaly: bool

# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------
@app.post("/score", response_model=ScoreResponse)
def score(request: ScoreRequest) -> ScoreResponse:
    status_code = STATUS_ENCODING.get(request.status.upper(), 1)

    features = np.array([[
        request.amount,
        request.retry_count,
        request.time_diff_seconds,
        status_code,
    ]])

    raw_score: float = float(model.score_samples(features)[0])
    is_anomaly: bool = raw_score < 0.0

    logger.info(
        "Scored: amount=%.2f retry=%d time_diff=%.2f status=%s "
        "→ score=%.4f anomaly=%s",
        request.amount,
        request.retry_count,
        request.time_diff_seconds,
        request.status,
        raw_score,
        is_anomaly,
    )

    return ScoreResponse(fraud_score=raw_score, is_anomaly=is_anomaly)


@app.get("/health")
def health() -> dict:
    return {"status": "UP"}