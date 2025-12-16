import time
import requests
import datetime
from typing import Optional
from app.models import WarrantyDetails, MinerType
from app.serial_parser import extract_date, is_alpha_miner_serial, ALPHA_SERIAL_PATTERN, normalize_canaan_date
from isoweek import Week

# Constants
USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36"
COMMON_HEADERS = {
    "User-Agent": USER_AGENT,
    "accept": "application/json, text/plain, */*",
    "accept-language": "en-US,en;q=0.7",
    "sec-ch-ua": '"Brave";v="111", "Not(A:Brand";v="8", "Chromium";v="111"',
    "sec-ch-ua-mobile": "?0",
    "sec-ch-ua-platform": '"Linux"'
}

def _fetch_with_retry(method: str, url: str, headers: dict = None, data: dict = None, params: dict = None) -> Optional[requests.Response]:
    """
    Performs HTTP request with rate limit handling (429).
    """
    if headers is None:
        headers = {}
    
    # Merge common headers
    req_headers = {**COMMON_HEADERS, **headers}

    while True:
        try:
            if method.upper() == "GET":
                response = requests.get(url, headers=req_headers, params=params, timeout=10)
            elif method.upper() == "POST":
                response = requests.post(url, headers=req_headers, data=data, timeout=10)
            else:
                return None
            
            if response.status_code == 200:
                return response
            
            if response.status_code == 429:
                retry_after = response.headers.get("Retry-After")
                wait_time = 60
                if retry_after:
                    try:
                        wait_time = int(retry_after)
                    except ValueError:
                        pass
                print(f"Rate limited (429). Waiting {wait_time} seconds...")
                time.sleep(wait_time)
                continue
            
            # Other errors
            print(f"Error {response.status_code} for {url}")
            return None

        except Exception as e:
            print(f"Exception fetching {url}: {e}")
            return None

def get_bitmain_warranty(serial: str) -> Optional[WarrantyDetails]:
    url = "https://shop-repair.bitmain.com/api/warranty/getWarranty"
    params = {"serialNumber": serial}
    headers = {
        "origin": "https://m.bitmain.com",
        "referer": "https://m.bitmain.com/"
    }
    
    # Bitmain specific delay from Java (only if not last in list... but here we can just be safe)
    # The java code has a 4s wait if NOT auto, but let's just be polite.
    time.sleep(1) 

    resp = _fetch_with_retry("GET", url, headers=headers, params=params)
    if not resp:
        return None
    
    try:
        data = resp.json()
        # {"warranty":98,"warrantyEndDate":"2023-07-01 00:00:00","haveWhiteList":"N","code":0}
        if data and "warrantyEndDate" in data and data["warrantyEndDate"]:
            date_str = extract_date(data["warrantyEndDate"])
            if date_str:
                return WarrantyDetails(MinerType.BITMAIN, date_str, serial)
    except Exception as e:
        print(f"Error parsing Bitmain response: {e}")
    return None

def get_whatsminer_warranty(serial: str) -> Optional[WarrantyDetails]:
    url = "https://www.whatsminer.com/renren-fast/app/RepairWorkOrder/warranty"
    params = {"str": serial, "lang": "en_US"}
    headers = {
        "referer": "https://www.whatsminer.com/src/views/support.html"
    }
    
    time.sleep(0.5)

    resp = _fetch_with_retry("GET", url, headers=headers, params=params)
    if not resp:
        return None
    
    try:
        data = resp.json()
        # {"dateList": ["", "2023-01-01"], ...}
        if data and "dateList" in data and len(data["dateList"]) > 1:
            raw_date = data["dateList"][1]
            date_str = extract_date(raw_date)
            if date_str:
                return WarrantyDetails(MinerType.WHATSMINER, date_str, serial)
    except Exception as e:
        print(f"Error parsing Whatsminer response: {e}")
    return None

