# InvestSage Spring Batch

Backend for InvestSage AI Chatbot - A Spring Boot application with batch processing capabilities for investment data embedding and RAG (Retrieval-Augmented Generation) functionality.

## Overview

InvestSage is an AI-powered investment advisory chatbot that uses Spring Batch for processing investment data, generating embeddings, and providing intelligent investment advice through RAG.

## Features

- **Spring Batch Processing**: Efficient batch processing for large datasets
- **OpenAI Integration**: Uses OpenAI for embeddings and chat functionality
- **PostgreSQL with pgvector**: Vector database for similarity search
- **RAG Implementation**: Retrieval-Augmented Generation for investment advice
- **RESTful API**: Controllers for chat and investment queries

## Technology Stack

- **Java 17**
- **Spring Boot 3.4.4**
- **Spring Batch 5.2.2**
- **Spring AI 1.0.0-M6**
- **PostgreSQL** with **pgvector** extension
- **Maven** for dependency management

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- PostgreSQL 12+ with pgvector extension
- OpenAI API key

## Setup

1. Clone the repository:
```bash
git clone https://github.com/shahdharmil11/investsage-spring-batch.git
cd investsage-spring-batch
```

2. Configure your database connection in `src/main/resources/application.properties`

3. Set your OpenAI API key in the application configuration

4. Build the project:
```bash
./mvnw clean install
```

5. Run the application:
```bash
./mvnw spring-boot:run
```

## Project Structure

```
src/
├── main/
│   ├── java/com/dharmil/investsage/
│   │   ├── batch/          # Batch processing components
│   │   ├── config/          # Configuration classes
│   │   ├── controller/      # REST controllers
│   │   ├── dto/             # Data transfer objects
│   │   ├── runner/          # Batch job runners
│   │   ├── service/         # Business logic services
│   │   └── util/            # Utility classes
│   └── resources/
│       ├── application.properties
│       └── db/              # Database scripts
└── test/                    # Test classes
```

## License

This project is private and proprietary.

