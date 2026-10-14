# Release signing key (encrypted)

`release.jks.enc` is Lifestream's release keystore, encrypted with AES-256-CBC (PBKDF2, 600,000
iterations). It is useless without the `LIFESTREAM_KEYSTORE_PASSWORD` repository secret, which
the build workflow uses to decrypt it into the runner's temp folder and deletes afterwards.

The plain `.jks` is git-ignored and must never be committed.
