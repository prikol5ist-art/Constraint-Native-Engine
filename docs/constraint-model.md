# Constraint Model and Search

The engine uses a deterministic model where combinatorial solutions are constructed by filling slots with inventory candidates. This document explains the mathematical foundations of candidates and slots, constraint evaluation, and search execution strategies.

---

## 1. Slots and Candidates

The core problem is defined as mapping a set of available items (**Candidates**) into specific slots (**Slot Requirements**).

### Candidate
A `Candidate` represents a single item in the inventory that can be selected.
*   `id`: A unique `CandidateId`.
*   `attributes`: Strongly-typed domain-specific metadata (generic type parameter `A`).
*   `eligibleSlots`: A set of `SlotId`s specifying which slots this candidate can be placed in.
*   `groupId`: An optional `GroupId` indicating item groups (e.g. to prevent selecting multiple items from the same group).

### SlotRequirement
A `SlotRequirement` defines a target location in the assembly template that needs to be filled with candidates.
*   `slot`: A unique `SlotId`.
*   `min`: The minimum number of candidates that must be assigned to this slot (must be $\ge 0$).
*   `max`: The maximum number of candidates that can be assigned to this slot (must be $\ge \text{min}$).

An **Assignment** binds a `Candidate` to a `SlotId`. A **PartialAssembly** holds a list of assignments and a boolean indicating if it is considered complete. Candidates in an assembly must be unique (no candidate can be reused in multiple slots).

---

## 2. Hard Constraints and Soft Scorers

The engine evaluates candidates during traversal using two types of evaluators:

### Hard Constraints
`HardConstraint` is a functional interface:
```kotlin
fun interface HardConstraint<A, C> {
    fun evaluate(assembly: PartialAssembly<A>, context: C): ConstraintVerdict
}
```
At any point in the search tree (on partial or complete assemblies), the constraint returns a `ConstraintVerdict`:
1.  `Satisfied`: The assembly is valid according to this constraint.
2.  `Undetermined`: There is not enough information in the partial assembly to evaluate validity (e.g., waiting for other slots to be filled). The search continues.
3.  `Violated(code, message)`: The assembly violates the constraint. The solver immediately **prunes the search subtree**, backtracking to avoid wasting computation on invalid paths. The violation `code` must match `^[a-z0-9_.-]{1,80}$`.

### Soft Scorers
`SoftScorer` is a functional interface:
```kotlin
fun interface SoftScorer<A, C> {
    fun score(assembly: PartialAssembly<A>, context: C): ScoreComponent
}
```
Unlike hard constraints, soft scorers evaluate the quality of a partial or complete assembly.
*   Each scorer returns a `ScoreComponent` containing a name and a finite `Double` value.
*   The total score is the sum of all `ScoreComponent` values.
*   Scores represent penalties (negative numbers) or rewards (positive numbers).
*   During `BOUNDED_BEAM` search, partial states are sorted by their current soft scorer sum to select the top beam elements for expansion.

---

## 3. Search Mode Behavior

The engine implements two distinct state-space traversal modes:

### SearchMode.EXACT
A complete, depth-first backtracking search (DFS) with branch-and-bound pruning.
*   **Behavior**: Systematically explores all valid combinations of candidate-to-slot assignments.
*   **Pruning**: If any hard constraint returns `Violated`, the entire sub-tree is pruned.
*   **Guarantees**: Guarantees finding the absolute mathematically optimal solution within the pruned search space according to the soft scorers.
*   **Scalability**: Scales exponentially. Suitable for smaller inventories or tight slot templates.

### SearchMode.BOUNDED_BEAM
A level-by-level breadth-first beam search.
*   **Behavior**: At each level (adding one assignment at a time), it generates all valid successor states by trying to assign candidates to eligible slots.
*   **Beam Width**: Solvers keep only the top `beamWidth` states at each level, sorted by their current soft scores.
*   **Pruning**: States violating hard constraints are immediately discarded.
*   **Scalability**: Scales linearly with inventory size and slot counts. Highly scalable, making it suitable for large catalog sizes (e.g., 1000+ items).
*   **Trade-off**: Since it retains only the top `beamWidth` states at each level, it may skip the absolute mathematically optimal solution.

---

## 4. Budgets and Limits

To prevent infinite loops or CPU exhaustion from combinatorial explosions, search execution is governed by `SearchBudget` limits.

*   `maxExpandedNodes`: The maximum number of nodes the solver is allowed to expand.
*   `maxDurationMillis`: The maximum wall-clock duration in milliseconds the search is allowed to run.
*   `beamWidth`: The width parameter for `BOUNDED_BEAM` search (must be $\ge$ `desiredSolutions`).

### Guard Check Timing
The solver checks limits using `SearchBudgetGuard`:
1.  **Before Node Expansion**: The solver increments the expanded node counter. If `expandedNodes > maxExpandedNodes`, it halts search immediately and returns `SearchTermination.NODE_BUDGET_EXHAUSTED`.
2.  **Time Elapsed Check**: The solver polls the monotonic clock. If the duration since start exceeds `maxDurationMillis`, it halts search immediately and returns `SearchTermination.TIME_BUDGET_EXHAUSTED`.
3.  **Result State**: Any solutions found prior to budget exhaustion are returned alongside the corresponding termination token.
