# 🤖 A2A Multi-Agent Fintech System

![Java](https://img.shields.io/badge/Java-17+-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Gemini](https://img.shields.io/badge/Gemini-Google_ADK-8E75FF?style=for-the-badge&logo=google&logoColor=white)
![A2A](https://img.shields.io/badge/A2A-Protocol_v1.1-00BCD4?style=for-the-badge&logo=googlechrome&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791?style=for-the-badge&logo=postgresql&logoColor=white)
![Langfuse](https://img.shields.io/badge/Observability-Langfuse-FF6B35?style=for-the-badge)

> **A production-grade multi-agent AI system for fintech, built on Google's A2A Protocol with full context engineering, LLM observability, and real-time monitoring.**

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        User / UI (React)                    │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│              Orchestrator Agent (A2A Protocol)              │
│         LoopAgent · Streaming v1.1.0 · Resilience4j        │
└──────┬──────────────┬────────────────┬──────────────┬───────┘
       │              │                │              │
       ▼              ▼                ▼              ▼
  ┌─────────┐   ┌──────────┐   ┌──────────┐   ┌──────────────┐
  │  Auth   │   │ Balance  │   │ Transfer │   │    Memory    │
  │  Agent  │   │  Agent   │   │  Agent   │   │    Agent     │
  │Keycloak │   │PostgreSQL│   │   CDC    │   │  pgvector    │
  │  JWT    │   │  Redis   │   │  Fraud   │   │ embeddings   │
  └─────────┘   └──────────┘   └──────────┘   └──────────────┘
       │              │                │              │
       └──────────────┴────────────────┴──────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
  │   Context    │ │     LLM      │ │  Monitoring  │
  │ Engineering  │ │Observability │ │              │
  │              │ │              │ │ Prometheus   │
  │Session State │ │  Langfuse    │ │  Grafana     │
  │Long-term Mem │ │LLM-as-Judge  │ │  Structured  │
  │pgvector      │ │Golden Dataset│ │  Logging     │
  │embedding-004 │ │Prompt Mgmt   │ │  Dynamic     │
  └──────────────┘ └──────────────┘ │  Sampling    │
                                     └──────────────┘
```

---

## ⚡ Core Features

### 🤖 Multi-Agent System (A2A Protocol)
- **Orchestrator Agent** — routes tasks across specialized agents using Google's A2A Protocol
- **Auth Agent** — handles authentication and authorization (Keycloak + JWT)
- **Balance Agent** — manages account balances (PostgreSQL + Redis)
- **Transfer Agent** — processes cross-border transfers with real-time CDC-based fraud detection
- **Memory Agent** — provides long-term memory and context retrieval via pgvector

### 🔄 Agent Communication
- **A2A Protocol v1.1** — Google's Agent-to-Agent communication standard
- **Streaming (v1.1.0)** — real-time streaming responses between agents
- **Iterative Refinement** — LoopAgent for self-correcting agent loops
- **Resilience4j** — circuit breaker, retry, and rate limiting for fault tolerance

### 🧠 Context Engineering
- **Session State** — stateful conversation management per user session
- **Long-term Memory** — persistent memory agent for cross-session context
- **PostgreSQL + pgvector** — vector storage for semantic search and retrieval
- **text-embedding-004** — Google's embedding model for context vectorization

### 🔬 LLM Observability (Langfuse)
- **Async LLM-as-a-Judge** — automated quality evaluation of agent responses
- **Structured Logging** — full trace of every agent interaction
- **Golden Dataset** — curated test cases for regression testing
- **Dataset Import** — automated dataset ingestion pipeline
- **Prompt Management** — versioned prompt templates with A/B testing

### 📊 Infrastructure Monitoring
- **Prometheus** — metrics collection across all agents
- **Grafana** — real-time dashboards for agent performance
- **Dynamic Sampling** — adaptive trace sampling under high load
- **Structured Logging** — JSON logs with correlation IDs

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17+ |
| Framework | Spring Boot 3.x |
| AI / LLM | Google Gemini via Google ADK |
| Agent Protocol | Google A2A Protocol v1.1 |
| Vector DB | PostgreSQL + pgvector |
| Embeddings | text-embedding-004 |
| Cache | Redis |
| Messaging | Apache Kafka |
| Resilience | Resilience4j |
| Auth | Keycloak |
| Observability | Langfuse |
| Monitoring | Prometheus + Grafana |
| UI | React |
| Containers | Docker + Kubernetes |

---

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Docker & Docker Compose
- Google ADK credentials
- PostgreSQL with pgvector extension
- Keycloak instance

### Run locally

```bash
# Clone the repository
git clone https://github.com/arshamsedaghatbin/a2a-multi-agent.git
cd a2a-multi-agent

# Start infrastructure
docker-compose up -d

# Run the application
./mvnw spring-boot:run
```

### Environment Variables

```env
GEMINI_API_KEY=your_gemini_api_key
KEYCLOAK_URL=http://localhost:8080
POSTGRES_URL=jdbc:postgresql://localhost:5432/agentdb
REDIS_URL=redis://localhost:6379
LANGFUSE_SECRET_KEY=your_langfuse_key
LANGFUSE_PUBLIC_KEY=your_langfuse_public_key
```

---

## 📁 Project Structure

```
src/
├── agents/
│   ├── orchestrator/      # Main orchestrator agent
│   ├── auth/              # Authentication agent
│   ├── balance/           # Balance management agent
│   ├── transfer/          # Transfer & fraud detection agent
│   └── memory/            # Long-term memory agent
├── a2a/
│   ├── protocol/          # A2A protocol implementation
│   └── streaming/         # Streaming v1.1.0 handlers
├── context/
│   ├── session/           # Session state management
│   ├── memory/            # pgvector memory store
│   └── embedding/         # text-embedding-004 integration
├── observability/
│   ├── langfuse/          # LLM tracing & evaluation
│   ├── logging/           # Structured logging
│   └── metrics/           # Prometheus metrics
└── monitoring/
    ├── prometheus/        # Metrics configuration
    └── grafana/           # Dashboard configs
```

---

## 🗺️ Roadmap

- [x] Core multi-agent system (A2A Protocol)
- [x] Streaming v1.1.0
- [x] Context engineering (session + long-term memory)
- [x] LLM observability (Langfuse + LLM-as-a-Judge)
- [x] Monitoring (Prometheus + Grafana)
- [ ] Telegram & WhatsApp channels
- [ ] RAG (bank documents)
- [ ] MCP Fraud Detection & Notification
- [ ] Parallel & Ambient Agents
- [ ] HITL (Human-in-the-Loop)
- [ ] LLM Guard & PII Redaction
- [ ] Kubernetes choreography pattern

---

## 📄 License

MIT License — feel free to use and contribute.

---

*Built with Google ADK · A2A Protocol · Spring Boot*
