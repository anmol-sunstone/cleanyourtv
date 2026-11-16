# Cozy Christmas: a candle, a bell and snow

This branch (`christmas-skin`) is a fork of [CleanYourTV](https://github.com/Gathin23/cleanyourtv) that adds a
Christmas skin to its launcher and a seven-picture `christmas` theme that turns the skin on. Everything else is
the main repo as of commit `ddc3b2cd`; its README, prompt and tools apply unchanged.

<img src="themes/christmas/screenshot.jpg" width="640" alt="Home with the Cozy Christmas skin">

It lives here rather than in the main repo because the main repo keeps seasonal skins out of its launcher, and
because this build is over its APK size limit (see [Cost](#cost)).

## What it adds

| Where | What you see |
|---|---|
| Network button | An Advent candle. Its flame follows the connection: taller with a stronger Wi-Fi signal, steady on Ethernet, out with a wisp of smoke when the TV is offline. The wax is full height until 1 December and a little shorter each day until the 25th |
| Inputs button | A gold bell with holly that swings twice when focused |
| Settings button | A snowflake that turns when focused |
| Home | Snow falling for as long as Home is on screen; it stops when you leave Home |
| Dock | Drifts of settled snow along the dock's top edge |
| Greeting | "39 sleeps to Christmas" for the 60 days before the 25th, "Merry Christmas" from the 25th to the 31st, "Happy New Year" in January (English only) |
| Focus glow | Candlelight (`#FFC46B`) |

The `christmas` theme adds seven pictures that change every 20 minutes, in order, each with its photographer's
name under the buttons: the main repo's own sleigh illustration first, then six photos from Wikimedia Commons.
Authors and licences are in [`themes/christmas/CREDITS.md`](themes/christmas/CREDITS.md).

No network access, no new permission, no new dependency, no background service. With `reduced_motion` on, the
snow does not fall and the icons stay still.

## Use it

1. Build and install the launcher from this branch, the same way as the main repo:
   `launcher/build-and-install.sh`, or `tools/docker-build.sh` then `SKIP_BUILD=1 launcher/build-and-install.sh`
   if you have no JDK.
2. Push the theme: `TV_HOST=<tv ip> tools/theme.sh themes/christmas`

The skin switches itself off after 6 January 2027 (`festival_until` in the theme's `config.json`); the pictures
stay until you run `tools/theme.sh --reset`. To get the skin with your own theme, add
`"seasonal_skin": "christmas"` to its `config.json`. The keys are described in
[`themes/README.md`](themes/README.md).

## Cost

- **Size:** the APK is 593,645 B (about 580 KB) as built for the test TV. The main repo's limit is 589,824 B,
  so this branch is about 4 KB over it and fails that repo's size check. The main repo's README on this branch
  still says "about 572 KB"; that figure describes the main repo's launcher, not this one.
- **CPU:** the falling snow redraws about 30 times a second. On the Sony BRAVIA 4K UR3 the launcher used about
  10% of one of its four CPU cores while idle on Home, against about 0% without the snow.
- **Memory:** 67 MB with the snow on that TV, against 55 MB before it was made continuous.

## Tested on

- Sony BRAVIA 4K UR3, Android TV 10
- Sony BRAVIA 4K VH22, Google TV 12

Home, the candle, the bell, the snowflake, the dock snow and the falling snow were checked from screenshots
taken over ADB. Not checked: the candle going out when the TV is offline, and how the bell swing, the snowflake
turn and the flame's flicker look in motion.

## What changed in the code

- New: `CandleDrawable`, `ChristmasIconDrawable`, `SnowfallView` in `launcher/app/src/main/java/com/gathin/tvhome/`
- Changed: `Festival` (the `christmas` name, the countdown, the candle's height), `MainActivity` (applies the
  skin, starts and stops the snow), `DockDecoration` (the dock's snow), `strings.xml` (four English strings),
  `FestivalTest` (three tests)
- Theme: `themes/christmas/` and its row in `themes/README.md`

## Licence

The code is MIT, like the main repo. The six Commons photos keep their own licences (CC BY and CC BY-SA), listed
in `themes/christmas/CREDITS.md`.
