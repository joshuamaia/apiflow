# Extra CA certificates for image builds (optional)

Leave this folder empty in normal environments.

If your network inspects TLS traffic (corporate proxy, antivirus "web shield", etc.), the Maven and npm
downloads inside the Docker builds fail with `certificate verify failed`. Put the inspecting root
certificate here as a PEM file with the `.crt` extension and rebuild:

```bash
docker compose build --no-cache
```

The certificates are trusted **only in the build stages** (system store, Java `cacerts` and
`NODE_EXTRA_CA_CERTS`); they are not copied into the runtime images. `*.crt` files in this folder are
git-ignored.
