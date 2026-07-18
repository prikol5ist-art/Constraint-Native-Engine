# Contributing to Constraint Native Engine

Thank you for your interest in contributing to the Constraint Native Engine! This guide outlines our processes and standards to help you get started.

## Code of Conduct

We aim to foster an open, welcoming, and professional community. Please treat all contributors with respect.

## Getting Started

1. **Fork the Repository**: Create a fork of the repository on GitHub.
2. **Clone Locally**: Clone your fork to your local machine.
3. **Set Up Java**: Ensure you have JDK 17 installed.
4. **Build and Test**: Run `./gradlew test` to verify the project builds and all tests pass.

## Development Workflow

We follow strict quality guidelines to maintain compiler safety, performance, and API stability:

1. **Test-Driven Development (TDD)**: Every feature or bugfix should be covered by corresponding unit tests.
2. **API Compatibility**: We use the Kotlin Binary Compatibility Validator. If you change public declarations:
   - Run `./gradlew apiDump` to update API baselines.
   - Commit the updated `.api` files along with your code.
3. **Code Formatting**: Follow standard Kotlin style guidelines.

## Pull Request Process

1. Create a descriptive feature branch.
2. Verify that `./gradlew test apiCheck` succeeds.
3. Submit a Pull Request targeting the `main` branch.
4. Provide a clear description of the problem solved and the approach taken.
