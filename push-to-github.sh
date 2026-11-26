#!/data/data/com.termux/files/usr/bin/bash
# Run inside the MarkAndroid folder in Termux:   bash push-to-github.sh YOUR_GITHUB_NAME YOUR_REPO_NAME
set -e
U="$1"; R="$2"
if [ -z "$U" ] || [ -z "$R" ]; then echo "usage: bash push-to-github.sh GITHUB_USERNAME REPO_NAME"; exit 1; fi
read -s -p "GitHub token (classic, scopes: repo + workflow): " T; echo
git init -q 2>/dev/null || true
git config user.email "mark@local"; git config user.name "Mark"
git add -A
git commit -qm "Mark Android" 2>/dev/null || true
git branch -M main
git remote remove origin 2>/dev/null || true
git remote add origin "https://$U:$T@github.com/$U/$R.git"
git push -u origin main --force
git remote set-url origin "https://github.com/$U/$R.git"     # do not leave the token in .git/config
echo
echo "Pushed. The build starts by itself: https://github.com/$U/$R/actions"
