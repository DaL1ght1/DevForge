FROM python:3.12-slim
RUN pip install poetry
WORKDIR /app
COPY pyproject.toml poetry.lock* ./
RUN poetry install --no-interaction --no-root
COPY . .
CMD ["poetry","run","uvicorn","app.main:app","--host","0.0.0.0","--port","8080"]
