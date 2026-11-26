# Build the APK using only your phone (about 15 minutes, free)

Android Studio does not run on phones. Instead GitHub builds the APK for you in the cloud and you download it.

## One time
1. Install **Termux** from F-Droid (the Play Store version is outdated).
2. In your phone browser: create a free account on github.com, then **New repository** -> name it `mark-android` -> **Private** -> Create (leave it empty).
3. GitHub -> Settings -> Developer settings -> Personal access tokens -> **Tokens (classic)** -> Generate new token -> tick **repo** and **workflow** -> copy it.

## Every build
In Termux:
```
pkg install -y git unzip
termux-setup-storage
cp ~/storage/downloads/MarkAndroid.zip ~ && cd ~ && unzip -o MarkAndroid.zip && cd MarkAndroid
bash push-to-github.sh YOUR_GITHUB_NAME mark-android
```
(paste the token when asked; nothing is shown while you paste)

Then open `https://github.com/YOUR_GITHUB_NAME/mark-android/actions`, wait for the green tick (3-6 minutes), open the run, and download **Mark-debug-apk** at the bottom. Unzip it with your file manager and install the APK (allow "install unknown apps" for the file manager).

## If the run turns red
Open the failed step "Build debug APK", copy the red error lines and send them to Claude. Fix, re-run `push-to-github.sh`, repeat. A first build of 2,600 lines of never-compiled code will probably need a few rounds.

## After installing
1. Settings -> Apps -> Mark -> three dots -> **Allow restricted settings** (Android 13+, otherwise the accessibility switch is greyed).
2. Open Mark -> "Enable accessibility service" -> turn Mark on.
3. Paste the Gemini API key (from aistudio.google.com), allow the permissions it asks for.
4. Test in this order: type a small task -> Live voice -> wake word.
