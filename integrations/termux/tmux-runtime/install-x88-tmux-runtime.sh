#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd -P)"
mkdir -p "$HOME/bin" "$HOME/.ariana/logs" "$HOME/.ariana/jobs"

for file in x88-tmux-bootstrap.sh x88-tmux-status.sh x88-tmux-stop.sh x88-adapter.sh; do
  cp "$HERE/$file" "$HOME/bin/$file"
  chmod 700 "$HOME/bin/$file"
done
cp "$HERE/x88-termux-adapter.contract.json" "$HOME/.ariana/x88-termux-adapter.contract.json"
chmod 600 "$HOME/.ariana/x88-termux-adapter.contract.json"

echo '[X88][OK] tmux runtime installed'
echo 'Run: ~/bin/x88-tmux-bootstrap.sh'
if [ "${1:-}" = "--bootstrap" ]; then
  exec "$HOME/bin/x88-tmux-bootstrap.sh"
fi
