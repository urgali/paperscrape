#!/usr/bin/env bash
# Generates a real release signing keystore for PaperScrape.
#
# Run this LOCALLY, on your own machine -- never in a shared/cloud environment, never send the
# resulting .jks file or its passwords to anyone (including Claude, in chat). This keystore is
# the app's permanent cryptographic identity: whoever holds it can publish an "update" that
# Android will accept over your existing install. Losing it means you can never again ship an
# update under the same signature; leaking it means someone else could.
#
# What this script does NOT do: it does not commit anything, does not touch git, does not upload
# anywhere. It only writes a .jks file to the path you choose.
set -euo pipefail

KEYSTORE_PATH="${1:-paperscrape-release.jks}"
KEY_ALIAS="${2:-paperscrape}"

if [ -f "$KEYSTORE_PATH" ]; then
  echo "Refusing to overwrite existing keystore at $KEYSTORE_PATH" >&2
  exit 1
fi

echo "Generating a new release keystore at: $KEYSTORE_PATH"
echo "Key alias: $KEY_ALIAS"
echo
echo "You'll be asked for a keystore password and a key password (they may be the same value)."
echo "Store both in a password manager -- there is no recovery if you lose them."
echo

keytool -genkeypair \
  -v \
  -keystore "$KEYSTORE_PATH" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10950 \
  -storetype PKCS12

echo
echo "Done. Next steps:"
echo
echo "1. Back up $KEYSTORE_PATH somewhere safe (password manager vault, encrypted drive) --"
echo "   NOT in this git repository, not in any public location."
echo
echo "2. Build locally with this key:"
echo "     export PAPERSCRAPE_RELEASE_STORE_FILE=\"\$(pwd)/$KEYSTORE_PATH\""
echo "     export PAPERSCRAPE_RELEASE_STORE_PASSWORD='<your keystore password>'"
echo "     export PAPERSCRAPE_RELEASE_KEY_ALIAS='$KEY_ALIAS'"
echo "     export PAPERSCRAPE_RELEASE_KEY_PASSWORD='<your key password>'"
echo "     ./gradlew assembleRelease"
echo
echo "3. To let CI build signed releases, add these GitHub Secrets"
echo "   (Settings -> Secrets and variables -> Actions -> New repository secret):"
echo "     RELEASE_KEYSTORE_BASE64   = base64 -w0 $KEYSTORE_PATH   (or base64 -i on macOS)"
echo "     RELEASE_STORE_PASSWORD    = <your keystore password>"
echo "     RELEASE_KEY_ALIAS         = $KEY_ALIAS"
echo "     RELEASE_KEY_PASSWORD      = <your key password>"
echo
echo "   The release workflow job decodes RELEASE_KEYSTORE_BASE64 back into a keystore file"
echo "   in a temp directory at build time only -- it is never written to the repository."
