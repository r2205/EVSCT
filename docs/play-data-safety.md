# Google Play — Data safety form (EVSCT)

Reference for filling in the Play Console **Data safety** section for EVSCT
(`com.evsct.app`). Keep this in sync with `docs/privacy-policy.html`.

**Guiding principle:** Play defines **"collected" = data that leaves the
device.** Almost everything EVSCT stores stays on the phone, so it does *not*
count as collected. **Location is the one real exception** — geocoding (address
lookup) and the map send it to Google.

## Part A — Overview questions

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | Only because location is sent off-device for address lookup + the map. |
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
| Required or optional? | **Optional** — "Users can choose whether this data is collected" (only read when they tap GPS autofill) |
| Purpose | **App functionality** only (no analytics, ads, or personalization) |

### Mark "Not collected" for everything else

Nothing below leaves the device:

- **Personal info** (name, email) — no account; nothing transmitted → Not collected
- **Financial info** — charging *costs* are the user's own notes, stored locally, never transmitted, and aren't payment instruments → Not collected
- **Photos & videos** (receipt / vehicle photos) — local only → Not collected
- **Files & docs** (receipt PDFs) — local only → Not collected
- **App activity / App info & performance** — no analytics or crash-reporting SDK → Not collected
- **Messages, Contacts, Calendar, Health & fitness, Audio, Web browsing** — not used → Not collected

## Judgment calls to confirm (Google Maps SDK)

1. **Is location "Shared"?** It's sent to Google for geocoding and map tiles. If
   you treat Google Maps Platform as a *service provider* processing on your
   behalf, "Shared" can be **No**. To be conservative you can mark **Shared =
   Yes**, purpose **App functionality**. Both are defensible; leaning **No
   (service provider)**.
2. **Device or other IDs** — the Maps SDK may access device identifiers for its
   own operation. See Google's "Data safety section guidance for the Maps SDK."
   Conservative option: declare **Device or other IDs = Collected, App
   functionality**. Many Maps-only apps leave this off — decide deliberately.

## Through-line

EVSCT keeps your data on your device; the only thing that goes to a third party
(Google) is location, and only to draw the map and look up addresses — which
matches `docs/privacy-policy.html`, so the two stay consistent.
