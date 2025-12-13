# TimeBlocker Pro

A student-focused planner that imports timetable/deadlines (.ics) and generates a weekly time-block schedule with automatic re-planning.

## Run locally

### Prerequisites
- Java 17
- Node 20+
- PostgreSQL 14 running locally

### Backend
```bash
cd backend
./mvnw spring-boot:run
```
Health check: http://localhost:8080/api/health

### Frontend
```bash
cd frontend
npm install
npm run dev
```
App: http://localhost:5173


