# Security policy

## Supported versions

The latest GitHub Release receives security fixes. Older APKs may remain available for reproducibility but are unsupported.

## Reporting

Use GitHub's private vulnerability reporting feature. Do not open a public issue containing an unpatched exploit, tokenized local receiver URL, personal media, signing material, or device identifiers.

## Local receiver threat model

Phone audio starts an HTTP server bound to the local network. Access requires a randomized 96-bit path token shown as a QR code. The token exists only for the app process lifetime and responses disable caching. Use the feature only on a trusted network. It is intentionally HTTP because self-signed TLS cannot provide a usable zero-configuration phone-browser flow; network observers may still see traffic. Do not expose TCP port 8088 to the public internet.

## Signing

Official APKs are signed in GitHub Actions using protected repository secrets. Release notes publish the certificate SHA-256 fingerprint. If the signing key ever changes, the release notes and README must state that existing installs cannot update in place and that uninstalling removes app data.

