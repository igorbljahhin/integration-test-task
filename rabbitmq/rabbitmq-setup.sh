#!/bin/bash

echo "Waiting for RabbitMQ to start..."
sleep 20

echo "Setting up RabbitMQ based on AsyncAPI schema..."

rabbitmqadmin declare exchange name=orderCreated-exchange type=direct
rabbitmqadmin declare queue name=orderCreated-queue durable=true
rabbitmqadmin declare binding source=orderCreated-exchange destination=orderCreated-queue routing_key=orderCreated

echo "RabbitMQ setup based on AsyncAPI is completed."

echo "Publishing initial messages..."

jq -c '.orders[]' /rabbitmq-messages/order_created_messages.json | while read order; do
    rabbitmqadmin publish exchange=orderCreated-exchange routing_key=orderCreated payload="$order"
done

echo "RabbitMQ setup and message publishing completed."