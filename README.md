# E-commerce Order Processing Application

This application processes orders from an e-commerce platform and distributes them to CRM and Financial systems.

## Prerequisites

- Java 21 or higher
- Maven 3.8+
- Docker and Docker Compose
- Git

## Setup Instructions

### 1. Clone the Repository
```bash
git clone https://github.com/igorbljahhin/integration-test-task.git
cd integration-test-task
```

### 2. Configure the Application

The application uses the following configuration files:
- `core/src/main/resources/application-dev.yml` - Development configuration
- `core/src/main/resources/application-prod.yml` - Production configuration (create if needed)

Default configuration values can be overridden using environment variables:
```bash
export APP_CRM_API_URL=http://localhost:4010
export APP_FINANCIAL_OUTPUT_DIRECTORY=./financial-output
```

### 3. Start Dependencies

Start RabbitMQ and Mock CRM service using Docker Compose:
```bash
docker-compose up -d
```

Verify services are running:
```bash
docker-compose ps
```

The following services should be available:
- RabbitMQ Management Console: http://localhost:15672 (guest/guest)
- Mock CRM API: http://localhost:4010

### 4. Build the Application

```bash
mvn clean package
```

### 5. Run the Application

Development mode:
```bash
java -jar core/target/integration-test-task.jar
```

Production mode:
```bash
java -jar core/target/integration-test-task.jar --spring.profiles.active=prod
```

## Testing

### Unit Tests

Run unit tests:
```bash
mvn test
```

### Integration Tests

1. Ensure Docker services are running:
```bash
docker-compose up -d
```

2. Run integration tests:
```bash
mvn verify -P integration-test
```

### Manual Testing

1. Access RabbitMQ Management Console:
   - URL: http://localhost:15672
   - Username: guest
   - Password: guest
   - Navigate to "Queues" tab to verify `orderCreated-queue` exists

2. Send test message to RabbitMQ:
```bash
curl -X POST \
  http://localhost:15672/api/exchanges/%2F/amq.default/publish \
  -u guest:guest \
  -H 'Content-Type: application/json' \
  -d '{
    "properties": {},
    "routing_key": "orderCreated-queue",
    "payload": "{\"orderId\":\"ORD123\",\"status\":\"paid\",\"customerId\":\"CUST456\"}",
    "payload_encoding": "string"
  }'
```

3. Verify output:
   - Check CRM mock service logs:
     ```bash
     docker-compose logs -f mock-crm
     ```
   - Check financial output directory for CSV files:
     ```bash
     ls -l financial-output/
     ```

## Troubleshooting

### Common Issues

1. Port Conflicts
   - RabbitMQ ports (5672, 15672) already in use
   - Mock CRM port (4010) already in use
   
   Solution: Stop conflicting services or modify ports in docker-compose.yml

2. Permission Issues
   - Financial output directory not writable
   
   Solution: Check directory permissions:
   ```bash
   chmod 755 financial-output/
   ```

3. RabbitMQ Connection Issues
   
   Solution: Verify RabbitMQ is running and credentials are correct:
   ```bash
   docker-compose ps rabbitmq
   docker-compose logs rabbitmq
   ```

### Logs

Application logs are written to:

- Console (default)

To enable debug logging, modify `application-dev.yml`:

```yaml
logging:
  level:
    com.example.ecommerceorderprocessor: DEBUG
    org.springframework.integration: DEBUG
```

## Monitoring

### Health Check

Access the health endpoint (if Spring Actuator is enabled):
```bash
curl http://localhost:8080/actuator/health
```
