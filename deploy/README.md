# Download site

`deploy/site/index.html` and the signed release APK are served from the Mac mini at
`https://localdex.midolog.cc`. The APK is built separately from the GitHub release workflow.

The Mac mini serves `/Users/kimjinseong/Sites/localdex` through nginx on
`127.0.0.1:8094`. A dedicated `localdex-macmini` Cloudflare Tunnel maps the public
hostname to that local port. Keep the APK filename, version, and SHA-256 shown in the
page in sync when publishing a new version.

The nginx, tunnel, and macOS LaunchAgent configurations are tracked under `deploy/`.
The tunnel credential JSON stays on the Mac mini and is never committed.

The release signing keystore is stored outside the repository. Never commit it or
the signing environment file. Build with the four `LOCALDEX_SIGNING_*` environment
variables described in the root README, then verify the APK signature and checksum
before replacing the hosted file.
