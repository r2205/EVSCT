#!/usr/bin/env python3
"""
Generate the ROAD-TRIP sample backup: a third, standalone pack that is NOT a
superset of the other two. Two vehicles only, both heavy long-distance
travellers, spanning three full years (Aug 2023 -> Jul 2026):

  * 2023 Kia EV6 GT-Line AWD - downtown condo car with street parking, so it
    NEVER charges at home. Every session is public: curbside posts, the
    workplace garage, flat-fee mall garages and DC fast. Zero "Home" sessions
    by design - use it to exercise stats/filters when home kWh is exactly 0.
  * 2022 Ford Mustang Mach-E Premium AWD ER - suburban home L2 (sparsely
    logged, like a real user who can't be bothered tracking the flat overnight
    rate) plus every road-trip charge.

Eight trips, all long-haul, across Canada AND the USA:
  1 Adirondacks colours weekend (NY)          - Mach-E, Oct 2023
  2 Chicago via Michigan (US)                 - EV6,    Jun 2024
  3 Cross-Canada - Ottawa to Banff (ON->AB)   - Mach-E, Jul/Aug 2024, 7,500 km
  4 New England fall colours (VT/NH/ME/MA/NY) - EV6,    Oct 2024
  5 Florida winter escape (I-81/I-95, USD)    - Mach-E, Feb 2025, 4,900 km
  6 Gaspesie loop (QC)                        - EV6,    Jul 2025
  7 Halifax & the Maritimes (QC/NB/NS)        - Mach-E, Aug 2025
  8 Nashville & the Smokies (OH/KY/TN)        - EV6,    Apr 2026

This script imports generate_sample_backup.py for the Builder, the image /
receipt generators and the odometer / trip-window consistency passes, so the
pack keeps every invariant the other packs guarantee (monotonic odometers,
battery end >= start, effective rates and powers within the app's hint
tolerances). It does NOT call base.build() - the dataset here is authored
from scratch.

Run:  python3 generate_sample_backup_roadtrip.py
Out:  evsct-sample-backup-roadtrip.zip  (next to this script)
"""

from __future__ import annotations

import collections
import io
import os
import json
import random
import zipfile
from datetime import datetime

from PIL import Image

import generate_sample_backup as base
from generate_sample_backup import Station

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_ZIP = os.path.join(HERE, "evsct-sample-backup-roadtrip.zip")

# Independent stream; base.rng stays untouched for lat/lon jitter etc.
rrng = random.Random(20260719)


def real_photo(filename: str, fallback: bytes) -> bytes:
    """This pack ships real vehicle photos (vehicle-photo-*.jpg next to this
    script) instead of the generated gradient cards; fall back to the card if
    an asset is missing so the script still runs from a partial checkout."""
    path = os.path.join(HERE, filename)
    if not os.path.exists(path):
        return fallback
    img = Image.open(path).convert("RGB")
    img.thumbnail((1600, 1600), Image.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=87)
    return buf.getvalue()


# --------------------------------------------------------------------------- #
#  Stations
# --------------------------------------------------------------------------- #

# -- Ottawa everyday (the EV6's whole life: no home charger) --
CURB_GLEBE = Station("ChargePoint", "Glebe curbside post", "Ottawa", "ON",
                     "Third Ave & Bank St, Ottawa, ON", 45.4020, -75.6890)
CURB_MACLAREN = Station("FLO", "MacLaren St curbside", "Ottawa", "ON",
                        "MacLaren St & Bank St, Ottawa, ON", 45.4170, -75.6960)
WORK_WEP = Station("ChargePoint", "World Exchange Plaza P2", "Ottawa", "ON",
                   "45 O'Connor St, Ottawa, ON", 45.4210, -75.6980)
RIDEAU_L2 = Station("ChargePoint", "Rideau Centre garage", "Ottawa", "ON",
                    "50 Rideau St, Ottawa, ON", 45.4260, -75.6910)

# -- Trip 2/8 corridor: southern Ontario + Michigan/Midwest --
CAMBRIDGE_IVY = Station("Ivy Charging Network", "ONroute Cambridge North", "Cambridge", "ON",
                        "Highway 401 ONroute, Cambridge, ON", 43.4290, -80.3230)
WOODSTOCK_IVY = Station("Ivy Charging Network", "ONroute Woodstock", "Woodstock", "ON",
                        "Highway 401 ONroute, Woodstock, ON", 43.1420, -80.7550)
TILBURY_IVY = Station("Ivy Charging Network", "ONroute Tilbury North", "Tilbury", "ON",
                      "Highway 401 ONroute, Tilbury, ON", 42.2570, -82.4720)
SARNIA_FLO = Station("FLO", "Lambton Mall", "Sarnia", "ON",
                     "1380 London Rd, Sarnia, ON", 42.9560, -82.3790)
FLINT_EA = Station("Electrify America", "Genesee Valley Center", "Flint", "MI",
                   "3341 S Linden Rd, Flint, MI", 43.0090, -83.7440, "US")
BENTONHARBOR_EA = Station("Electrify America", "Benton Harbor - Meijer", "Benton Harbor", "MI",
                          "1920 Pipestone Rd, Benton Harbor, MI", 42.0680, -86.4400, "US")
CHI_HOTEL = Station("ChargePoint", "Loop hotel garage", "Chicago", "IL",
                    "172 W Madison St, Chicago, IL", 41.8790, -87.6290, "US")
CHI_EA = Station("Electrify America", "Chicago South Loop", "Chicago", "IL",
                 "1101 S Canal St, Chicago, IL", 41.8620, -87.6260, "US")
BATTLECREEK_EA = Station("Electrify America", "Battle Creek - Meijer", "Battle Creek", "MI",
                         "6405 B Dr N, Battle Creek, MI", 42.2650, -85.1820, "US")
TOLEDO_EA = Station("Electrify America", "Franklin Park Mall", "Toledo", "OH",
                    "5001 Monroe St, Toledo, OH", 41.7000, -83.6440, "US")
FINDLAY_TSC = Station("Tesla Supercharger", "Findlay", "Findlay", "OH",
                      "2020 Tiffin Ave, Findlay, OH", 41.0230, -83.6690, "US")
CINCY_EA = Station("Electrify America", "Deerfield Towne Center", "Cincinnati", "OH",
                   "5305 Deerfield Blvd, Mason, OH", 39.2960, -84.3100, "US")
LEX_EA = Station("Electrify America", "Fayette Mall", "Lexington", "KY",
                 "3401 Nicholasville Rd, Lexington, KY", 38.0050, -84.5280, "US")
KNOXVILLE_EA = Station("Electrify America", "Turkey Creek", "Knoxville", "TN",
                       "11693 Parkside Dr, Knoxville, TN", 35.9030, -84.1560, "US")
GATLINBURG_L2 = Station("ChargePoint", "Gatlinburg downtown lot", "Gatlinburg", "TN",
                        "520 Parkway, Gatlinburg, TN", 35.7130, -83.5180, "US")
NASH_EA = Station("Electrify America", "One Bellevue Place", "Nashville", "TN",
                  "7620 Hwy 70 S, Nashville, TN", 36.0770, -86.9430, "US")
LOUISVILLE_EA = Station("Electrify America", "Louisville - Meijer", "Louisville", "KY",
                        "2500 S Hurstbourne Pkwy, Louisville, KY", 38.2400, -85.7580, "US")

# -- Trip 3 corridor: the Trans-Canada (Petro-Canada's Electric Highway) --
NORTHBAY_PC = Station("Petro-Canada EV Fast Charge", "North Bay", "North Bay", "ON",
                      "1120 Lakeshore Dr, North Bay, ON", 46.3060, -79.4610)
SUDBURY_PC = Station("Petro-Canada EV Fast Charge", "Sudbury", "Sudbury", "ON",
                     "2149 Regent St, Sudbury, ON", 46.4670, -80.9860)
SSM_PC = Station("Petro-Canada EV Fast Charge", "Sault Ste. Marie", "Sault Ste. Marie", "ON",
                 "440 Great Northern Rd, Sault Ste. Marie, ON", 46.5330, -84.3080)
WAWA_FLO = Station("FLO", "Wawa - Mission Rd", "Wawa", "ON",
                   "Highway 17 & Mission Rd, Wawa, ON", 47.9930, -84.7730)
