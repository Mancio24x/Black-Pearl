# Publishing a release

Only when the owner asks ("pubblica"), and only from `main` after his merge.

```bash
R=Mancio24x/Black-Pearl          # ALWAYS pass --repo: gh otherwise picks the "upstream" Quillpad remote
gh pr view <N> --repo $R --json state,mergedAt     # merged?
git fetch origin && git diff --stat origin/main <tested-branch-tip>   # empty = main is the tested code
git tag -a vX.Y.Z origin/main -m "Black Pearl X.Y.Z"
git push origin refs/tags/vX.Y.Z                    # push only the tag
```

- The tag starts `.github/workflows/release.yml`: version from the tag (`-PreleaseVersion`,
  versionCode = X*10000 + Y*100 + Z, e.g. 0.5.1 → 501), signed with the keystore in **GitHub Actions secrets**,
  creates a **draft** release with `black-pearl-X.Y.Z.apk` and the R8 mapping.
- Wait for it with a loop (`gh run watch` sometimes returns early):
  `until [ "$(gh run view $ID --repo $R --json status --jq .status)" = completed ]; do sleep 30; done`
- Verify before publishing:

```bash
gh release download vX.Y.Z --repo $R --pattern '*.apk' --dir "$D"
BT=$(ls -d $HOME/Library/Android/sdk/build-tools/* | sort -V | tail -1)
"$BT/apksigner" verify --print-certs "$D"/*.apk | grep SHA-256
#   must be e9054f1cf9b6ac97badc7b91199c5065108dd59d23b52b5415dfbb03ab2ffb55 (same key = updates install over)
"$BT/aapt2" dump badging "$D"/*.apk | head -1      # package io.github.mancio24x.blackpearl, versionCode, versionName
```

- Publish with Italian notes (same style as previous releases: "## Novità…", short bullets, last line
  "Aggiornamento diretto dalle versioni precedenti (stessa firma)."):

```bash
gh release edit vX.Y.Z --repo $R --title "Black Pearl X.Y.Z" --notes-file notes.md --draft=false --latest
```

- The owner installs through **Obtainium** from the Releases page; the latest release must contain the APK.
- The keystore backup and its password note are on the owner's Mac (`~/Desktop/crafting/BlackPearl-firma/`);
  never copy them into the repo or into messages.
