FROM python:3.12-slim
RUN pip install poetry
WORKDIR /app
COPY . .
RUN poetry install --no-interaction
CMD ["poetry","run","python","manage.py","runserver","0.0.0.0:8080"]
