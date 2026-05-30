# ZipRun AI Reassignment Engine

An intelligent order reassignment system that automatically detects when delivery agents go offline and uses AI to recommend optimal order reassignments to operations staff.

## Features

- **Agentic Re-planning Loop**: Automatically detects agent offline events and generates reassignment suggestions
- **AI-Powered Recommendations**: Uses OpenAI GPT-4o to analyze order context and agent availability
- **Real-time Streaming**: SSE-based streaming shows AI reasoning token-by-token
- **Human-in-the-Loop**: Suggestions require ops approval before assignment changes
- **Pluggable Routing**: Switchable between rule-based and AI strategies at runtime

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 3.5, Java 17, Spring Data JPA |
| Frontend | React 18, Vite, TypeScript, shadcn/ui |
| Database | H2 (in-memory) |
| Cache/Streaming | Redis |
| AI | OpenAI GPT-4o |

## Quick Start (< 5 minutes)

### Prerequisites

- Java 17+
- Node.js 18+
- Redis (for SSE streaming)
- OpenAI API Key

### 1. Start Redis

```bash
# macOS
brew install redis && redis-server

# Docker
docker run -d -p 6379:6379 redis:alpine

# Or use the included docker-compose
cd backend && docker-compose up -d
```

### 2. Start Backend

```bash
cd backend

# Set your OpenAI API key
export LLM_API_KEY=your-openai-api-key

# Run with Maven wrapper
./mvnw spring-boot:run
```

Backend starts at: **http://localhost:8080**

H2 Console (for debugging): **http://localhost:8080/h2-console**
- JDBC URL: `jdbc:h2:mem:ziprun`
- Username: `sa`
- Password: (empty)

### 3. Start Frontend

```bash
cd frontend

# Install dependencies
npm install

# Start dev server
npm run dev
```

Frontend starts at: **http://localhost:5173**

## Demo Flow (Re-plan Path)

1. Open the dashboard at http://localhost:5173
2. View the **Agents** tab - notice agents with BUSY/AVAILABLE status
3. Click **Set Offline** on an agent with assigned orders (e.g., AGT-001)
4. Watch the agentic loop trigger:
   - Orders move to REASSIGNMENT_PENDING status
   - AI generates suggestions with **Re-plan** badge
   - Reasoning appears showing AI's decision process
5. **Accept** or **Reject** the suggestion
   - Accept: Order gets reassigned to recommended agent
   - Reject: Provide feedback, new suggestion generates in 5 seconds

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/orders` | List all orders (filterable by status) |
| POST | `/api/orders` | Create new order |
| GET | `/api/agents` | List all agents |
| PATCH | `/api/agents/{id}/status` | Update agent status (triggers re-plan if OFFLINE) |
| POST | `/api/orders/{id}/suggest` | Request AI suggestion for an order |
| GET | `/api/suggestions/{id}/stream` | SSE stream for real-time AI reasoning |
| PATCH | `/api/suggestions/{id}` | Accept or reject a suggestion |

## Configuration

Key settings in `backend/src/main/resources/application.properties`:

```properties
# LLM Provider (openai, gemini, groq, ollama)
llm.provider=openai
llm.api-key=${LLM_API_KEY}
llm.model=gpt-4o

# Routing Strategy (rule-based or ai)
routing.strategy=ai

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

## Project Structure

```
zyucs/
├── backend/
│   └── src/main/java/com/ziprun/reassignment/
│       ├── controller/     # REST endpoints
│       ├── service/        # Business logic
│       ├── domain/         # Entities & enums
│       ├── routing/        # Strategy pattern implementation
│       ├── ai/             # LLM integration
│       └── event/          # Async event handling
├── frontend/
│   └── src/
│       ├── components/     # React components
│       ├── api/            # API client
│       └── types/          # TypeScript types
├── ADR.md                  # Architecture Decision Records
└── README.md
```

## Architecture Highlights

- **Strategy Pattern**: Routing strategies are pluggable via Spring bean map
- **Event-Driven**: Agent offline triggers async re-planning via `@EventListener`
- **Fallback Design**: AI failures gracefully fall back to rule-based strategy
- **Idempotent**: Duplicate offline events don't create duplicate suggestions

See [ADR.md](./ADR.md) for detailed architecture decisions.

## Seed Data

The app starts with pre-loaded data:
- 5 agents (mix of AVAILABLE, BUSY)
- 8 orders assigned to agents

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Redis connection refused | Ensure Redis is running on port 6379 |
| AI suggestions not working | Check LLM_API_KEY is set correctly |
| CORS errors | Backend must be on 8080, frontend on 5173 |
| No orders showing | Check H2 console for data, restart backend |

## License

MIT
