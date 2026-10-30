FROM golang:1.26-alpine AS build

WORKDIR /build

COPY provisioner/go.mod provisioner/go.sum ./provisioner/
WORKDIR /build/provisioner

RUN go mod download

COPY provisioner/ ./

RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -trimpath \
    -ldflags="-s -w" \
    -o /out/provisioner ./cmd/server

FROM alpine:3.22 AS runtime

WORKDIR /app

RUN apk add --no-cache ca-certificates \
    && addgroup -S devforge \
    && adduser -S -G devforge devforge \
    && chown -R devforge:devforge /app

COPY --from=build /out/provisioner /app/provisioner

ENV PORT=8081

USER devforge

EXPOSE 8081

ENTRYPOINT ["/app/provisioner"]
