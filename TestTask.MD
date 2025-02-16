# **E-commerce Order Processing Application – Test Task**

## **Business Requirements**
The application must process orders from an e-commerce platform and distribute relevant information to two external systems.

### **Order Distribution**
1. **CRM System**
    - Must receive **all order statuses**.

2. **Financial System**
    - Must receive **only** orders with the following statuses:
        - **Paid**
        - **Canceled**

---

## **Technical Requirements**
1. The application must be developed using **Java** with **Spring Integration**.
2. **RabbitMQ** must serve as the entry point for order messages:
    - It should be started and populated with test data **before** the application starts.
    - The queue structure is defined in the provided **AsyncAPI specification** [ecommerce-order-created-asyncapi.json](https://github.com/rkaev/integration-test-task/blob/main/ecommerce-order-created-asyncapi.json).
    - If needed, the **AsyncAPI definition may be converted to YAML**.
    - A sample JSON message file [order_created_messages.json](https://github.com/rkaev/integration-test-task/blob/main/order_created_messages.json) is provided.
3. The system must support **parallel message processing**, ensuring that messages for the **same order** are processed **in the correct sequence** as published in the queue.
4. The **CRM System** requires a mock server:
    - A **simulated service** should be implemented.
    - API specification is provided in **Swagger format** [CRM_Swagger_Definition.yaml](https://github.com/rkaev/integration-test-task/blob/main/CRM_Swagger_Definition.yaml).
5. The **Financial System** expects a **CSV file-based integration**:
    - The application should **append** new orders to an existing file or **create a new file** if none exists.
    - If a file reaches **1000 order items**, a new file should be created.
    - **File Naming Convention:**
      ```
      fin_orders_{datetime:DDMMYYYYHHmm}.csv
      ```
    - **File Structure Example:**  
      Provided in [fin_orders_example.csv](https://github.com/rkaev/integration-test-task/blob/main/fin_orders_example.csv).
    - Files should be stored in separate folder.
6. Implement **unit and integration tests** to validate business logic.
7. Provide a **README.md** with:
    - Instructions for setting up and running the application.
    - Guidelines for executing tests.
8. Publish the **source code** on **GitHub**.
9. *(Optional)* Create an **application architecture diagram** using:
    - **Mermaid.js** or **Lucidchart**.

---

## **Technical Details**
1. The **e-commerce platform** publishes order messages to the **RabbitMQ queue** `orderCreated`, which the application must consume.
2. The **CRM system** provides a **REST API** for integration.
3. The **Financial system** expects **CSV-formatted order files** as input.

---