MARATHON_FLO = Station("FLO", "Marathon - Peninsula Rd", "Marathon", "ON",
                       "Peninsula Rd, Marathon, ON", 48.7520, -86.3720)
THUNDERBAY_PC = Station("Petro-Canada EV Fast Charge", "Thunder Bay", "Thunder Bay", "ON",
                        "779 Memorial Ave, Thunder Bay, ON", 48.4090, -89.2410)
DRYDEN_FLO = Station("FLO", "Dryden - Government St", "Dryden", "ON",
                     "479 Government St, Dryden, ON", 49.7830, -92.8290)
KENORA_PC = Station("Petro-Canada EV Fast Charge", "Kenora", "Kenora", "ON",
                    "Highway 17 E, Kenora, ON", 49.7660, -94.4640)
WINNIPEG_PC = Station("Petro-Canada EV Fast Charge", "Winnipeg West", "Winnipeg", "MB",
                      "3801 Portage Ave, Winnipeg, MB", 49.8320, -97.2450)
WPG_HOTEL = Station("FLO", "Fort Garry Hotel garage", "Winnipeg", "MB",
                    "222 Broadway, Winnipeg, MB", 49.8890, -97.1350)
BRANDON_PC = Station("Petro-Canada EV Fast Charge", "Brandon", "Brandon", "MB",
                     "1930 18th St N, Brandon, MB", 49.8480, -99.9500)
REGINA_PC = Station("Petro-Canada EV Fast Charge", "Regina South", "Regina", "SK",
                    "4530 Albert St, Regina, SK", 50.4030, -104.6180)
SWIFTCURRENT_PC = Station("Petro-Canada EV Fast Charge", "Swift Current", "Swift Current", "SK",
                          "105 N Service Rd E, Swift Current, SK", 50.2830, -107.7640)
MEDHAT_PC = Station("Petro-Canada EV Fast Charge", "Medicine Hat", "Medicine Hat", "AB",
                    "1601 Trans Canada Way SE, Medicine Hat, AB", 50.0330, -110.6770)
CALGARY_EA = Station("Electrify Canada", "Deerfoot Meadows", "Calgary", "AB",
                     "8180 11 St SE, Calgary, AB", 50.9860, -114.0500)
CANMORE_HOTEL = Station("FLO", "Canmore hotel garage", "Canmore", "AB",
                        "1720 Bow Valley Trail, Canmore, AB", 51.0860, -115.3500)
LAKELOUISE_DC = Station("FLO", "Lake Louise village", "Lake Louise", "AB",
                        "201 Village Rd, Lake Louise, AB", 51.4260, -116.1810)
BANFF_L2 = Station("ChargePoint", "Banff - Bear St parkade", "Banff", "AB",
                   "138 Bear St, Banff, AB", 51.1770, -115.5710)

# -- Trip 4 corridor: New England --
WILLISTON_EA = Station("Electrify America", "Williston - Maple Tree Place", "Williston", "VT",
                       "41 Hawthorne St, Williston, VT", 44.4400, -73.0930, "US")
NCONWAY_L2 = Station("ChargePoint", "North Conway village lot", "North Conway", "NH",
                     "2617 White Mountain Hwy, North Conway, NH", 44.0540, -71.1280, "US")
PORTLAND_EA = Station("Electrify America", "Maine Mall", "Portland", "ME",
                      "364 Maine Mall Rd, South Portland, ME", 43.6350, -70.3350, "US")
PORTLAND_HOTEL = Station("ChargePoint", "Old Port hotel garage", "Portland", "ME",
                         "468 Fore St, Portland, ME", 43.6570, -70.2590, "US")
BOSTON_EVGO = Station("EVgo", "Seaport garage", "Boston", "MA",
                      "88 Seaport Blvd, Boston, MA", 42.3490, -71.0430, "US")
ALBANY_EA = Station("Electrify America", "Crossgates Mall", "Albany", "NY",
                    "1 Crossgates Mall Rd, Albany, NY", 42.6850, -73.8490, "US")
PLATTSBURGH_CP = Station("ChargePoint", "Champlain Centre", "Plattsburgh", "NY",
                         "60 Smithfield Blvd, Plattsburgh, NY", 44.7070, -73.4890, "US")

# -- Trip 5 corridor: I-81 / I-95 to Florida --
WATERTOWN_TSC = Station("Tesla Supercharger", "Watertown", "Watertown", "NY",
                        "1290 Arsenal St, Watertown, NY", 43.9750, -75.9060, "US")
VESTAL_EA = Station("Electrify America", "Vestal - Parkway Plaza", "Vestal", "NY",
                    "2425 Vestal Pkwy E, Vestal, NY", 42.0850, -76.0270, "US")
HARRISBURG_EA = Station("Electrify America", "Union Deposit Rd", "Harrisburg", "PA",
                        "3801 Union Deposit Rd, Harrisburg, PA", 40.2730, -76.8440, "US")
WINCHESTER_EA = Station("Electrify America", "Apple Blossom Mall", "Winchester", "VA",
                        "1850 Apple Blossom Dr, Winchester, VA", 39.1750, -78.1660, "US")
CHRISTIANSBURG_EA = Station("Electrify America", "Uptown Christiansburg", "Christiansburg", "VA",
                            "782 New River Rd NW, Christiansburg, VA", 37.1400, -80.4030, "US")
CHARLOTTE_EA = Station("Electrify America", "Northlake Mall", "Charlotte", "NC",
                       "6801 Northlake Mall Dr, Charlotte, NC", 35.3480, -80.8540, "US")
SANTEE_EA = Station("Electrify America", "Santee", "Santee", "SC",
                    "8919 Old Number Six Hwy, Santee, SC", 33.4860, -80.4760, "US")
SAVANNAH_TSC = Station("Tesla Supercharger", "Savannah", "Savannah", "GA",
                       "1801 E Victory Dr, Savannah, GA", 32.0450, -81.0680, "US")
STAUG_EA = Station("Electrify America", "St. Augustine outlets", "St. Augustine", "FL",
                   "500 Outlet Mall Blvd, St. Augustine, FL", 29.9250, -81.4160, "US")
ORL_HOTEL = Station("ChargePoint", "Universal-area hotel garage", "Orlando", "FL",
                    "5800 Universal Blvd, Orlando, FL", 28.4740, -81.4680, "US")
KSC_L2 = Station("ChargePoint", "Kennedy Space Center lot", "Merritt Island", "FL",
                 "Space Commerce Way, Merritt Island, FL", 28.5230, -80.6830, "US")
DAYTONA_TSC = Station("Tesla Supercharger", "Daytona Beach", "Daytona Beach", "FL",
                      "1900 W International Speedway Blvd, Daytona Beach, FL",
                      29.2280, -81.0930, "US")
FLORENCE_EA = Station("Electrify America", "Magnolia Mall", "Florence", "SC",
                      "2701 David H McLeod Blvd, Florence, SC", 34.2000, -79.8310, "US")

# -- Trip 6/7 corridor: Quebec + the Maritimes --
LEVIS_EC = Station("Electric Circuit", "Galeries Chagnon", "Lévis", "QC",
                   "1200 Boul Alphonse-Desjardins, Lévis, QC", 46.7420, -71.2770)
BOUCHERVILLE_EC = Station("Electric Circuit", "Boucherville - Halte 20", "Boucherville", "QC",
                          "Autoroute 20, Boucherville, QC", 45.5900, -73.4360)
RDL_EC = Station("Electric Circuit", "Rivière-du-Loup", "Rivière-du-Loup", "QC",
                 "299 Boul Armand-Thériault, Rivière-du-Loup, QC", 47.8280, -69.5430)
RIMOUSKI_EC = Station("Electric Circuit", "Rimouski - Colisée", "Rimouski", "QC",
                      "111 2e Rue O, Rimouski, QC", 48.4390, -68.5350)
SADM_EC = Station("Electric Circuit", "Sainte-Anne-des-Monts", "Sainte-Anne-des-Monts", "QC",
                  "90 Boul Sainte-Anne O, Sainte-Anne-des-Monts, QC", 49.1240, -66.4920)
GASPE_EC = Station("Electric Circuit", "Gaspé - Carrefour", "Gaspé", "QC",
                   "39 Montée de Sandy Beach, Gaspé, QC", 48.8330, -64.4870)
PERCE_L2 = Station("Municipal", "Percé - lot du quai", "Percé", "QC",
                   "199 Route 132 O, Percé, QC", 48.5240, -64.2130)
