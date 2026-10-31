# DJ Mix: continuous DJ-style Spotify mixing for Android

Pick one or more of your Spotify playlists (or Liked Songs) and DJ Mix plays them non-stop,
shuffled or in order, with DJ-style transitions between tracks.

**Download:** grab `DJMix.apk` from the [latest release](../../releases/latest) on your phone and install it
(allow "install unknown apps" for your browser when asked).

## Features

- Pick multiple playlists plus Liked Songs and merge them into one continuous mix.
- **Shuffle** (smart: no duplicates, avoids the same artist back to back, can alternate between playlists) or **In order**.
- Endless: when the queue runs out it reshuffles and keeps going.
- Two transition styles:
  - **DJ fade** (default): at the mix point the app fades the outgoing track down (equal-power curve),
    drops the next track at a low level and fades it up. You can tune fade-out and fade-in length,
    how deep the volume dips, a **mix out early** setting to cut long outros, and **skip intro** to cue the next track a few seconds in.
  - **Spotify crossfade**: the app keeps the next track queued in Spotify so Spotify overlaps them
    (a true crossfade). Turn on *Spotify → Settings → Playback → Crossfade* (8–12 s works well).
- Now-playing deck with artwork, a mix-point marker on the progress bar, "Mix next" (transition right now), and a tappable up-next list.
- Keeps mixing with the screen off (foreground service with notification controls).

## One-time setup (about 3 minutes)

You need the **Spotify app installed and logged in** on the phone, and **Spotify Premium**
(Spotify requires Premium for on-demand playback control and for developer apps).

1. Go to <https://developer.spotify.com/dashboard> → **Create app**.
   - App name / description: anything (e.g. "DJ Mix").
   - **Redirect URI:** `djapp://callback`
   - APIs used: tick **Web API** and **Android**.
2. In the app's settings, under **Android packages**, add:
   - Package name: `com.mansoor.djapp`
   - SHA1 fingerprint: `14:CB:F1:14:45:5D:23:C0:CF:84:07:E2:4D:37:7E:C2:69:F5:00:7D`
3. Copy the **Client ID** from the app's settings page.
4. Open DJ Mix, paste the Client ID, tap **Connect Spotify**, and approve in the browser.
   The first time the app connects to the Spotify app you'll also see a Spotify approval screen.

The setup screen in the app shows the redirect URI, package name and SHA1, with copy buttons.

## How it works

- **Web API** (Authorization Code + PKCE, no client secret) reads your playlists and their tracks.
- **Spotify App Remote SDK** controls playback in the Spotify app and streams player state
  (track, position) so the app knows exactly when to start each transition.
- Spotify only plays one stream at a time and gives third-party apps no audio access, so the
  *DJ fade* style rides the phone's media volume around each track change. The *Spotify crossfade*
  style hands the overlap to Spotify's own crossfade engine.

## Known limits (imposed by Spotify)

- Since Feb 2026, Spotify's Web API only returns the tracks of playlists **you own or collaborate on**.
  Other playlists (e.g. Spotify-made ones you follow) still work: they're played as *blocks* of N
  tracks (set the block size in settings) via Spotify itself, with DJ transitions at each track change.
  Tip: save a copy of a followed playlist to your own library ("Add to other playlist") to fully mix it.
- Spotify's audio-features / BPM endpoints are no longer available to new apps, so there's no beat-matching.
- The DJ fade changes the **phone's** media volume, so it doesn't work while Spotify is playing on another device through Connect.
- Most phones have about 15 media volume steps, so fades move in small steps.
- In Spotify-crossfade mode the app adds the next track to your Spotify queue. Anything already in your queue plays first.

## Build it yourself

GitHub Actions builds and publishes the APK on every push to `main` (see `.github/workflows/build.yml`).
Locally: open in Android Studio, or run `./gradlew assembleRelease` (JDK 17+, Android SDK 35).

The signing keystore (`app/dj.keystore`) is committed on purpose so every build has the same
SHA1 that's registered in the Spotify dashboard. It's only for this personal sideloaded app, so
don't reuse it for anything published.
