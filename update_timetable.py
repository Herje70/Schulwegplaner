import requests
import json
from datetime import datetime, timedelta

def fetch_connections():
    # Wir suchen nach Verbindungen für den nächsten Montag um 12:00 Uhr,
    # um einen typischen Schultag-Fahrplan zu erhalten.
    today = datetime.now()
    days_until_monday = (7 - today.weekday()) % 7
    if days_until_monday == 0: days_until_monday = 7
    next_monday = (today + timedelta(days=days_until_monday)).replace(hour=12, minute=0, second=0)
    
    # API-Abfrage (Delitzsch nach Zschortau)
    url = f"https://v6.db.transport.rest/journeys?from=Delitzsch&to=Zschortau&departure={next_monday.isoformat()}&results=20"
    
    try:
        response = requests.get(url)
        response.raise_for_status()
        data = response.json()
        
        connections = []
        for journey in data.get('journeys', []):
            legs = journey.get('legs', [])
            # Wir suchen den ersten Teil der Reise, der kein Fußweg ist
            transit_leg = next((leg for leg in legs if not leg.get('walking')), None)
            
            if transit_leg:
                line = transit_leg.get('line', {})
                mode = line.get('mode', '')
                is_train = mode == 'train'
                
                # Formatierung für die App
                connections.append({
                    "type": "S-Bahn" if is_train else "Bus",
                    "lineName": line.get('name', 'ÖPNV'),
                    "fromStop": transit_leg['origin']['name'],
                    "departureTime": transit_leg['departure'].split('T')[1][:5], # Extrahiert HH:mm
                    "toStop": transit_leg['destination']['name'],
                    "arrivalTime": transit_leg['arrival'].split('T')[1][:5],     # Extrahiert HH:mm
                    "requiredWalkBuffer": 15 if is_train else 5
                })
        
        # Doppelte Einträge entfernen und nach Zeit sortieren
        unique = list({(c['departureTime'], c['lineName']): c for c in connections}.values())
        unique.sort(key=lambda x: x['departureTime'])
        return unique

    except Exception as e:
        print(f"Fehler: {e}")
        return None

if __name__ == "__main__":
    new_data = fetch_connections()
    if new_data:
        with open('timetable.json', 'w', encoding='utf-8') as f:
            json.dump(new_data, f, indent=2, ensure_ascii=False)
        print(f"Erfolgreich aktualisiert: {len(new_data)} Verbindungen.")
