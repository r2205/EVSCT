# Google Play — Data safety form (EVSCT)

Reference for filling in the Play Console **Data safety** section for EVSCT
(`com.evsct.app`). Keep this in sync with `docs/privacy-policy.html`.

**Guiding principle:** Play defines **"collected" = data that leaves the
device.** Almost everything EVSCT stores stays on the phone, so it does *not*
count as collected. **Location is the one real exception** — both the device's
coordinates *and the location text the user types* are sent to Google, for
address lookup and to draw the map.

> **Read "When location data leaves the device" below before answering the
> form.** Some of those requests are automatic — they are not limited to the
> moment the user taps "GPS autofill", and one of them happens even if the
> user denied the location permission outright.

## Part A — Overview questions

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | Only because location — device coordinates and typed address text — is sent off-device for address lookup + the map. |
| Is all collected data encrypted in transit? | **Yes** | Google Maps / geocoding calls use HTTPS. |
| Do you provide a way for users to request data deletion? | **Yes** | Users delete sessions/trips/vehicles in-app, and Clear data / uninstall wipes everything. No account-deletion URL needed — there are no accounts and no server-side data. |

## Part B — Data types

### Declare collected → Location only

| Field | Answer |
|---|---|
| Data type | **Approximate location** and **Precise location** |
| Collected? | **Yes** |
| Shared? | See judgment call #1 below |
| Processed ephemerally only? | **No** (resolved coordinates/address are saved locally with the session) |
| Required or optional? | **Optional** — "Users can choose whether this data is collected". The app is fully usable without ever opening the Map tab or tapping a location control; see judgment call #3. |
| Purpose | **App functionality** only (no analytics, ads, or personalization) |

### When location data leaves the device

The honest answer is broader than "when the user taps GPS autofill", which is
what an earlier draft of this file said. Six paths, three of them automatic:

| Trigger | What goes to Google | Initiated by |
|---|---|---|
| "GPS autofill" on the session form | Device coordinates, reverse-geocoded to an address | User taps |
| "My location" on the map or the location picker | Device coordinates | User taps |
| Confirming a point in the location picker | The picked coordinates, reverse-geocoded to an address | User taps |
| **Opening the Map tab** | The **typed** address / city / province of each distinct logged stop that has no coordinates yet, forward-geocoded into coordinates | **Automatic** |
| **Saving a session whose address fields were edited** | That session's **typed** address / city / province, forward-geocoded | **Automatic** |
| **Whenever the map or the location picker is on screen** | Map viewport coordinates, to fetch tiles | **Automatic** |

Three things follow that are easy to get wrong on the form:

- **Direction.** The last three send *text the user typed* and get coordinates
  back. The privacy policy currently describes only the reverse (coordinates
  in, address out) — see judgment call #4.
- **Permission is not the gate.** Forward geocoding uses the platform
  `Geocoder`, which needs no location permission. A user who denied location
  outright still has their typed stop addresses sent to Google the first time
  they open the Map tab.
- **Throttling is not prevention.** The map backfill retries previously failed
  lookups at most once a day, but an address that is new or edited since the
  last attempt is looked up on the next map open regardless.

Implementation, if you need to re-check this: `MapViewModel.runBackfill`,
`SessionEditViewModel.save` (the post-save re-geocode), and `LocationAutofill`.

### Mark "Not collected" for everything else

Nothing below leaves the device:

- **Personal info** (name, email) — no account; nothing transmitted → Not collected
- **Financial info** — charging *costs* are the user's own notes, stored locally, never transmitted, and aren't payment instruments → Not collected
- **Photos & videos** (receipt / vehicle photos) — local only → Not collected
- **Files & docs** (receipt PDFs) — local only → Not collected
- **App activity / App info & performance** — no analytics or crash-reporting SDK → Not collected
- **Messages, Contacts, Calendar, Health & fitness, Audio, Web browsing** — not used → Not collected

## Judgment calls to confirm

1. **Is location "Shared"?** It's sent to Google for geocoding and map tiles. If
   you treat Google Maps Platform as a *service provider* processing on your
   behalf, "Shared" can be **No**. To be conservative you can mark **Shared =
   Yes**, purpose **App functionality**. Both are defensible; leaning **No
   (service provider)**.
2. **Device or other IDs** — the Maps SDK may access device identifiers for its
   own operation. See Google's "Data safety section guidance for the Maps SDK."
   Conservative option: declare **Device or other IDs = Collected, App
   functionality**. Many Maps-only apps leave this off — decide deliberately.
3. **Does typed address text count as "Location"?** It isn't a device-sensor
   reading, but it is information about where the user has been, and it does
   leave the device. Treating it as Location is the safe reading, and it costs
   nothing on the form — Location is already declared collected, so the only
   thing that changes is the justification above. It does slightly weaken
   "Optional": a user who never grants the location permission still transmits
   this by opening the Map tab. "Optional" still holds, because using the Map
   tab is the user's choice and the rest of the app works without it — but if
   you want the stricter reading, the fix is a setting that turns the automatic
   backfill off, not a different form answer.
4. **`docs/privacy-policy.html` needs the matching correction.** It currently
   says location is read "only when you actively request it" and describes
   Google as receiving "the coordinates being looked up" — accurate for the
   device-location paths and for reverse geocoding, but it never mentions that
   typed address text is sent, or that the map backfill and the post-save
   re-geocode run on their own. Until it is updated, the two documents
   disagree.

## Through-line

EVSCT keeps your data on your device. The one thing that goes to a third party
(Google) is location — the device's coordinates when you ask for them, plus the
address text you type, which the app looks up automatically to place your stops
on the map — and only to draw the map and resolve addresses. Nothing else is
transmitted.
