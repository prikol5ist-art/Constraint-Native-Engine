# Benchmark Methodology

This document outlines the methodology, assumptions, and design of the reproducible payload and timing benchmarks for the Constraint-Native Generative Engine.

---

## 1. Data Generation and Scenarios
To ensure reproducible and deterministic benchmarks, we generate synthetic fashion inventories using a fixed seed:
- **Seed Formula**: $20260719 + \text{inventorySize}$.
- **Inventory Distributions**: Candidates are distributed across predefined categories (e.g., tops, bottoms, outerwear, shoes), type hierarchies, materials, color palettes, and style attributes.
- **Sizes Evaluated**: 25, 100, 500, and 1,000 items.

---

## 2. Payload Measurement
We compare two strategies for sending context to the LLM:
1. **Inventory-First (Baseline)**: Sending the entire candidate inventory plus user context.
2. **Constraint-Native (Constrained)**: Running local constraints first, finding the top $K$ optimal solutions, and sending only the solution candidates and their scores.

### UTF-8 Byte Measurement
The serialized payloads are rendered using structured plain text (defined in `EvidencePayloadRenderer`). The byte size is determined using `payload.toByteArray(Charsets.UTF_8).size`.

### Token Estimation Formula
Token usage is estimated via the standard heuristic:
$$\text{estimatedTokens} = \lceil \frac{\text{UTF-8 bytes}}{4} \rceil$$
> [!IMPORTANT]
> This formula is an approximation for general sizing and does not reflect actual provider-specific tokenizer counts or billing.

---

## 3. Headline Scenario
The **500-item inventory with 3 desired solutions** is chosen as the headline scenario. This represents a typical catalog size for a user's closet or a curated retail shelf.

---

## 4. Verification and Validation
- **Constraint Verification**: The generated solutions are independently validated by `EvidenceVerifier` to guarantee zero hard-constraint violations (e.g. weather matching, item availability).
- **Candidate Integrity**: We verify that no unknown candidate IDs are accepted or returned in the solutions.

---

## 5. Performance Timing
- **JIT Warm-up**: The JVM is warmed up with 5 iterations before measurement.
- **Sampling**: 30 timed iterations are sampled per scenario.
- **Metrics**: p50 (median) and p95 (95th percentile) latency values.
- **Metadata**: System/JVM attributes (OS name, architecture, JVM version) and CPU model are recorded in the report.

---

## 6. Exact vs. Beam Search Comparison
For the 25-item scenario, we run both `EXACT` (exhaustive backtracking) and `BOUNDED_BEAM` (beam width = 32) search to compute the exact regret (loss in score), validating the quality of the beam search approximation.

---

## 7. Limitations
- **Synthetic Data**: The clothing catalog is generated synthetically and may not reflect real-world inventory complexities.
- **No Subjective Evaluation**: The benchmarks measure structural constraint and scoring performance; they do not assess subjective aesthetic quality.
- **No Cost/Billing Claim**: Actual API costs and token usage depend on the specific LLM provider's model (e.g. Gemini, OpenAI) and pricing model. We make no direct billing or cost reduction claims.
