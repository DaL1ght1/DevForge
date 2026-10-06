FROM rust:1.81 AS build
WORKDIR /app
COPY . .
RUN cargo build --release
FROM debian:bookworm-slim
COPY --from=build /app/target/release/{{SERVICE_NAME}} /usr/local/bin/app
CMD ["app"]
