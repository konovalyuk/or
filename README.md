# Operations Research Lab

Interactive educational application for transportation problems and the MODI (transportation simplex) method. It is a Spring Boot API with a React/Vite single-page application, packaged as one executable JAR and one OCI-compatible Docker image.

## Prerequisites

- Java 25
- Node.js 20+
- Docker (for containerized execution)

## Local development

Run the backend in one terminal:

```bash
./gradlew bootRun
```

Run the frontend with live reload in another:

```bash
cd frontend
npm ci
npm run dev
```

Open http://localhost:5173. The Vite development server proxies `/api` requests to Spring Boot on port 8080.

## Production-like JAR

```bash
./gradlew bootJar
java -jar build/libs/or-0.0.1-SNAPSHOT.jar
```

The frontend is built and embedded into the JAR. Open http://localhost:8080. The readiness endpoint is http://localhost:8080/actuator/health.

## Docker

```bash
docker build -t operations-research-lab .
docker run --rm -p 8080:8080 operations-research-lab
```

The image accepts `PORT` and defaults to `8080`, so it can run on Render, Railway, Fly.io, Cloud Run, Azure Container Apps, AWS ECS/Fargate, or any OCI-compatible service.

## GitHub and deployment

Create a private empty GitHub repository, then add it as `origin` and push `main`:

```bash
git remote add origin https://github.com/<account>/<repository>.git
git push -u origin main
```

For Render, create a new **Web Service**, connect the repository, choose **Docker**, and expose port `8080`. Other cloud providers only need the Dockerfile and a public HTTP route to the application port. Do not commit cloud tokens or `.env` files; configure secrets in the provider dashboard.
