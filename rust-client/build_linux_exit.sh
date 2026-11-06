#!/usr/bin/env bash
# The client for the first VPS of a cascade: static Linux builds the app uploads when a cascade is added.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
WRAP="$ROOT/build/zig-wrappers"
mkdir -p "$WRAP" "$ROOT/../app/src/main/assets"
for tool in cc c++; do
  file="$WRAP/zig$tool"
  cat > "$file" <<SH
#!/usr/bin/env bash
args=()
for arg in "\$@"; do
  [[ "\$arg" == --target=* ]] || args+=("\$arg")
done
exec zig $tool -target "\$MRTT_ZIG_TARGET" "\${args[@]}"
SH
done
printf '#!/usr/bin/env bash\nexec zig ar "$@"\n' > "$WRAP/zigar"
chmod +x "$WRAP"/*
rustup target add x86_64-unknown-linux-musl aarch64-unknown-linux-musl --toolchain 1.97.1
build() {
  local target="$1" zig_target="$2" asset="$3" env_name
  env_name="${target//-/_}"
  env "CC_$env_name=$WRAP/zigcc" "CXX_$env_name=$WRAP/zigc++" "AR_$env_name=$WRAP/zigar" \
    "CARGO_TARGET_$(echo "$env_name" | tr '[:lower:]' '[:upper:]')_LINKER=$WRAP/zigcc" \
    MRTT_ZIG_TARGET="$zig_target" CARGO_TARGET_DIR="$ROOT/build/linux-exit" \
    cargo +1.97.1 zigbuild --release --target "$target" --bin client
  cp "$ROOT/build/linux-exit/$target/release/client" "$ROOT/../app/src/main/assets/$asset"
  ls -lh "$ROOT/../app/src/main/assets/$asset"
}
build x86_64-unknown-linux-musl x86_64-linux-musl mrtt-exit-client-amd64
build aarch64-unknown-linux-musl aarch64-linux-musl mrtt-exit-client-arm64
