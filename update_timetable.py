import requests
import json
from datetime import datetime, timedelta

def fetch_connections():
    today = datetime.now()
    days_until_monday = (7 - today.weekday()) % 7
    if days_until_monday == 0: 
        days_until_monday = 7
    next_monday = (today + timedelta(days=days_until_monday)).replace(hour=12, minute=0, second=0)
    
    url = f"https://v6.db.transport.rest/journeys?from=Delitzsch&to=Zschortau&departure={next_monday.isoformat()}&results=20"
    
    # WICHTIG: Eigenen User-Agent mitgeben, um den 503-Block von Python-Requests zu umgehen!
    headers = {
        'User-Agent': 'SchulwegPlanerDelitzsch/1.0 (Contact: eltern-app-privat)'
    }
    
    try:
        # Timeout auf 15 Sekunden erhöhen
        response = requests.get(url, headers=headers, timeout=15)
        response.raise_for_status()
        data = response.json()
        
        connections = []
        for journey in data.get('journeys', []):
            legs = journey.get('legs', [])
            transit_leg = next((leg for leg in legs if not leg.get('walking')), None)
            
            if transit_leg:
                line = transit_leg.get('line', {})
                mode = line.get('mode', '')
                is_train = mode == 'train'
                
                connections.append({
                    "type": "S-Bahn" if is_train else "Bus",
                    "lineName": line.get('name', 'ÖPNV'),
                    "fromStop": transit_leg.get('origin', {}).get('name', 'Delitzsch'),
                    "departureTime": transit_leg['departure'].split('T')[1][:5],
                    "toStop": transit_leg.get('destination', {}).get('name', 'Zschortau'),
                    "arrivalTime": transit_leg['arrival'].split('T')[1][:5],
                    "requiredWalkBuffer": 15 if is_train else 5
                })
        
        unique = list({(c['departureTime'], c['lineName']): c for c in connections}.values())
        unique.sort(key=lambda x: x['departureTime'])
        return unique

    except Exception as e:
        print(f"Fehler beim Abrufen der API: {e}")
        return None

if __name__ == "__main__":
    new_data = fetch_connections()
    if new_data and len(new_data) > 0:
        with open('timetable.json', 'w', encoding='utf-8') as f:
            json.dump(new_data, f, indent=2, ensure_ascii=False)
        print(f"Erfolgreich aktualisiert: {len(new_data)} Verbindungen.")
    else:
        print("Keine neuen Daten empfangen. Die bestehende timetable.json bleibt unverändert.")