CARLETON_EC = Station("Electric Circuit", "Carleton-sur-Mer", "Carleton-sur-Mer", "QC",
                      "629 Boul Perron, Carleton-sur-Mer, QC", 48.0970, -66.1180)
AMQUI_EC = Station("Electric Circuit", "Amqui", "Amqui", "QC",
                   "49 Boul St-Benoît O, Amqui, QC", 48.4640, -67.4310)
EDMUNDSTON_NBP = Station("NB Power eCharge", "Edmundston", "Edmundston", "NB",
                         "555 Victoria St, Edmundston, NB", 47.3620, -68.3250)
FREDERICTON_NBP = Station("NB Power eCharge", "Fredericton - Regent Mall", "Fredericton", "NB",
                          "1381 Regent St, Fredericton, NB", 45.9440, -66.6560)
MONCTON_NBP = Station("NB Power eCharge", "Moncton - Dieppe", "Moncton", "NB",
                      "477 Paul St, Dieppe, NB", 46.0940, -64.7920)
WOODSTOCKNB_NBP = Station("NB Power eCharge", "Woodstock", "Woodstock", "NB",
                          "115 Connell Rd, Woodstock, NB", 46.1260, -67.5730)
HFX_HOTEL = Station("FLO", "Halifax waterfront hotel garage", "Halifax", "NS",
                    "1919 Upper Water St, Halifax, NS", 44.6480, -63.5850)
PEGGYS_L2 = Station("ChargePoint", "Peggy's Cove visitor lot", "Peggy's Cove", "NS",
                    "109 Peggys Point Rd, Peggy's Cove, NS", 44.4940, -63.9160)


# --------------------------------------------------------------------------- #
#  Session shorthand
# --------------------------------------------------------------------------- #

def D(ts: str) -> datetime:
    return datetime.strptime(ts, "%Y-%m-%d %H:%M")


def K(x):  # posted $/kWh
    return ("kwh", x)


def M(x):  # posted $/min
    return ("min", x)


def FL(x):  # flat fee
    return ("flat", x)


def HY(flat, per_min):  # connection fee + per-minute
    return ("hyb", flat, per_min)


FREE = ("free",)

_last_leg_day: dict = {}


def sess(b, v, ts, st, ctype, km, bs, be, kw, maxkw, price, *, trip=None,
         tags=None, stall=None, wait=None, notes=None, receipts=None,
         cont=None, exact=False):
    """One session. Cross-border currency and road-trip 'usa' tagging are
    derived from the station; `continuesPrevious` defaults to true for the
    second-and-later legs of the same trip on the same calendar day."""
    when = D(ts)
    if trip is not None:
        key = (v, trip)
        if cont is None:
            cont = _last_leg_day.get(key) == when.date()
        _last_leg_day[key] = when.date()
    kind = price[0]
    noise = 0.0 if exact else rrng.uniform(-0.02, 0.02)
    if kind == "kwh":
        kwargs = dict(pricing="PER_KWH", posted_kwh=price[1], eff_noise=noise)
    elif kind == "min":
        kwargs = dict(pricing="PER_MINUTE", posted_min=price[1], eff_noise=noise)
    elif kind == "flat":
        kwargs = dict(pricing="FLAT", flat_cost=price[1])
    elif kind == "hyb":
        kwargs = dict(pricing="HYBRID", flat_cost=price[1], posted_min=price[2])
    else:
        kwargs = dict(pricing="FREE", free=True)
    if st.country == "US" and tags and "usa" not in tags:
        tags = tags + ",usa"
    b.add_session(v, when, st, ctype, drive_km=km, batt_start=bs, batt_end=be,
                  avg_kw=kw, posted_max_kw=maxkw,
                  currency=("USD" if st.country == "US" else "CAD"),
                  trip_id=trip, tags=tags, stall=stall, wait=wait, notes=notes,
                  receipts=receipts, continues=bool(cont), **kwargs)


# --------------------------------------------------------------------------- #
#  Compose the dataset
# --------------------------------------------------------------------------- #

