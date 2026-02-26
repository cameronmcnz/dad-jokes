# Phoenix Arise Chat UI

This folder contains the static landing page for the Phoenix Arise Foundation RAG chatbot.

## How It Works
- `index.html` calls the backend endpoint `/ask?cid=...&question=...`.
- A unique `cid` is generated once and stored in localStorage.
- Question/answer history is stored in localStorage and restored on reload.

## Quick Try (with Spring Boot running)
Open `http://localhost:8080/` in a browser.
