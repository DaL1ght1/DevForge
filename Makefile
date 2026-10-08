.PHONY: run backend-up backend-down frontend-install frontend-dev check

run: backend-up frontend-install
	@echo "Starting frontend..."
	cd frontend && npm run dev

backend-up:
	cd backend && docker compose up --build -d

backend-down:
	cd backend && docker compose down

frontend-install:
	cd frontend && npm install

frontend-dev:
	cd frontend && npm run dev

check:
	cd backend/provisioner && go test ./...
	cd frontend && npm run lint && npm run build