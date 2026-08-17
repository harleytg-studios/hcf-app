# Release signing

Development package: `com.harleytg.forum.dev`

All new DEV/pre-release APKs must use the persistent HCF DEV v2 signing certificate.

Expected SHA-256 signer certificate:
`AC:6B:91:3E:E0:80:94:83:37:1F:66:A7:3C:C5:D0:BB:DA:1E:45:D4:91:E1:43:57:4D:40:46:74:B0:23:AB:CE`

The build script enforces this fingerprint before and after signing.

The private signing key and password must never be committed to the public source
repository or included in distributed source ZIPs.

Older DEV signing lines are not update-compatible unless their exact private signing
key is recovered. Do not rotate the DEV key again.
