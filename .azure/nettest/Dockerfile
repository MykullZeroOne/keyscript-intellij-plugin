FROM python:3.12-alpine
RUN apk add --no-cache curl bind-tools iputils traceroute netcat-openbsd bash iproute2
COPY app.py /app.py
EXPOSE 80
CMD ["python3", "/app.py"]
