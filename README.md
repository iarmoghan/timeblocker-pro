# TimeBlocker Pro

TimeBlocker Pro is a full-stack student study-planning web application. It allows a student to import their timetable from an `.ics` calendar file, add tasks with estimates, priorities and deadlines, then automatically generate a weekly time-blocked study plan around fixed timetable events.

The application also supports replanning when study blocks are completed, skipped, or reset. This makes the schedule more realistic because it can adapt when plans change.

## Project Summary

Many students struggle to organise study time around lectures, labs, part-time work and deadlines. TimeBlocker Pro aims to solve this by combining fixed timetable events with flexible study tasks and generating a structured weekly plan.

The system:
- imports timetable events from `.ics` files,
- stores events, tasks, plans, blocks and preferences in PostgreSQL,
- generates study blocks around fixed events,
- supports replanning,
- marks tasks complete when enough scheduled work has been completed,
- shows unscheduled task details when work cannot fit,
- exports the generated plan back to `.ics`.

## Tech Stack

### Frontend
- React
- TypeScript
- Vite
- CSS / inline component styling

### Backend
- Java 17
- Spring Boot
- Spring Web
- Spring Data JPA
- Bean Validation
- Flyway database migrations

### Database
- PostgreSQL

### Calendar Support
- iCal4j for `.ics` import/export handling

## Main Features

### Dashboard
The dashboard gives a quick overview of the selected day, including:
- open task count,
- study blocks,
- fixed timetable events,
- plan status,
- today timeline,
- next upcoming event or study block.

### Task Management
Users can add tasks with:
- title,
- estimated minutes,
- priority,
- optional deadline.

Tasks can be marked done manually. A task is also automatically marked done when enough linked study block time is completed.

### Timetable Import
Users can upload an `.ics` file containing timetable events. These imported events are treated as fixed busy time by the planner.

### Weekly Plan Generation
The planner generates study blocks around fixed timetable events using:
1. earliest deadline first,
2. highest priority second,
3. oldest created task as a fallback.

### Replanning
When blocks are marked DONE, SKIPPED or reset to PLANNED, the user can replan the week. DONE blocks are kept fixed, and remaining work is scheduled again into available time.

### Unscheduled Task Feedback
If a task cannot fully fit into the available study time, the system shows which task was not fully scheduled and how many minutes remain.

### Export
The weekly study plan can be exported as an `.ics` calendar file.

## How to Run Locally

### Prerequisites
Make sure the following are installed:

- Java 17
- Node.js 20+
- PostgreSQL 14+
- Maven wrapper included in the backend folder

## Database Setup

Create the database if it does not already exist:

```bash
createdb timeblocker
```

If you need to reset demo data without deleting important setup records, use:

```bash
psql -d timeblocker -c "TRUNCATE TABLE blocks, plans, tasks, events, user_preferences RESTART IDENTITY CASCADE;"
```

## Run Backend

From the project root:

```bash
cd backend
./mvnw spring-boot:run
```

Backend runs at:

```bash
http://localhost:8080
```

Health check:

```bash
http://localhost:8080/api/health
```

## Run Frontend

Open a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Frontend runs at:

```bash
http://localhost:5173
```

## Demo Flow

1. Start PostgreSQL.
2. Start the backend.
3. Start the frontend.
4. Open the web app.
5. Go to Planner.
6. Import a demo .ics timetable.
7. Go to Tasks and add tasks.
8. Go to Settings and set:
   - Day start: 9
   - Day end: 18
   - Block minutes: 60
9. Go to Planner and generate a weekly plan.
10. Go to Dashboard and check the timeline.
11. Mark a study block as DONE.
12. Replan the week.
13. Export the weekly plan as .ics.

## Testing Checklist

Manual testing was carried out for the main workflow:

-Import .ics timetable
-Add tasks
-Save preferences
-Generate weekly plan
-Confirm planner avoids fixed timetable events
-Mark blocks DONE
-Confirm task becomes DONE after enough completed block time
-Reset block status
-Confirm task can become OPEN again
-Replan week
-Confirm DONE blocks remain fixed
-Confirm unscheduled task details appear when the week is overloaded
-Export weekly plan as .ics

## Known Limitations
The app currently uses a demo user rather than full account/login support.
The planner uses a rule-based scheduling approach rather than machine learning.
Recurring calendar event handling is limited to what is parsed/imported.
The frontend is designed for desktop/laptop use first.
The system is intended as an academic prototype, not a production commercial system.

## Future Work

Possible future improvements include:

-user accounts and authentication,
-drag-and-drop rescheduling,
-stronger recurring event support,
-calendar sync with Google Calendar,
-more advanced optimisation for deadlines and workload balance,
-automated test suite expansion,
-mobile responsive layout improvements.

