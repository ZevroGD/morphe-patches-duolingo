# Zevro Morphe Patches

Custom Morphe patches for Duolingo.

## Add this source to Morphe Manager

Add this repository URL as a patch source:

`https://github.com/ZevroGD/morphe-patches-duolingo`

The source metadata is published in [patches-bundle.json](patches-bundle.json). The patch catalog is in [patches-list.json](patches-list.json).

## Current patches

- **Duolingo Linear Progress Bar**: replaces the overshoot interpolator used by the matched progress-bar animation with a linear interpolator.

## Releases

Releases are created manually through **Actions → Build and Release Morphe Patches → Run workflow**. Enter a semantic version such as `1.0.0`. The workflow builds the bundle, publishes it as a GitHub release, and updates the source metadata.

Versions follow semantic versioning:
- Patch release: bug fixes, e.g. `1.0.1`
- Minor release: new patches or features, e.g. `1.1.0`
- Major release: breaking changes, e.g. `2.0.0`

## Signing

Bundle signing is optional. Unsigned releases use an empty `signature_download_url`; no signing key is required. If signing is added later, the private key must be stored only as the GitHub Actions secret `MORPHE_SIGNING_KEY`. Never commit the private key.

## Local build

Run:

```bash
./gradlew -Pversion=1.0.0 :patches:buildAndroid
```

The generated `.mpp` file is in `patches/build/libs/`.
