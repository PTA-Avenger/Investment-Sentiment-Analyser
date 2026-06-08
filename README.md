# JSE Investment Sentiment Analyser

A full-stack Natural Language Processing (NLP) pipeline designed for Johannesburg Stock Exchange (JSE) investors. The system aggregates real-time South African financial news, classifies articles by market sector, evaluates headline sentiment using Stanford CoreNLP, and visualizes live sentiment trends on an interactive Angular dashboard.

---

## 1. Features & Architectural Patterns

### 1.1 Pipeline Pattern
The backend is structured around a modular pipeline data flow consisting of 5 distinct processing stages:
1.  **Ingestion**: Scrapes RSS feeds from **Moneyweb** and **BusinessTech** in real-time. Automatically falls back to high-quality default seed data on startup or network failures.
2.  **Cleaning**: Normalizes text by combining titles/descriptions, stripping HTML tags, and decoding character entities.
3.  **Sector Classification**: Evaluates JSE company names and market terms to map articles to JSE sectors (*Mining*, *Financials*, *Retail*, *Technology & Telecoms*, *Energy & Industrials*, or *General Business*).
4.  **Sentiment Analysis**: Scores text using a deep linguistic constituency parser model.
5.  **Storage & Broadcasting**: Persists unique records in a rolling 72-hour window in H2 database, prunes older records, and broadcasts updates over Server-Sent Events (SSE).

### 1.2 Graceful Degradation (Regex Fallback Engine)
Stanford CoreNLP constituency parsing is CPU and memory intensive (~2-3 GB of RAM). To ensure high availability and responsiveness under resource constraints (e.g. Render's 512MB free tier) or high load:
-   The CoreNLP engine loads **asynchronously** in the background on startup, allowing the Spring application to boot instantly.
-   A timeout boundary of **1500ms** is enforced on NLP tasks.
-   If CoreNLP is still loading, disabled, or times out, the system routes the request to a pre-compiled **Regex Fallback Engine** that performs case-insensitive word matching in `< 1ms`.

### 1.3 Zero-Rebuild Frontend Deployments
To simplify deployments on platforms like Vercel and Render:
-   The Angular frontend connects to the backend dynamically.
-   Users can input their production Render backend URL directly in the **API Server Settings** panel. The URL is saved in `localStorage`, eliminating the need to rebuild the Angular bundle when backend endpoints change.

---

## 2. Tech Stack

-   **Backend**: Java 17, Spring Boot 3, Hibernate JPA, H2 Database (In-Memory).
-   **NLP Engine**: Stanford CoreNLP 4.5.4, Rome RSS Parser 2.1.0.
-   **Frontend**: Angular 19 (Standalone), Vanilla CSS (Glassmorphism design system).
-   **Automation**: PowerShell build script, multi-stage Dockerfile, Vercel routing configs.

---

## 3. Local Setup & Execution

### Prerequisites
-   **Java Development Kit (JDK) 17**
-   **Node.js** (v18+) and **npm**

### Quick Start
1.  Clone this repository to your local machine.
2.  Open PowerShell in the project root folder.
3.  Run the automation script:
    ```powershell
    powershell -File run.ps1
    ```
    This script will:
    -   Configure the Maven environment.
    -   Compile the Angular frontend and transfer the built static files to the backend resources.
    -   Compile and package the Spring Boot backend JAR.
    -   Launch the server on port **8081** with a 3GB heap flag (`-Xmx3g`) to accommodate the Stanford CoreNLP models.
4.  Open your browser and navigate to:
    `http://localhost:8081`

---

## 4. REST API Documentation

All endpoints are exposed under `/api/sentiment` and support CORS.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/sentiment/sectors` | Retrieves positive, neutral, negative counts and weighted averages for all JSE sectors in the rolling 72h window. |
| `GET` | `/api/sentiment/headlines` | Returns the list of parsed headlines sorted by published date. |
| `GET` | `/api/sentiment/status` | Returns system operational stats (NLP state, latencies, requests, and pipeline log streams). |
| `POST` | `/api/sentiment/analyze` | Benchmark playground. Submits custom text and returns a side-by-side comparison of CoreNLP vs Regex engine results. |
| `POST` | `/api/sentiment/toggle` | Enables or disables the CoreNLP parser, forcing the Regex fallback. |
| `POST` | `/api/sentiment/ingest` | Manually triggers the RSS news ingestion pipeline in a background thread. |
| `POST` | `/api/sentiment/seed` | Purges the database and reloads mock seed JSE headlines. |
| `GET` | `/api/sentiment/stream` | Server-Sent Events (SSE) event stream endpoint for real-time update notifications. |

### Sample Benchmark Request (`POST /api/sentiment/analyze`)
**Payload:**
```json
{
  "text": "Capitec bank earnings soar by 25% following customer growth"
}
```
**Response:**
```json
{
  "text": "Capitec bank earnings soar by 25% following customer growth",
  "serviceResult": {
    "sentiment": "POSITIVE",
    "score": 0.5,
    "engineUsed": "CORENLP",
    "latencyMs": 123
  },
  "regexResult": {
    "sentiment": "POSITIVE",
    "score": 1.0,
    "engineUsed": "REGEX_FALLBACK",
    "latencyMs": 1
  }
}
```

---

## 5. Hosting & Cloud Deployments

### 5.1 Backend Deployment (Render - Free Tier)
1.  Create a web service on **Render** (`https://render.com`).
2.  Connect your repository and set the **Root Directory** to `backend`.
3.  Choose environment **Docker**. Render will build the image using the multi-stage [Dockerfile](file:///c:/InvestmentSentimentAnalyser/backend/Dockerfile).
4.  Once deployed, copy your Render web service URL (e.g., `https://your-app.onrender.com`).

*Note: Since Render's free tier has a 512MB RAM ceiling, Stanford CoreNLP will automatically fail to allocate its heap on startup and trigger the fallback state. The backend will gracefully run entirely on the Regex fallback engine without crashing.*

### 5.2 Frontend Deployment (Vercel - Free Tier)
1.  Import your repository into **Vercel** (`https://vercel.com`).
2.  Set the **Root Directory** to `frontend`.
3.  Select the **Angular** framework preset (Vercel will build via `npm run build` and serve from `dist/frontend/browser`).
4.  Once deployed, open your Vercel site, click the settings cog on the top right, and paste your Render backend URL to connect.
