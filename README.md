# DJ Mix: continuous DJ-style Spotify mixing for Android

Pick one or more of your Spotify playlists (or Liked Songs) and DJ Mix plays them non-stop,
shuffled or in order, with DJ-style transitions between tracks.

**Download:** grab `DJMix.apk` from the [latest release](../../releases/latest) on your phone and install it
(allow "install unknown apps" for your browser when asked).

## Features

- Pick multiple playlists plus Liked Songs and merge them into one continuous mix.
- Modes: **Shuffle** (no duplicates, avoids the same artist back to back, can alternate between playlists),
  **In order**, or **Custom** (your hand-picked songs first, then the selected playlists).
- Endless: when the queue runs out it reshuffles and keeps going.
- **Deck**:
  - Spinning vinyl with the album art as its label. Drag it round like a jog wheel to move through the track.
  - Progress bar with two handles: drag or tap the white playhead to seek, and drag the purple **mix marker**
    to choose when the next song comes in (e.g. move 1:30 to 2:30 to play the song longer). Tap **Auto** to go back.
  - ±10 s, **Loop last / Loop next** N seconds with a 1–16 s length slider (adjustable while looping), **Repeat** track, "Mix next now", previous.
- **Search**: filter your playlists, or search all of Spotify for songs (Play now / Next / +Queue) and playlists to add to the mix.
- **About** (bottom of settings): app version, Spotify account, Spotify app and SDK versions, device.
- **Queue**: see everything coming up, search it, mix into any song now, reorder, remove. Open any playlist
  to **Play** a song now, play it **Next**, or **+Queue** it, even in shuffle mode.
- **Auto mix**:
  - **Random play length**: each song mixes out at a random point within a range you set (e.g. 50–90 %).
  - **Smooth fade** (default): at the mix point the song fades down from wherever it is, with no jumping.
    The next song (already waiting in Spotify's queue, so it starts fast) comes in underneath at low
    volume and rises. Fade lengths and the lowest volume are configurable.
  - **Outro crossfade** (experimental): Spotify can only blend the *last* seconds of a song into the next,
    so at the mix point the app jumps the song to its outro and lets Spotify's crossfade
    (*Settings → Playback → Crossfade*) take over. It only helps if Spotify's own crossfade works on your phone.
  - True two-deck mixing (two songs playing at once from any point, tempo, beat-matching) needs Spotify's
    partner DJ integration, which only djay, rekordbox and Serato have. It isn't available to other apps.
  - **Smart switch (beta)**: listens to the audio output around the mix point and switches at a break where
    the voice band goes quiet, which is often where there are no vocals. Needs microphone permission; nothing is recorded.
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
- Spotify's audio-features / BPM endpoints are no longer available to new apps, so there's no beat-matching,
  and Smart switch can only guess at breaks from the sound.
- **No tempo / speed control**: App Remote only exposes playback speed for podcasts. Premium downloads are
  encrypted and only playable inside Spotify, and Spotify's terms forbid caching or altering its audio.
- Apps can only play one Spotify stream at a time, so overlap mixes rely on Spotify's own crossfade, whose length
  only Spotify's settings can change. Jumping to the outro is audible as a quick cut, softened by a short volume dip.
- Loops are made by seeking, so there's a tiny gap at each loop point.
- Dip fades change the **phone's** media volume, so they don't work while Spotify is playing on another device through Connect.
  Most phones have about 15 media volume steps, so fades move in small steps.
- In overlap mode the app adds the next track to your Spotify queue. Anything already in your queue plays first,
  and if you stop a mix, the queued song stays in Spotify's queue.

## Build it yourself

GitHub Actions builds and publishes the APK on every push to `main` (see `.github/workflows/build.yml`).
Locally: open in Android Studio, or run `./gradlew assembleRelease` (JDK 17+, Android SDK 35).

The signing keystore (`app/dj.keystore`) is committed on purpose so every build has the same
SHA1 that's registered in the Spotify dashboard. It's only for this personal sideloaded app, so
don't reuse it for anything published.
