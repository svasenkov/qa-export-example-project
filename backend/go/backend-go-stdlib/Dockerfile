FROM golang:1.27-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/backend-go-stdlib .

FROM alpine:3.21
RUN apk add --no-cache ca-certificates wget \
    && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /out/backend-go-stdlib ./backend-go-stdlib
USER app
ENV SERVER_PORT=8080
ENV MANAGEMENT_PORT=8081
EXPOSE 8080 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${SERVER_PORT}/api/health" >/dev/null || exit 1
CMD ["./backend-go-stdlib"]
