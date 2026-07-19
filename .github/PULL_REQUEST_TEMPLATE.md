## Description

Please include a summary of the changes and the related issue. Please also include relevant motivation and context.

## Checklist

Please verify that you have completed the following steps before submitting your Pull Request:

- [ ] All unit tests pass locally: `./gradlew test`
- [ ] Kover coverage verification passes: `./gradlew koverVerify`
- [ ] Binary compatibility check passes: `./gradlew apiCheck`
- [ ] Dokka generation succeeds: `./gradlew dokkaHtmlMultiModule`
- [ ] Tracked evidence is verified: `./gradlew :benchmark:verifyEvidence`
- [ ] Snapshot scanner passes: `./scripts/verify-public-snapshot.sh .`

## Additional Details

List any other relevant info, dependencies, or architectural considerations.
