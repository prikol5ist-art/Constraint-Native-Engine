# Contributing to Constraint Native Engine

Thank you for your interest in contributing to the Constraint Native Engine! This guide outlines our processes and standards to help you get started.

Please note our project governance policies are documented in [GOVERNANCE.md](GOVERNANCE.md), and our security vulnerability reporting guidelines are documented in [SECURITY.md](SECURITY.md).

## Code of Conduct

We aim to foster an open, welcoming, and professional community. Please treat all contributors with respect. The full code of conduct is available in [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Development Philosophy

The deterministic solver module has priority over generative adapters. No contribution may bypass candidate validation or introduce network dependencies into `:engine-core` or `:engine-solver`.

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
3. **Local Publication**: We verify local maven publishing to ensure artifact generation (sources and Dokka documentation) is correct:
   - Run `./gradlew publishToMavenLocal`.
   - Note that only the five library modules (`engine-core`, `engine-solver`, `engine-generative`, `adapter-gemini`, `domain-fashion`) are published.
4. **Code Formatting**: Follow standard Kotlin style guidelines.

## Pull Request Process

1. Create a descriptive feature branch.
2. Verify that the standalone verification block succeeds:
   ```bash
   ./gradlew clean test koverVerify apiCheck dokkaHtmlMultiModule
   ./gradlew :benchmark:benchmark
   ./gradlew :examples:wardrobe-cli:run --args='--occasion formal --temperature 8 --diversityWeight 0.5'
   ./gradlew publishToMavenLocal
   ```
3. Submit a Pull Request targeting the `main` branch.
4. Provide a clear description of the problem solved and the approach taken.
