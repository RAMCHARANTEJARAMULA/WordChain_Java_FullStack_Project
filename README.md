# WordChain - Java Full Stack Multiplayer Word Game

A beginner-friendly college project: React + CSS frontend, Java Spring Boot REST API, Spring Data JPA, and MySQL/Aiven support.

## Features
- Create a room and receive a referral code
- Join a room with the code and a player name
- Up to 4 players per room
- Take turns entering a word beginning with the last letter of the previous word
- Prevent duplicate words and reject invalid turns
- Score, turn order, word history, room status
- CRUD REST endpoints for rooms and words
- H2 local database by default; switch to Aiven MySQL with environment variables

## Requirements
- Java 17+
- Maven 3.9+
- Node.js 18+
- Git
- VS Code (Java Extension Pack and ES7 React snippets optional)

## Run backend
```bash
cd backend
mvn spring-boot:run
```
API: http://localhost:8080
Health: http://localhost:8080/api/health

Local development uses H2 in-memory database. H2 console: http://localhost:8080/h2-console
JDBC URL: `jdbc:h2:mem:wordchaindb`, username `sa`, password blank.

## Run frontend
Open a second terminal:
```bash
cd frontend
npm install
npm run dev
```
Open the URL Vite prints (usually http://localhost:5173). Frontend calls backend at `http://localhost:8080`.

## Use Aiven MySQL
Create a MySQL service in Aiven and allow connections from your backend host. Set these environment variables for backend:
- `SPRING_DATASOURCE_URL=jdbc:mysql://YOUR_HOST:YOUR_PORT/defaultdb?sslMode=REQUIRED`
- `SPRING_DATASOURCE_USERNAME=YOUR_USERNAME`
- `SPRING_DATASOURCE_PASSWORD=YOUR_PASSWORD`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=update`
Never commit credentials to GitHub. If Aiven provides a CA certificate or different connection URL, follow the connection details shown in your Aiven console.

## Deploy backend on Render
1. Push the `backend` folder to a GitHub repository (or push the project and set Root Directory to `backend`).
2. Create a Render Web Service from that repository.
3. Runtime: Java; Build command: `mvn clean package -DskipTests`
4. Start command: `java -Dserver.port=$PORT -jar target/wordchain-1.0.0.jar`
5. Add Aiven environment variables above. Set `SPRING_PROFILES_ACTIVE=prod` if desired.
6. After deployment, test `https://YOUR-BACKEND.onrender.com/api/health`.

## Deploy frontend on Vercel or Netlify
Push `frontend` to a separate GitHub repository.
- Build command: `npm run build`
- Output directory: `dist`
- Add environment variable `VITE_API_URL=https://YOUR-BACKEND.onrender.com/api`
- Redeploy after setting the variable.
For Netlify, a `frontend/netlify.toml` is included. For Vercel, use the Vite preset.
Do not use the local API URL in production.

## GitHub links
Create two repositories:
1. `wordchain-frontend` — upload contents of `frontend/`
2. `wordchain-backend` — upload contents of `backend/`
You must sign in and push/deploy yourself; this ZIP cannot create accounts or publish public links automatically.

## API overview
- `GET /api/health`
- `POST /api/rooms` `{ "playerName": "Alex" }` -> creates room and host joins
- `POST /api/rooms/{code}/join` `{ "playerName": "Sam" }`
- `GET /api/rooms/{code}`
- `POST /api/rooms/{code}/words` `{ "playerName": "Alex", "word": "apple" }`
- `GET /api/rooms/{code}/words`
- `DELETE /api/rooms/{code}`

## Important limitation
This starter uses polling in the frontend (refreshes room data every few seconds), not WebSockets. It is suitable for a quick demo with players on the same live backend. Word validation checks the chain rule and duplicates; it does not verify words against a dictionary.