def build() -> base.Builder:
    b = base.Builder()

    # ---- Vehicles -------------------------------------------------------- #
    b.add_vehicle(
        1, "EV6", 2023, "Kia", "EV6", "GT-Line AWD",
        77.4, 441, "KNDC4DLCXP5106742",
        "Downtown condo car — street parking only, so it never charges at "
        "home. Lives on curbside posts, the office garage and DC fast; the "
        "800V pack is what makes that livable.",
        real_photo("vehicle-photo-ev6.jpg",
                   base.vehicle_photo_bytes((32, 33, 38), (208, 158, 42), "EV6",
                                            "2023 Kia · GT-Line AWD")),
        True, datetime(2023, 8, 5, 17, 30), base_odo=6480.0,
    )
    b.add_vehicle(
        2, "Mach-E", 2022, "Ford", "Mustang Mach-E", "Premium AWD Extended Range",
        91.0, 434, "3FMTK3SU5NMA48291",
        "The long-haul road-tripper, garaged in Barrhaven. Home L2 only gets "
        "logged now and then — the flat overnight rate isn't worth tracking — "
        "but every trip charge is in here.",
        real_photo("vehicle-photo-mach-e.jpg",
                   base.vehicle_photo_bytes((44, 12, 18), (168, 34, 48), "Mach-E",
                                            "2022 Ford · Premium AWD ER")),
        False, datetime(2023, 8, 5, 17, 40), base_odo=24310.0,
    )

    # ---- Trips ----------------------------------------------------------- #
    # Odometer windows are recomputed from member sessions afterwards.
    b.add_trip(1, "Adirondacks colours weekend",
               datetime(2023, 10, 6, 8, 30), datetime(2023, 10, 9, 15, 0),
               0.0, 0.0,
               "Lake Placid via the Cornwall crossing — the Mach-E's first "
               "cross-border run.", "ORANGE")
    b.add_trip(2, "Chicago via Michigan",
               datetime(2024, 6, 15, 8, 0), datetime(2024, 6, 21, 16, 0),
               0.0, 0.0,
               "EV6 to Chicago over the Blue Water Bridge: deep-dish, the Bean "
               "and a Cubs game. ~2,700 km.", "CYAN")
    b.add_trip(3, "Cross-Canada — Ottawa to Banff",
               datetime(2024, 7, 20, 8, 0), datetime(2024, 8, 5, 21, 30),
               0.0, 0.0,
               "Seventeen days and 7,500 km out to the Rockies and back on the "
               "Trans-Canada — Petro-Canada's Electric Highway nearly the "
               "whole way.", "RED")
    b.add_trip(4, "New England fall colours",
               datetime(2024, 10, 5, 8, 0), datetime(2024, 10, 9, 21, 0),
               0.0, 0.0,
               "Vermont, the Kancamagus, a lobster roll in Portland, home via "
               "Boston and Albany.", "AZURE")
    b.add_trip(5, "Florida winter escape",
               datetime(2025, 2, 8, 8, 30), datetime(2025, 2, 21, 15, 0),
               0.0, 0.0,
               "Ottawa to Orlando down I-81/I-95 in February. First big test "
               "for the NACS adapter; all sessions south of the border in "
               "USD.", "YELLOW")
    b.add_trip(6, "Gaspésie loop",
               datetime(2025, 7, 12, 8, 0), datetime(2025, 7, 17, 20, 0),
               0.0, 0.0,
               "Around the peninsula — Rocher Percé, Forillon, and Electric "
               "Circuit BRCCs the whole way.", "GREEN")
    b.add_trip(7, "Halifax & the Maritimes",
               datetime(2025, 8, 9, 8, 30), datetime(2025, 8, 15, 18, 30),
               0.0, 0.0,
               "Down the St. Lawrence and across New Brunswick on NB Power's "
               "eCharge network. 3,000 km.", "BLUE")
    b.add_trip(8, "Nashville & the Smokies",
               datetime(2026, 4, 18, 7, 30), datetime(2026, 4, 25, 21, 30),
               0.0, 0.0,
               "Honky-tonks, Newfound Gap and 3,800 km of twang. Includes the "
               "EV6's first Supercharger stop on the NACS adapter.", "VIOLET")

    # ---- Receipts -------------------------------------------------------- #
    rec_chicago = b.make_receipt(
        "jpg", "Electrify America", "Chicago South Loop · Jun 18, 2024",
        [("Session", "DC Fast 350 kW"), ("Energy", "48.9 kWh"),
         ("Rate", "US$0.48 / kWh"), ("Duration", "20 min"), ("Total", "US$23.46")],
        "ea-chicago-2024-06-18.jpg")
    rec_tbay = b.make_receipt(
        "jpg", "Petro-Canada EV Fast Charge", "Thunder Bay, ON · Jul 21, 2024",
        [("Session", "DC Fast"), ("Energy", "73.8 kWh"),
         ("Rate", "$0.62 / kWh"), ("Duration", "54 min"), ("Total", "$45.73")],
        "petrocan-thunderbay-2024-07-21.jpg")
    rec_williston = b.make_receipt(
        "pdf", "Electrify America", "Williston, VT · Oct 5, 2024",
        [("Session", "DC Fast 350 kW"), ("Energy", "57.8 kWh"),
         ("Rate", "US$0.48 / kWh"), ("Duration", "23 min"), ("Total", "US$27.76")],
        "ea-williston-2024-10-05.pdf")
    rec_savannah = b.make_receipt(
        "jpg", "Tesla Supercharger", "Savannah, GA · Feb 10, 2025",
        [("Session", "DC Fast (NACS adapter)"), ("Energy", "36.4 kWh"),
         ("Rate", "US$0.39 / kWh"), ("Duration", "24 min"), ("Total", "US$14.20")],
        "tesla-savannah-2025-02-10.jpg")
    rec_gaspe = b.make_receipt(
        "jpg", "Electric Circuit", "Gaspé, QC · Jul 13, 2025",
        [("Session", "BRCC 100 kW"), ("Duration", "43 min"),
         ("Rate", "$0.32 / min"), ("Energy", "58.7 kWh"), ("Total", "$13.73")],
        "circuit-electrique-gaspe-2025-07-13.jpg")
    rec_moncton = b.make_receipt(
        "pdf", "NB Power eCharge", "Dieppe, NB · Aug 10, 2025",
        [("Session", "DC Fast 50 kW"), ("Duration", "54 min"),
         ("Rate", "$0.30 / min"), ("Energy", "44.1 kWh"), ("Total", "$16.18")],
        "nbpower-moncton-2025-08-10.pdf")
    rec_findlay = b.make_receipt(
        "pdf", "Tesla Supercharger", "Findlay, OH · Apr 24, 2026",
        [("Session", "DC Fast (NACS adapter)"), ("Energy", "55.4 kWh"),
         ("Rate", "US$0.42 / kWh"), ("Duration", "35 min"), ("Total", "US$23.27")],
        "tesla-findlay-2026-04-24.pdf")

    # ===================================================================== #
    #  VEHICLE 1 — Kia EV6: everyday life with NO home charging
    # ===================================================================== #
    v = 1
    CURB, MACL, WORK, RID = CURB_GLEBE, CURB_MACLAREN, WORK_WEP, RIDEAU_L2
    FLO_DC, PC = base.OTT_FLO_DC, base.OTT_PETROCAN
    everyday = [
        # (when, station, type, km, bs, be, kw, maxkw, price, note)
        ("2023-08-09 20:45", CURB, "AC_L2", 96, 41, 96, 6.4, 7.2, K(0.28),
         "First week at the condo — curbside post it is."),
        ("2023-08-26 11:20", FLO_DC, "DC_FAST", 342, 18, 85, 86, 100, K(0.40),
         "Groceries plus 30 minutes of DC — the weekly routine."),
        ("2023-09-19 21:10", MACL, "AC_L2", 388, 27, 92, 6.1, 6.5, K(0.30), None),
        ("2023-10-24 09:05", WORK, "AC_L2", 361, 38, 88, 6.9, 7.2, FREE,
         "Office garage L2 — free, first-come."),
        ("2023-11-15 12:40", PC, "DC_FAST", 402, 16, 84, 118, 200, K(0.62),
         "First cold snap of the season."),
        ("2023-12-20 14:30", RID, "AC_L2", 296, 47, 71, 6.2, 6.6, FL(6.00),
         "Christmas shopping; the garage fee buys the parking anyway."),
        ("2024-01-09 21:00", CURB, "AC_L2", 305, 25, 90, 6.3, 7.2, K(0.28),
         "-18°C and the post still works. Range doesn't."),
        ("2024-02-13 09:10", WORK, "AC_L2", 356, 35, 86, 6.9, 7.2, FREE, None),
        ("2024-03-23 10:50", FLO_DC, "DC_FAST", 411, 20, 88, 88, 100, K(0.40), None),
        ("2024-05-29 09:05", WORK, "AC_L2", 462, 40, 90, 6.9, 7.2, FREE, None),
        ("2024-06-11 20:30", MACL, "AC_L2", 371, 33, 98, 6.1, 6.5, K(0.30),
         "Topping up ahead of the Chicago run."),
        ("2024-07-18 12:15", PC, "DC_FAST", 366, 22, 86, 142, 200, K(0.62), None),
        ("2024-09-14 19:40", CURB, "AC_L2", 415, 30, 94, 6.4, 7.2, K(0.28), None),
        ("2024-10-22 13:20", RID, "AC_L2", 338, 44, 74, 6.2, 6.6, FL(6.00), None),
        ("2024-12-13 12:30", PC, "DC_FAST", 389, 19, 82, 92, 200, K(0.62),
         "Cold-soaked pack — DC well off its summer pace."),
        ("2025-01-21 09:10", WORK, "AC_L2", 344, 36, 85, 6.9, 7.2, FREE, None),
        ("2025-02-15 11:00", FLO_DC, "DC_FAST", 375, 15, 82, 58, 100, K(0.55),
         "-20°C: half the usual charging speed."),
        ("2025-03-18 21:05", CURB, "AC_L2", 356, 31, 95, 6.4, 7.2, K(0.28), None),
        ("2025-05-22 09:00", WORK, "AC_L2", 471, 42, 88, 6.9, 7.2, FREE, None),
        ("2025-07-08 18:45", PC, "DC_FAST", 397, 24, 92, 138, 200, K(0.66),
         "Filling high before Gaspésie."),
        ("2025-08-23 10:40", FLO_DC, "DC_FAST", 348, 21, 86, 87, 100, K(0.55), None),
        ("2025-09-16 20:50", MACL, "AC_L2", 402, 29, 93, 6.1, 6.5, K(0.30), None),
        ("2025-11-22 12:10", PC, "DC_FAST", 419, 17, 84, 104, 200, K(0.66), None),
        ("2026-01-20 11:30", FLO_DC, "DC_FAST", 361, 18, 80, 61, 100, K(0.55),
         "Deep-winter DC. Bring a coffee."),
        ("2026-02-17 09:05", WORK, "AC_L2", 352, 37, 87, 6.9, 7.2, FREE, None),
        ("2026-04-16 20:30", CURB, "AC_L2", 383, 28, 95, 6.4, 7.2, K(0.28),
         "Topped the night before the Nashville run."),
        ("2026-05-20 13:40", RID, "AC_L2", 366, 46, 76, 6.2, 6.6, FL(6.00), None),
        ("2026-06-16 21:00", CURB, "AC_L2", 394, 32, 96, 6.4, 7.2, K(0.28), None),
        ("2026-07-11 10:30", FLO_DC, "DC_FAST", 372, 23, 85, 88, 100, K(0.55),
         "Three years in — still zero home charges."),
    ]
    for ts, st, ctype, km, bs, be, kw, maxkw, price, note in everyday:
        tag = ("work charge,free" if st is WORK
               else "errand" if st in (RID, FLO_DC, PC)
               else "condo life,curbside")
        sess(b, v, ts, st, ctype, km, bs, be, kw, maxkw, price,
             tags=tag, notes=note)

    # ---- Trip 2: Chicago via Michigan (EV6, Jun 2024) ----
    t = 2
    sess(b, v, "2024-06-15 08:50", base.TRENTON_EC, "DC_FAST", 252, 32, 80,
         120, 350, M(0.45), trip=t, tags="road trip", stall="Charger 2",
         notes="Westbound — ONroute breakfast.")
    sess(b, v, "2024-06-15 12:10", CAMBRIDGE_IVY, "DC_FAST", 273, 14, 82,
         135, 150, M(0.33), trip=t, tags="road trip", stall="Charger 1", wait=6)
    sess(b, v, "2024-06-15 15:20", SARNIA_FLO, "DC_FAST", 205, 35, 85,
         82, 100, K(0.40), trip=t, tags="road trip", stall="DC-1",
         notes="Topped high before the Blue Water Bridge.")
    sess(b, v, "2024-06-15 18:40", FLINT_EA, "DC_FAST", 132, 58, 80,
         96, 150, K(0.48), trip=t, tags="road trip", stall="Charger 3",
         notes="Quick splash — Flint overnight.")
    sess(b, v, "2024-06-16 11:40", BENTONHARBOR_EA, "DC_FAST", 328, 12, 85,
         142, 350, K(0.48), trip=t, tags="road trip", stall="Charger 2")
    sess(b, v, "2024-06-16 15:10", CHI_HOTEL, "AC_L2", 168, 48, 95,
         6.2, 6.6, FL(18.00), trip=t, tags="road trip,destination",
         notes="Loop garage overnight — flat USD fee.")
    sess(b, v, "2024-06-18 13:20", CHI_EA, "DC_FAST", 118, 30, 90,
         150, 350, K(0.48), trip=t, tags="road trip", stall="Charger 1",
         notes="South Loop EA after the museum campus.",
         receipts=[rec_chicago], exact=True)
    sess(b, v, "2024-06-20 09:30", BATTLECREEK_EA, "DC_FAST", 275, 20, 85,
         138, 350, K(0.48), trip=t, tags="road trip", stall="Charger 4")
    sess(b, v, "2024-06-20 13:40", TILBURY_IVY, "DC_FAST", 262, 18, 80,
         128, 150, M(0.33), trip=t, tags="road trip", stall="Charger 2",
         notes="Back over the border at Detroit.")
    sess(b, v, "2024-06-20 17:10", WOODSTOCK_IVY, "DC_FAST", 178, 45, 85,
         122, 150, M(0.33), trip=t, tags="road trip", stall="Charger 3")
    sess(b, v, "2024-06-21 10:20", base.TRENTON_EC, "DC_FAST", 253, 28, 85,
         125, 350, M(0.45), trip=t, tags="road trip", stall="Charger 1")
    sess(b, v, "2024-06-21 13:50", CURB_GLEBE, "AC_L2", 249, 40, 90,
         6.4, 7.2, K(0.28), trip=t, tags="road trip,condo life,curbside",
         notes="Unloaded, then straight to the curbside post.")

    # ---- Trip 4: New England fall colours (EV6, Oct 2024) ----
    t = 4
    sess(b, v, "2024-10-05 08:20", base.OTT_PETROCAN, "DC_FAST", 375, 30, 95,
         72, 200, K(0.62), trip=t, tags="road trip", stall="Stall 2",
         notes="No home charger to leave full from — topped high for the border.")
    sess(b, v, "2024-10-05 11:50", WILLISTON_EA, "DC_FAST", 318, 14, 85,
         148, 350, K(0.48), trip=t, tags="road trip", stall="Charger 2",
         receipts=[rec_williston], exact=True)
    sess(b, v, "2024-10-05 15:30", NCONWAY_L2, "AC_L2", 232, 38, 56,
         6.6, 7.2, K(0.30), trip=t, tags="road trip,destination",
         notes="Kancamagus done — L2 while we walked North Conway.")
    sess(b, v, "2024-10-05 18:20", PORTLAND_EA, "DC_FAST", 104, 42, 80,
         132, 350, K(0.48), trip=t, tags="road trip", stall="Charger 1")
    sess(b, v, "2024-10-05 21:10", PORTLAND_HOTEL, "AC_L2", 12, 76, 100,
         6.2, 6.6, FL(8.00), trip=t, tags="road trip,destination",
         notes="Old Port hotel garage, flat fee overnight.")
    sess(b, v, "2024-10-07 12:40", BOSTON_EVGO, "DC_FAST", 185, 55, 85,
         92, 100, K(0.49), trip=t, tags="road trip", stall="Bay 2",
         notes="Seaport garage — lobster roll count: 4.")
    sess(b, v, "2024-10-09 10:30", ALBANY_EA, "DC_FAST", 274, 22, 85,
         150, 350, K(0.48), trip=t, tags="road trip", stall="Charger 3")
    sess(b, v, "2024-10-09 14:50", PLATTSBURGH_CP, "DC_FAST", 262, 25, 80,
         60, 62.5, K(0.35), trip=t, tags="road trip",
         notes="Sleepy ChargePoint pedestal, but it did the job.")
    sess(b, v, "2024-10-09 18:40", CURB_MACLAREN, "AC_L2", 205, 32, 85,
         6.1, 6.5, K(0.30), trip=t, tags="road trip,condo life,curbside",
         notes="Home — curbside around the corner.")

    # ---- Trip 6: Gaspésie loop (EV6, Jul 2025) ----
    t = 6
    sess(b, v, "2025-07-12 08:40", base.DRUMMOND_EC, "DC_FAST", 362, 10, 85,
         84, 100, M(0.32), trip=t, tags="road trip", stall="BRCC 1",
         notes="Early start — Drummondville BRCC breakfast.")
    sess(b, v, "2025-07-12 12:00", base.QC_EC, "DC_FAST", 158, 48, 85,
         82, 100, M(0.32), trip=t, tags="road trip", stall="BRCC 3")
    sess(b, v, "2025-07-12 15:10", RDL_EC, "DC_FAST", 211, 38, 88,
         84, 100, M(0.32), trip=t, tags="road trip", stall="BRCC 2",
         notes="St. Lawrence widening out the window.")
    sess(b, v, "2025-07-13 09:30", RIMOUSKI_EC, "DC_FAST", 108, 62, 90,
         76, 100, M(0.32), trip=t, tags="road trip", stall="BRCC 1")
    sess(b, v, "2025-07-13 13:20", SADM_EC, "DC_FAST", 222, 34, 88,
         83, 100, M(0.32), trip=t, tags="road trip", stall="BRCC 1",
         notes="Haute-Gaspésie — chargers thin out from here.")
    sess(b, v, "2025-07-13 17:30", GASPE_EC, "DC_FAST", 239, 13, 85,
         82, 100, M(0.32), trip=t, tags="road trip", stall="BRCC 2",
         notes="Around Forillon into Gaspé.", receipts=[rec_gaspe], exact=True)
    sess(b, v, "2025-07-14 20:00", PERCE_L2, "AC_L2", 78, 55, 95,
         6.4, 7.2, FREE, trip=t, tags="road trip,destination,free",
         notes="Municipal lot by the quai — Rocher Percé at sunset.")
    sess(b, v, "2025-07-16 11:40", CARLETON_EC, "DC_FAST", 196, 32, 85,
         80, 100, M(0.32), trip=t, tags="road trip", stall="BRCC 1",
         notes="Baie des Chaleurs side.")
    sess(b, v, "2025-07-16 15:20", AMQUI_EC, "DC_FAST", 183, 40, 82,
         78, 100, M(0.32), trip=t, tags="road trip", stall="BRCC 1",
         notes="Up the Matapédia valley.")
    sess(b, v, "2025-07-16 18:10", RDL_EC, "DC_FAST", 188, 38, 90,
         83, 100, M(0.32), trip=t, tags="road trip", stall="BRCC 1")
    sess(b, v, "2025-07-17 12:30", base.DRUMMOND_EC, "DC_FAST", 305, 14, 92,
         85, 100, M(0.32), trip=t, tags="road trip", stall="BRCC 2")
    sess(b, v, "2025-07-17 16:40", base.CASSELMAN_IVY, "DC_FAST", 265, 22, 70,
         120, 150, M(0.45), trip=t, tags="road trip", stall="Charger 2")
    sess(b, v, "2025-07-17 18:50", CURB_GLEBE, "AC_L2", 100, 62, 90,
         6.4, 7.2, K(0.28), trip=t, tags="road trip,condo life,curbside",
         notes="Back to the condo curb.")

    # ---- Trip 8: Nashville & the Smokies (EV6, Apr 2026) ----
    t = 8
    sess(b, v, "2026-04-18 08:20", base.TRENTON_EC, "DC_FAST", 265, 38, 80,
         128, 350, M(0.57), trip=t, tags="road trip", stall="Charger 4",
         notes="Rolling out for Nashville — ONroute breakfast.")
    sess(b, v, "2026-04-18 11:30", CAMBRIDGE_IVY, "DC_FAST", 273, 18, 85,
         132, 150, M(0.45), trip=t, tags="road trip", stall="Charger 2")
    sess(b, v, "2026-04-18 14:40", TILBURY_IVY, "DC_FAST", 262, 22, 80,
         125, 150, M(0.45), trip=t, tags="road trip", stall="Charger 1")
    sess(b, v, "2026-04-18 17:40", TOLEDO_EA, "DC_FAST", 131, 52, 85,
         138, 350, K(0.56), trip=t, tags="road trip", stall="Charger 2",
         notes="Across the Ambassador Bridge — push day, Toledo overnight.")
    sess(b, v, "2026-04-19 12:20", CINCY_EA, "DC_FAST", 322, 8, 85,
         140, 350, K(0.56), trip=t, tags="road trip", stall="Charger 1",
         notes="Sweated the last 30 km into Cincinnati.")
    sess(b, v, "2026-04-19 15:10", LEX_EA, "DC_FAST", 142, 55, 88,
         136, 350, K(0.56), trip=t, tags="road trip", stall="Charger 3")
    sess(b, v, "2026-04-19 18:30", KNOXVILLE_EA, "DC_FAST", 278, 25, 80,
         138, 350, K(0.56), trip=t, tags="road trip", stall="Charger 2")
    sess(b, v, "2026-04-20 17:30", GATLINBURG_L2, "AC_L2", 88, 55, 90,
         6.6, 7.2, K(0.30), trip=t, tags="road trip,destination",
         notes="Downtown lot while we walked the strip.")
    sess(b, v, "2026-04-22 10:40", KNOXVILLE_EA, "DC_FAST", 121, 45, 85,
         135, 350, K(0.56), trip=t, tags="road trip", stall="Charger 1",
         notes="Back through the park — Newfound Gap was clear.")
    sess(b, v, "2026-04-22 14:00", NASH_EA, "DC_FAST", 289, 22, 85,
         137, 350, K(0.56), trip=t, tags="road trip", stall="Charger 2",
         notes="Broadway honky-tonks tonight.")
    sess(b, v, "2026-04-24 09:20", LOUISVILLE_EA, "DC_FAST", 278, 24, 88,
         139, 350, K(0.56), trip=t, tags="road trip", stall="Charger 1")
    sess(b, v, "2026-04-24 12:50", CINCY_EA, "DC_FAST", 161, 52, 90,
         130, 350, K(0.56), trip=t, tags="road trip", stall="Charger 4")
    sess(b, v, "2026-04-24 17:10", FINDLAY_TSC, "DC_FAST", 316, 12, 80,
         96, 250, K(0.42), trip=t, tags="road trip,supercharger",
         stall="Stall 5", receipts=[rec_findlay], exact=True,
         notes="First Supercharger in the Kia — the V3 caps the 800V pack "
               "near 100 kW.")
    sess(b, v, "2026-04-25 10:10", TILBURY_IVY, "DC_FAST", 201, 42, 88,
         126, 150, M(0.45), trip=t, tags="road trip", stall="Charger 3")
    sess(b, v, "2026-04-25 13:20", WOODSTOCK_IVY, "DC_FAST", 180, 50, 85,
         124, 150, M(0.45), trip=t, tags="road trip", stall="Charger 2")
    sess(b, v, "2026-04-25 16:40", base.TRENTON_EC, "DC_FAST", 250, 28, 88,
         130, 350, M(0.57), trip=t, tags="road trip", stall="Charger 2")
    sess(b, v, "2026-04-25 20:50", CURB_GLEBE, "AC_L2", 253, 30, 85,
         6.4, 7.2, K(0.28), trip=t, tags="road trip,condo life,curbside",
         notes="Home — 3,800 km of twang.")

    # ===================================================================== #
    #  VEHICLE 2 — Ford Mustang Mach-E: sparse home log + the big hauls
    # ===================================================================== #
    v = 2
    for ts, km, bs, be, note in [
        ("2023-09-12 21:40", 410, 28, 90, None),
        ("2024-01-16 22:10", 385, 24, 100, "-20°C — preheating every morning."),
        ("2024-05-21 21:30", 460, 35, 85, None),
        ("2024-09-10 22:00", 432, 30, 90, None),
        ("2024-12-03 21:50", 355, 26, 95, "Snow tires on, range down."),
        ("2025-03-11 22:20", 402, 31, 85, None),
        ("2025-10-14 21:35", 488, 27, 90, None),
        ("2026-06-16 22:05", 415, 33, 90, None),
    ]:
        sess(b, v, ts, base.HOME, "AC_L2", km, bs, be,
             9.8 * rrng.uniform(0.98, 1.02), 11.5, K(0.103),
             tags="home", notes=note)
    sess(b, v, "2024-04-06 12:40", base.OTT_PETROCAN, "DC_FAST", 260, 21, 80,
         87, 200, K(0.62), tags="errand", stall="Stall 1",
         notes="IKEA-day splash.")
    sess(b, v, "2025-06-07 13:30", base.OTT_CHARGEPOINT, "AC_L2", 190, 48, 76,
         6.9, 7.7, FREE, tags="free,errand",
         notes="Free L2 during the Lansdowne market.")

    # ---- Trip 1: Adirondacks colours weekend (Mach-E, Oct 2023) ----
    t = 1
    sess(b, v, "2023-10-06 09:10", base.CIRCLEK_CORNWALL, "DC_FAST", 108, 58, 85,
         95, 180, HY(1.00, 0.30), trip=t, tags="road trip", stall="DC-1",
         notes="Border top-up — Circle K's $1 connection + per-minute.")
    sess(b, v, "2023-10-06 16:30", base.LAKEPLACID_L2, "AC_L2", 148, 42, 90,
         6.8, 7.7, FL(5.00), trip=t, tags="road trip,destination",
         notes="Municipal lot by the Olympic oval.")
    sess(b, v, "2023-10-08 18:45", base.LAKEPLACID_L2, "AC_L2", 96, 55, 95,
         6.8, 7.7, FL(5.00), trip=t, tags="road trip,destination",
         notes="Whiteface and Keene Valley day.")
    sess(b, v, "2023-10-09 12:20", base.CIRCLEK_CORNWALL, "DC_FAST", 155, 35, 75,
         90, 180, HY(1.00, 0.30), trip=t, tags="road trip", stall="DC-1",
         notes="Back over the bridge — hello Canada.")

    # ---- Trip 3: Cross-Canada — Ottawa to Banff (Mach-E, Jul/Aug 2024) ----
    t = 3
    sess(b, v, "2024-07-20 10:20", NORTHBAY_PC, "DC_FAST", 363, 10, 85,
         84, 200, K(0.62), trip=t, tags="road trip", stall="Stall 1",
         notes="Day one — 363 km nonstop to North Bay.")
    sess(b, v, "2024-07-20 13:00", SUDBURY_PC, "DC_FAST", 127, 55, 90,
         68, 200, K(0.62), trip=t, tags="road trip", stall="Stall 2",
         notes="Lunch in Sudbury — Big Nickel obligatory.")
    sess(b, v, "2024-07-20 17:10", SSM_PC, "DC_FAST", 300, 22, 85,
         86, 200, K(0.62), trip=t, tags="road trip", stall="Stall 1")
    sess(b, v, "2024-07-21 10:00", WAWA_FLO, "DC_FAST", 228, 33, 85,
         47, 50, K(0.40), trip=t, tags="road trip", stall="DC",
         notes="50 kW crawl — goose photos while we waited.")
    sess(b, v, "2024-07-21 13:40", MARATHON_FLO, "DC_FAST", 181, 46, 82,
         46, 50, K(0.40), trip=t, tags="road trip", stall="DC",
         notes="Lake Superior around every bend.")
    sess(b, v, "2024-07-21 17:50", THUNDERBAY_PC, "DC_FAST", 301, 13, 90,
         82, 200, K(0.62), trip=t, tags="road trip", stall="Stall 2",
         notes="Terry Fox lookout first, then the longest charge of the day.",
         receipts=[rec_tbay], exact=True)
    sess(b, v, "2024-07-22 11:30", DRYDEN_FLO, "DC_FAST", 344, 11, 85,
         78, 100, K(0.40), trip=t, tags="road trip", stall="DC-1")
    sess(b, v, "2024-07-22 14:10", KENORA_PC, "DC_FAST", 141, 54, 82,
         74, 200, K(0.62), trip=t, tags="road trip", stall="Stall 1")
    sess(b, v, "2024-07-22 17:00", WINNIPEG_PC, "DC_FAST", 209, 36, 85,
         85, 200, K(0.62), trip=t, tags="road trip", stall="Stall 2",
         notes="Prairies unlocked.")
    sess(b, v, "2024-07-22 21:40", WPG_HOTEL, "AC_L2", 12, 80, 100,
         9.4, 10.9, FREE, trip=t, tags="road trip,destination,free",
         notes="Fort Garry garage — two nights in Winnipeg.")
    sess(b, v, "2024-07-24 11:00", BRANDON_PC, "DC_FAST", 216, 44, 95,
         72, 200, K(0.62), trip=t, tags="road trip", stall="Stall 1",
         notes="Prairie cruise control.")
    sess(b, v, "2024-07-24 16:30", REGINA_PC, "DC_FAST", 355, 13, 85,
         84, 200, K(0.62), trip=t, tags="road trip", stall="Stall 2")
    sess(b, v, "2024-07-25 10:40", SWIFTCURRENT_PC, "DC_FAST", 246, 29, 82,
         83, 200, K(0.62), trip=t, tags="road trip", stall="Stall 1")
    sess(b, v, "2024-07-25 13:30", MEDHAT_PC, "DC_FAST", 224, 31, 85,
         84, 200, K(0.62), trip=t, tags="road trip", stall="Stall 1")
    sess(b, v, "2024-07-25 17:20", CALGARY_EA, "DC_FAST", 292, 16, 80,
         89, 350, M(0.45), trip=t, tags="road trip", stall="Charger 2", wait=5)
    sess(b, v, "2024-07-25 19:30", CANMORE_HOTEL, "AC_L2", 107, 56, 100,
         6.2, 6.6, FREE, trip=t, tags="road trip,destination,free",
         notes="Mountains! Hotel L2 overnight.")
    sess(b, v, "2024-07-27 11:20", LAKELOUISE_DC, "DC_FAST", 131, 58, 85,
         45, 50, K(0.40), trip=t, tags="road trip", stall="DC",
         notes="Moraine Lake shuttle day.")
    sess(b, v, "2024-07-29 14:40", BANFF_L2, "AC_L2", 63, 68, 94,
         6.9, 7.2, FL(5.00), trip=t, tags="road trip,destination",
         notes="Bear St parkade while we hiked Tunnel Mountain.")
    sess(b, v, "2024-08-01 13:10", MEDHAT_PC, "DC_FAST", 399, 5, 90,
         83, 200, K(0.62), trip=t, tags="road trip", stall="Stall 2",
         notes="Steady 100 with a tailwind — rolled in at 5%.")
    sess(b, v, "2024-08-01 16:20", SWIFTCURRENT_PC, "DC_FAST", 224, 38, 85,
         84, 200, K(0.62), trip=t, tags="road trip", stall="Stall 2")
    sess(b, v, "2024-08-01 19:10", REGINA_PC, "DC_FAST", 246, 32, 85,
         85, 200, K(0.62), trip=t, tags="road trip", stall="Stall 1")
    sess(b, v, "2024-08-02 11:30", BRANDON_PC, "DC_FAST", 355, 10, 90,
         82, 200, K(0.62), trip=t, tags="road trip", stall="Stall 2")
    sess(b, v, "2024-08-02 14:40", WINNIPEG_PC, "DC_FAST", 216, 40, 80,
         86, 200, K(0.62), trip=t, tags="road trip", stall="Stall 1")
    sess(b, v, "2024-08-02 17:30", KENORA_PC, "DC_FAST", 209, 32, 88,
         75, 200, K(0.62), trip=t, tags="road trip", stall="Stall 2")
    sess(b, v, "2024-08-03 10:50", DRYDEN_FLO, "DC_FAST", 141, 52, 85,
         77, 100, K(0.40), trip=t, tags="road trip", stall="DC-1")
    sess(b, v, "2024-08-03 15:00", THUNDERBAY_PC, "DC_FAST", 344, 9, 90,
         84, 200, K(0.62), trip=t, tags="road trip", stall="Stall 1")
    sess(b, v, "2024-08-04 10:10", MARATHON_FLO, "DC_FAST", 301, 16, 82,
         46, 50, K(0.40), trip=t, tags="road trip", stall="DC",
         notes="Superior again — still enormous.")
    sess(b, v, "2024-08-04 13:50", WAWA_FLO, "DC_FAST", 181, 42, 85,
         47, 50, K(0.40), trip=t, tags="road trip", stall="DC")
    sess(b, v, "2024-08-04 18:00", SSM_PC, "DC_FAST", 228, 34, 85,
         85, 200, K(0.62), trip=t, tags="road trip", stall="Stall 2")
    sess(b, v, "2024-08-05 10:30", SUDBURY_PC, "DC_FAST", 300, 20, 85,
         86, 200, K(0.62), trip=t, tags="road trip", stall="Stall 1")
    sess(b, v, "2024-08-05 12:50", NORTHBAY_PC, "DC_FAST", 127, 57, 90,
         70, 200, K(0.62), trip=t, tags="road trip", stall="Stall 2")
    sess(b, v, "2024-08-05 20:40", base.HOME, "AC_L2", 363, 12, 90,
         9.8, 11.5, K(0.103), trip=t, tags="home,road trip",
         notes="Home — 7,500 km, zero drama.")

    # ---- Trip 5: Florida winter escape (Mach-E, Feb 2025) ----
    t = 5
    sess(b, v, "2025-02-08 09:00", WATERTOWN_TSC, "DC_FAST", 178, 36, 90,
         88, 250, K(0.42), trip=t, tags="road trip,supercharger", stall="Stall 3",
         notes="First Supercharger through the NACS adapter — plug-and-charge "
               "just worked.")
    sess(b, v, "2025-02-08 13:10", VESTAL_EA, "DC_FAST", 249, 9, 85,
         86, 350, K(0.56), trip=t, tags="road trip", stall="Charger 2", wait=8,
         notes="-15°C and a queue.")
    sess(b, v, "2025-02-08 17:00", HARRISBURG_EA, "DC_FAST", 271, 11, 90,
         84, 350, K(0.56), trip=t, tags="road trip", stall="Charger 1")
    sess(b, v, "2025-02-09 10:20", WINCHESTER_EA, "DC_FAST", 182, 41, 95,
         76, 350, K(0.56), trip=t, tags="road trip", stall="Charger 3")
    sess(b, v, "2025-02-09 14:40", CHRISTIANSBURG_EA, "DC_FAST", 318, 12, 88,
         85, 350, K(0.56), trip=t, tags="road trip", stall="Charger 2")
    sess(b, v, "2025-02-09 18:20", CHARLOTTE_EA, "DC_FAST", 289, 17, 80,
         88, 350, K(0.56), trip=t, tags="road trip", stall="Charger 1")
    sess(b, v, "2025-02-10 10:10", SANTEE_EA, "DC_FAST", 272, 20, 80,
         87, 350, K(0.56), trip=t, tags="road trip", stall="Charger 2",
         notes="Finally above freezing.")
    sess(b, v, "2025-02-10 13:50", SAVANNAH_TSC, "DC_FAST", 179, 37, 75,
         92, 250, K(0.39), trip=t, tags="road trip,supercharger", stall="Stall 8",
         receipts=[rec_savannah], exact=True)
    sess(b, v, "2025-02-10 17:40", STAUG_EA, "DC_FAST", 281, 12, 85,
         86, 350, K(0.56), trip=t, tags="road trip", stall="Charger 4")
    sess(b, v, "2025-02-10 20:40", ORL_HOTEL, "AC_L2", 179, 44, 100,
         6.2, 6.6, FL(15.00), trip=t, tags="road trip,destination",
         notes="Universal-area hotel garage — flat $15 a night.")
    sess(b, v, "2025-02-13 11:30", KSC_L2, "AC_L2", 118, 62, 85,
         6.5, 7.2, K(0.30), trip=t, tags="road trip,destination",
         notes="Kennedy Space Center lot during the tour.")
    sess(b, v, "2025-02-18 09:40", DAYTONA_TSC, "DC_FAST", 152, 40, 95,
         90, 250, K(0.39), trip=t, tags="road trip,supercharger", stall="Stall 1",
         notes="Homeward — speedway first, though.")
    sess(b, v, "2025-02-18 14:10", SAVANNAH_TSC, "DC_FAST", 335, 12, 90,
         89, 250, K(0.39), trip=t, tags="road trip,supercharger", stall="Stall 2")
    sess(b, v, "2025-02-18 17:50", FLORENCE_EA, "DC_FAST", 264, 22, 85,
         85, 350, K(0.56), trip=t, tags="road trip", stall="Charger 3")
    sess(b, v, "2025-02-19 11:00", CHARLOTTE_EA, "DC_FAST", 232, 35, 92,
         86, 350, K(0.56), trip=t, tags="road trip", stall="Charger 2")
    sess(b, v, "2025-02-19 15:30", CHRISTIANSBURG_EA, "DC_FAST", 289, 22, 92,
         84, 350, K(0.56), trip=t, tags="road trip", stall="Charger 1")
    sess(b, v, "2025-02-20 10:40", HARRISBURG_EA, "DC_FAST", 388, 6, 90,
         83, 350, K(0.56), trip=t, tags="road trip", stall="Charger 2",
         notes="Long cold leg over the mountains — rolled in at 6%.")
    sess(b, v, "2025-02-20 15:10", VESTAL_EA, "DC_FAST", 271, 15, 92,
         83, 350, K(0.56), trip=t, tags="road trip", stall="Charger 1")
    sess(b, v, "2025-02-21 10:20", WATERTOWN_TSC, "DC_FAST", 249, 15, 85,
         90, 250, K(0.42), trip=t, tags="road trip,supercharger", stall="Stall 6")
    sess(b, v, "2025-02-21 13:40", base.HOME, "AC_L2", 178, 40, 90,
         9.8, 11.5, K(0.103), trip=t, tags="home,road trip",
         notes="Home — 4,900 km round trip, minus one blizzard.")

    # ---- Trip 7: Halifax & the Maritimes (Mach-E, Aug 2025) ----
    t = 7
    sess(b, v, "2025-08-09 09:10", BOUCHERVILLE_EC, "DC_FAST", 198, 45, 85,
         82, 100, M(0.32), trip=t, tags="road trip", stall="BRCC 1")
    sess(b, v, "2025-08-09 12:30", LEVIS_EC, "DC_FAST", 238, 30, 90,
         84, 100, M(0.32), trip=t, tags="road trip", stall="BRCC 2")
    sess(b, v, "2025-08-09 16:10", RDL_EC, "DC_FAST", 205, 38, 88,
         83, 100, M(0.32), trip=t, tags="road trip", stall="BRCC 1")
    sess(b, v, "2025-08-09 19:20", EDMUNDSTON_NBP, "DC_FAST", 121, 58, 85,
         47, 50, M(0.30), trip=t, tags="road trip", stall="DC-1",
         notes="NB Power eCharge — supper next door while the 50 kW worked.")
    sess(b, v, "2025-08-10 11:00", FREDERICTON_NBP, "DC_FAST", 275, 15, 85,
         48, 50, M(0.30), trip=t, tags="road trip", stall="DC-1")
    sess(b, v, "2025-08-10 14:50", MONCTON_NBP, "DC_FAST", 179, 42, 88,
         49, 50, M(0.30), trip=t, tags="road trip", stall="DC-2",
         receipts=[rec_moncton], exact=True)
    sess(b, v, "2025-08-10 18:40", HFX_HOTEL, "AC_L2", 262, 18, 100,
         6.3, 6.6, HY(2.00, 0.02), trip=t, tags="road trip,destination",
         notes="Hotel garage: $2 connection plus pennies a minute, two nights.")
    sess(b, v, "2025-08-12 13:40", PEGGYS_L2, "AC_L2", 96, 70, 92,
         6.4, 7.2, FREE, trip=t, tags="road trip,destination,free",
         notes="Tour-lot L2 at Peggy's Cove.")
    sess(b, v, "2025-08-14 10:20", MONCTON_NBP, "DC_FAST", 268, 14, 90,
         48, 50, M(0.30), trip=t, tags="road trip", stall="DC-1")
    sess(b, v, "2025-08-14 14:40", WOODSTOCKNB_NBP, "DC_FAST", 297, 12, 88,
         47, 50, M(0.30), trip=t, tags="road trip", stall="DC-1",
         notes="Saint John River valley all afternoon.")
    sess(b, v, "2025-08-14 18:50", RDL_EC, "DC_FAST", 191, 35, 90,
         82, 100, M(0.32), trip=t, tags="road trip", stall="BRCC 2")
    sess(b, v, "2025-08-15 11:10", base.DRUMMOND_EC, "DC_FAST", 305, 15, 85,
         84, 100, M(0.32), trip=t, tags="road trip", stall="BRCC 1")
    sess(b, v, "2025-08-15 15:00", base.CASSELMAN_IVY, "DC_FAST", 262, 20, 70,
         95, 150, M(0.45), trip=t, tags="road trip", stall="Charger 1")
    sess(b, v, "2025-08-15 17:30", base.HOME, "AC_L2", 103, 55, 90,
         9.8, 11.5, K(0.103), trip=t, tags="home,road trip",
         notes="Wheels home — 3,000 km of shoreline.")

    # Resolve odometers chronologically, then trip windows from members.
    base._compute_odometers(b)
    base._fill_trip_odometers(b)
    return b


