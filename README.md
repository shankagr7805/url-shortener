# ⚡ URL Shortener — Full Stack (Spring Boot + React + Redis)

A production-grade URL shortener built with Spring Boot, Redis, MySQL, and React.

## Architecture

```
React (port 3000)
      │
      ▼
Spring Boot (port 8083)
      │
      ├── Redis (port 6382)  ← O(1) URL lookup cache + Rate limiting
      └── MySQL (port 3309)  ← Persistent URL storage
```

## Features

- **Base62 encoding** — unique short codes (supports 100M+ URLs)
- **Redis O(1) lookup** — sub-5ms redirect response time
- **Distributed rate limiting** — 100 req/min per IP via Redis
- **URL expiry** — TTL-based auto-deactivation with scheduler
- **Click analytics** — per-URL click tracking with async processing
- **Custom aliases** — user-defined short codes
- **Pagination** — efficient URL listing with indexed queries
- **Unit tests** — JUnit 5 + Mockito for service layer

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.2, Spring Web, Spring Data JPA |
| Cache | Redis (Lettuce client, connection pool) |
| Database | MySQL 8 with HikariCP connection pool |
| Frontend | React 18, Axios |
| Testing | JUnit 5, Mockito |
| DevOps | Docker, GitHub Actions |

---

## Running Locally

### Prerequisites
- Java 21
- Node.js 18+
- Docker Desktop

### Step 1 — Start Docker containers
```bash
cd url-shortener-backend
docker compose up -d
docker ps   # confirm MySQL on 3309, Redis on 6382
```

### Step 2 — Start backend
```bash
cd url-shortener-backend
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```
App starts on http://localhost:8083

### Step 3 — Start frontend
```bash
cd url-shortener-frontend
npm install
npm start
```
Frontend starts on http://localhost:3000

### Step 4 — Test the API
```bash
# Health check
curl http://localhost:8083/api/v1/health

# Shorten a URL
curl -X POST http://localhost:8083/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://www.google.com"}'

# Redirect (opens in browser)
curl -L http://localhost:8083/{shortCode}

# Analytics
curl http://localhost:8083/api/v1/analytics/{shortCode}
```

---

## Running Tests
```bash
cd url-shortener-backend
./mvnw test
```

Test coverage:
- `Base62EncoderTest` — 6 tests covering encoding/decoding correctness
- `UrlServiceTest` — 8 tests covering cache hit/miss, CRUD, error handling

---

## Load Testing with JMeter

```bash
# Install JMeter
brew install jmeter

# Run load test (1000 concurrent users)
jmeter -n -t load-test/url_shortener_test.jmx \
  -Jthreads=1000 -Jramp=30 -Jduration=60 \
  -l results.jtl

# Get metrics
awk -F',' 'NR>1 && $3=="Redirect" && $8=="true" {sum+=$2; count++} \
  END {printf "Avg: %dms | Requests: %d\n", sum/count, count}' results.jtl
```

---

## API Reference

| Method | Endpoint | Description |
|---|---|---|
| POST | /api/v1/shorten | Create short URL |
| GET | /{shortCode} | Redirect to original URL |
| GET | /api/v1/urls | List all URLs (paginated) |
| GET | /api/v1/analytics/{code} | Get click analytics |
| DELETE | /api/v1/urls/{code} | Deactivate a URL |
| GET | /api/v1/health | Health check |