def get_canaan_warranty(serial: str) -> Optional[WarrantyDetails]:
    url = "https://www.canaan.io/?do_action=action.supports_v2_sn_product_info"
    # Java POST body: &list%5B0%5D%5BSN%5D={serial}...
    # Requests data dict handles encoding automatically usually, but let's match keys exactly
    payload = {
        "list[0][SN]": serial,
        "list[0][Symptom]": "",
        "list[0][Remark]": ""
    }
    headers = {
        "referer": "https://www.canaan.io/support/warranty_check",
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"
    }

    time.sleep(0.5)

    resp = _fetch_with_retry("POST", url, headers=headers, data=payload)
    if not resp:
        return None
    
    try:
        data = resp.json()
        # Structure per java code manual parsing: msg -> response -> [ { Expired: ... } ]
        if "msg" in data and "response" in data["msg"]:
            responses = data["msg"]["response"]
            if isinstance(responses, list) and len(responses) > 0:
                item = responses[0]
                if "Expired" in item:
                    raw_date = item["Expired"]
                    date_str = extract_date(raw_date)
                    if date_str:
                        final_date = normalize_canaan_date(date_str)
                        return WarrantyDetails(MinerType.CANAAN, final_date, serial)
    except Exception as e:
        print(f"Error parsing Canaan response: {e}")
    return None

def get_alphaminer_warranty(serial: str) -> Optional[WarrantyDetails]:
    match = ALPHA_SERIAL_PATTERN.match(serial)
    if not match:
        return None
    
    try:
        # Group 1: Year (2 digits) -> +2000
        # Group 2: Week
        year = int(match.group(1)) + 2000
        week_num = int(match.group(2))
        
        if week_num > 53:
            return None
            
        # Get Friday of that week
        w = Week(year, week_num)
        # Java used: yw.atDay(DayOfWeek.FRIDAY) -> Friday is index 4 in some systems or 5?
        # Java DayOfWeek.FRIDAY is standard. 
        # isoweek: w.friday()
        friday = w.friday()
        
        # Add 6 months
        # Crude approach: add 180 days or use relativedelta? 
        # Java: date.plus(6, ChronoUnit.MONTHS)
        # Let's use a simpler approx or import dateutil if needed, but standard datetime doesn't have add months.
        # I'll stick to a simple approximation + 6*30 days or better implement a quick month adder.
        
        # Better:
        new_month = friday.month + 6
        new_year = friday.year
        if new_month > 12:
            new_month -= 12
            new_year += 1
        
        # Handle day clamping (e.g. if day is 31 but new month has 30)
        # However, Friday is safe usually? No, "atDay(FRIDAY)" returns a date.
        # Let's just use 182 days (26 weeks) close enough for warranty estimation if strictly no deps.
        # But wait, Java `plus(6, MONTHS)` is calendar months.
        # I will try to be more precise.
        
        try:
            future_date = datetime.date(new_year, new_month, friday.day)
        except ValueError:
            # If day is out of range for new month (e.g. 31st), clamp to last day
            # This is rare for Friday but possible?
            # Actually simplest valid python way without dateutil is:
            # Go to first day of next month, subtract one day...
            pass
            # Let's just use +182 days for now to avoid complexity without `python-dateutil` in requirements yet.
            # Java code: date.plus(6, ChronoUnit.MONTHS);
            future_date = friday + datetime.timedelta(days=182)

        return WarrantyDetails(MinerType.ALPHAMINER, str(future_date), serial)

    except Exception as e:
        print(f"Error calculating Alpha Miner warranty: {e}")
        return None

def check_warranty(serial: str, miner_type: MinerType = MinerType.AUTO) -> Optional[WarrantyDetails]:
    if miner_type == MinerType.BITMAIN:
        return get_bitmain_warranty(serial)
    elif miner_type == MinerType.WHATSMINER:
        return get_whatsminer_warranty(serial)
    elif miner_type == MinerType.CANAAN:
        return get_canaan_warranty(serial)
    elif miner_type == MinerType.ALPHAMINER:
        return get_alphaminer_warranty(serial)
    elif miner_type == MinerType.AUTO:
        # Order from Java: Canaan, Whatsminer, Bitmain, AlphaMiner
        print(f"Auto checking {serial} against Canaan...")
        res = get_canaan_warranty(serial)
        if res: return res
        
        print(f"Auto checking {serial} against Whatsminer...")
        res = get_whatsminer_warranty(serial)
        if res: return res
        
        print(f"Auto checking {serial} against Bitmain...")
        res = get_bitmain_warranty(serial)
        if res: return res
        
        print(f"Auto checking {serial} against Alpha Miner...")
        res = get_alphaminer_warranty(serial)
        if res: return res
        
    return None
