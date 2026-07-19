# Smart Wardrobe integration case study

## Status

This is a synthetic, local integration snapshot. It does not contain production wardrobe records, prompts, photos, user text, locations, credentials, or measured production latency.

## Integration

Smart Wardrobe consumes `engine-generative`, `adapter-gemini`, and `domain-fashion` through an optional Gradle composite build during local development. The app maps Room-backed `ClothingItem` values to portable fashion candidates at the app boundary; Android, Room, Supabase authentication, and credentials remain outside the open-source engine.

The constrained gateway:

1. maps the caller's finite inventory to typed candidates;
2. solves dress and separates templates independently;
3. enforces availability, laundry, weather, slot completeness, uniqueness, and locked-set atomicity locally;
4. merges and diversity-reranks feasible solutions;
5. optionally asks Gemini to rank known solution IDs through the existing authenticated Supabase proxy;
6. validates the remote response and falls back to deterministic local ordering on unknown IDs, duplicates, malformed output, timeout, cancellation-safe transport errors, or service failures;
7. resolves every selected ID through the original app inventory before returning the existing UI result type.

## Rollout

Home currently runs in shadow mode: legacy output remains user-visible while the constrained engine computes a local comparison without a second Gemini request. Fresh Looks, Inspiration matching, and the daily worker use the mandatory gateway. Travel capsule output retains its destination-specific contract but is rejected unless all IDs belong to the supplied inventory, every day is a complete outfit, laundry items are absent, and locked sets remain atomic.

Promotion of Home beyond shadow mode still requires replay evidence from at least 100 synthetic or consented redacted cases, zero hard-constraint violations, and no complete-outfit-rate regression.

## Privacy

The reusable engine is telemetry-free. App diagnostics may contain only process-salted ID hashes, constraint codes, slot counts, termination, node count, duration, ranker status, and token counts. Raw IDs, paths, images, notes, profile text, prompts, explanations, email-like values, locations, and exact wardrobe metadata are excluded.

## Verified synthetic properties

Automated tests cover:

- original-inventory membership and complete dress/separates templates;
- deterministic local fallback after an unknown ranker ID or transport failure;
- hard exclusion of laundry and rain-invalid footwear;
- atomic locked sets;
- explicit app-to-engine enum and attribute mapping;
- authenticated proxy request shape and coroutine cancellation;
- travel capsule ID, completeness, laundry, and locked-set validation;
- privacy-safe trace formatting;
- an architecture rule preventing direct legacy outfit-generation calls outside the approved adapter.

Machine-sensitive production performance claims are intentionally omitted until a documented device and replay corpus are approved.
