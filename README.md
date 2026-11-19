# Discovery Service (Speedkathon PSSO, grupa 1: planer wycieczek)

Rejestr adresów mikroserwisów. Serwis zgłasza swoją nazwę, discovery zapamiętuje IP komputera, z którego przyszło
zgłoszenie. Klient pyta o IP po nazwie.

Adres discovery: `http://172.20.147.20:8080` (HTTP + JSON). Rejestr przechowuje tylko IP, bez portów: każdy serwis
działa na osobnym komputerze, więc IP wskazuje serwis.

Kontakt: Paweł Tyburski (zespół discovery).

## Nazwy serwisów

| Nazwa w discovery | Zespół | IP (adres awaryjny) |
|---|---|---|
| `attractions` | 1. Atrakcje | 172.20.147.24 |
| `gastronomy` | 2. Gastronomia | 172.20.147.27 |
| `transport` | 3. Transport | 172.20.147.17 |
| `weather` | 4. Warunki dnia | 172.20.147.21 |
| `planner` | 5. Planer i integracja | 172.20.147.14 |

Wielkość liter nie ma znaczenia (`Transport` = `transport`), ale prosimy o nazwy dokładnie jak w tabeli.

## Endpointy

### 1. Rejestracja: `POST /services`
Klient: każdy serwis, przy starcie albo ręcznie przez curl **z komputera, na którym działa serwis**.
IP jest odczytywane z połączenia. Ponowna rejestracja tej samej nazwy nadpisuje IP.

```http
POST /services
Content-Type: application/json

{"name": "transport"}
```
Odpowiedź `201 Created` (pierwsza rejestracja) albo `200 OK` (nadpisanie):
```json
{"name": "transport", "ip": "172.20.147.17", "registeredAt": "2026-11-19T10:30:00Z"}
```
Opcjonalnie można podać IP wprost (np. rejestracja z innego komputera): `{"name": "transport", "ip": "172.20.147.17"}`.

Błąd `400 Bad Request`: `{"error": "name is required"}`.

### 2. Wyrejestrowanie: `DELETE /services/{name}`
```http
DELETE /services/transport
```
`204 No Content`, albo `404 Not Found`: `{"error": "service not found: transport"}`.

### 3. Zapytanie o IP: `GET /services/{name}`
```http
GET /services/transport
```
`200 OK`:
```json
{"name": "transport", "ip": "172.20.147.17", "registeredAt": "2026-11-19T10:30:00Z"}
```
`404 Not Found`: `{"error": "service not found: transport"}`.

### 4. Lista wszystkich: `GET /services`
```json
[{"name": "transport", "ip": "172.20.147.17", "registeredAt": "2026-11-19T10:30:00Z"}]
```

## Zasady dla klientów
1. Limit oczekiwania na odpowiedź discovery: 2 s.
2. Discovery niedostępne (timeout, brak połączenia) albo `404`: używasz IP z tabeli wyżej. Discovery nikogo nie blokuje.
3. Na start serwisy używają IP wpisanych na stałe. Na discovery przełączamy się, gdy integracja już działa.
4. Brak wpisu (`404`) znaczy „serwis się nie zarejestrował”, a nie „serwis nie działa”.

## Rejestracja przez curl (z komputera serwisu)
```bash
curl -X POST http://172.20.147.20:8080/services -H "Content-Type: application/json" -d '{"name":"transport"}'
curl http://172.20.147.20:8080/services/transport
curl http://172.20.147.20:8080/services
curl -X DELETE http://172.20.147.20:8080/services/transport
```

## Uruchomienie
Java 21, Maven. W IntelliJ: `DiscoveryServiceApplication` → Run. Z terminala:
```bash
mvn spring-boot:run
```
Inny port bez przebudowy: `PORT=9000 mvn spring-boot:run`. Serwis nasłuchuje na `0.0.0.0`, więc jest widoczny z sieci.
Rejestr jest w pamięci: po restarcie discovery serwisy rejestrują się ponownie.
