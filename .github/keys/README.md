# Development signing key

`development.p12` is an intentionally public, dedicated WideLight development key.
Alias: `widelight-development`; store/key password: `android`.
It keeps automatically published APKs update-compatible without secret setup.
It is not a production identity and must never be reused for another application.

For private signing, configure the four repository Actions secrets documented in
README.md. Keep the same certificate across builds. Switching signing identities
requires uninstalling an existing app signed by a different certificate (and loses
its local library), unless an Android signing-key migration is implemented.