# --------------------------------------------------------------------------- #
#  Invariant checks (same guarantees the other packs promise)
# --------------------------------------------------------------------------- #

def verify(b: base.Builder) -> dict:
    per_vehicle = collections.Counter(s["vehicleId"] for s in b.sessions)
    for vid, n in per_vehicle.items():
        assert 40 <= n <= 80, f"vehicle {vid} has {n} sessions (want 40-80)"
    # The EV6 (vehicle 1) must never charge at home.
    assert not any(s["brand"] == "Home" for s in b.sessions if s["vehicleId"] == 1), \
        "EV6 must have zero home sessions"
    byv = collections.defaultdict(list)
    for s in b.sessions:
        byv[s["vehicleId"]].append(s)
    for vid, lst in byv.items():
        lst.sort(key=lambda s: s["sessionStart"])
        prev = 0.0
        for s in lst:
            assert s["odometerKm"] > prev, f"odometer regressed (session {s['id']})"
            prev = s["odometerKm"]
    for s in b.sessions:
        assert s["batteryEndPct"] >= s["batteryStartPct"], s["id"]
        e, d = s["energyKwh"], s["durationSeconds"]
        if e and d and s["postedMaxPowerKw"]:
            assert e / (d / 3600.0) <= s["postedMaxPowerKw"] * 1.001, s["id"]
        if s["postedEnergyPricePerKwh"] and e:
            ratio = (s["totalCost"] / e) / s["postedEnergyPricePerKwh"]
            assert 0.75 <= ratio <= 1.25, (s["id"], ratio)
        if s["postedTimeRatePerMin"] and d and s["pricingModel"] == "PER_MINUTE":
            ratio = (s["totalCost"] / (d / 60.0)) / s["postedTimeRatePerMin"]
            assert 0.75 <= ratio <= 1.25, (s["id"], ratio)
    return dict(per_vehicle)


# --------------------------------------------------------------------------- #
#  Write the zip
# --------------------------------------------------------------------------- #

def main():
    b = build()
    per_vehicle = verify(b)

    payload = b.backup_json()
    payload["exportedAt"] = base.millis(datetime(2026, 7, 19, 9, 0))
    js = json.dumps(payload, indent=2, ensure_ascii=False)

    with zipfile.ZipFile(OUT_ZIP, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("backup.json", js)
        for path, data in b.media.items():
            z.writestr(path, data)

    usd = sum(1 for s in b.sessions if s["currency"] == "USD")
    print(f"Wrote {OUT_ZIP}")
    print(f"  vehicles : {len(b.vehicles)}")
    print(f"  trips    : {len(b.trips)}")
    print(f"  sessions : {len(b.sessions)}  per-vehicle={per_vehicle}  ({usd} in USD)")
    print(f"  media    : {len(b.media)} files "
          f"({sum(1 for k in b.media if k.startswith('vehicles/'))} photos, "
          f"{sum(1 for k in b.media if k.startswith('receipts/'))} receipts)")
    print(f"  zip size : {os.path.getsize(OUT_ZIP):,} bytes")


if __name__ == "__main__":
    main()
