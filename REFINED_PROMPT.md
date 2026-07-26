# Refined Project Prompt: YouTube Subscriptions & Intelligence Web App

## 1. Project Overview
Develop a modern, performant, and secure full-stack web application for exploring YouTube subscriptions, content (Videos, Shorts, Playlists, Live Streams), streaming media in-browser, and running natural language prompt-based searches across subscriptions using Gemini AI.

---

## 2. Updated Architecture & Technology Requirements

### A. Frontend Specifications
- **Framework & Build**: React + Vite (TypeScript/JavaScript).
- **Styling**: Modern, responsive CSS / Glassmorphic dark mode theme.
- **Testing**: Vitest + React Testing Library (Unit & UI component tests).
- **Media Playback**: Embedded YouTube IFrame Player API.

### B. Backend Specifications
- **Framework**: Java Spring Boot 3.x with **Gradle** build tool.
- **Security & Auth**: Spring Security with OAuth2 Client (`https://www.googleapis.com/auth/youtube.readonly`).
- **API Documentation**: **Swagger UI / OpenAPI 3** (`springdoc-openapi-starter-webmvc-ui`) detailing every endpoint with clear descriptions, schemas, and response codes.
- **Testing**: JUnit 5 + Mockito + Spring Boot Test (Unit & REST integration tests).

### C. Data & Infrastructure
- **Database**: PostgreSQL (Local environment: `localhost:5432/utube_db`).
- **Cloud Migration Target**: Aiven.io Managed PostgreSQL (configured via environment variables / Spring profiles).
- **Cloud Hosting Target**: Render.com (Spring Boot JAR deployment & Vite static site / unified container).

---

## 3. Strict Feature Branch & Git Workflow Protocol

All ongoing development **MUST** adhere to the following lifecycle protocol:

```
[Main / Master Branch]
        │
        ├──► Create Feature Branch (e.g., feature/oauth2-login)
        │           │
        │           ├──► 1. Implement Code & Features
        │           ├──► 2. Run Backend Tests (Gradle test) & Frontend Tests (Vitest)
        │           ├──► 3. Build & Deploy / Run Locally
        │           ├──► 4. Solicit User Verification & Approval
        │           │
        │           └──► 5. Merge Feature Branch into Main / Master
        ▼
[Updated Main Branch] ──► Local / Render.com Deployment
```

---

## 4. Key Functional Capabilities

### A. OAuth 2.0 Authentication
- Log in with Google Account to obtain YouTube Data API read-only authorization token.
- Secure token handling and refresh mechanism.

### B. Channel & Subscription Management
- List all user subscriptions with channel thumbnails, subscriber counts, total video count, and upload frequency.
- Filter and drill down into channel contents:
  - **Videos**: Upload history with view counts and dates.
  - **Shorts**: Vertical/short-form content (< 60s).
  - **Playlists**: Structured tracklists and public playlists.

### C. In-Browser Media Player
- Play videos, Shorts, and playlists directly in the app with queueing and full-screen controls.

### D. AI Prompt Search & Intelligence
- Accept natural language prompts (e.g., *"Find programming tutorials under 20 mins from my coding subscriptions uploaded this month"*).
- Process prompt via Gemini AI to translate into structured parameters (duration, tags, publication date, channels).
- Filter local PostgreSQL cache & execute YouTube API requests.
