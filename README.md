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
IP jest odczytywane z połączenia.
Pod jedną nazwą może być kilka instancji (kilka IP): ta sama nazwa z innego IP dodaje instancję (`201`),
ta sama nazwa z tego samego IP tylko odświeża wpis (`200`).

```http
POST /services
Content-Type: application/json

{"name": "transport"}
```
Odpowiedź `201 Created` (nowa instancja) albo `200 OK` (odświeżenie istniejącej):
```json
{"name": "transport", "ip": "172.20.147.17", "registeredAt": "2026-11-19T10:30:00Z"}
```
Opcjonalnie można podać IP wprost (np. rejestracja z innego komputera): `{"name": "transport", "ip": "172.20.147.17"}`.

Błąd `400 Bad Request`: `{"error": "name is required"}`.

### 2. Wyrejestrowanie: `DELETE /services/{name}`
```http
DELETE /services/transport                      (usuwa wszystkie instancje)
DELETE /services/transport?ip=172.20.147.17     (usuwa jedną instancję)
```
`204 No Content`, albo `404 Not Found`: `{"error": "service not found: transport"}`
lub `{"error": "instance not found: transport 172.20.147.17"}`.

### 3. Zapytanie o IP: `GET /services/{name}`
Zwraca **jedną losową** instancję (prosty load balancing): przy dwóch instancjach kolejne zapytania trafiają
raz na jedno, raz na drugie IP. Klient pyta przed każdym wywołaniem, żeby ruch się rozkładał.
```http
GET /services/transport
```
`200 OK`:
```json
{"name": "transport", "ip": "172.20.147.17", "registeredAt": "2026-11-19T10:30:00Z"}
```
`404 Not Found`: `{"error": "service not found: transport"}`.

### 4. Wszystkie instancje jednego serwisu: `GET /services/{name}/instances`
```json
[{"name": "transport", "ip": "172.20.147.17", "registeredAt": "..."},
 {"name": "transport", "ip": "172.20.147.18", "registeredAt": "..."}]
```
`404 Not Found`, gdy nazwa nie jest zarejestrowana.

### 5. Lista wszystkich: `GET /services`
```json
[{"name": "transport", "ip": "172.20.147.17", "registeredAt": "2026-11-19T10:30:00Z"}]
```
Każda instancja to osobny element listy.

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
Java 17+, Maven. W IntelliJ: `DiscoveryServiceApplication` → Run. Z terminala:
```bash
mvn spring-boot:run
```
Inny port bez przebudowy: `PORT=9000 mvn spring-boot:run`. Serwis nasłuchuje na `0.0.0.0`, więc jest widoczny z sieci.
Rejestr jest zapisywany do pliku `registry.json` (w katalogu, z którego uruchamiasz serwis) i wczytywany po restarcie.
Inna ścieżka: `--discovery.storage-file=/sciezka/plik.json`; pusty parametr wyłącza zapis.

## Pracownia (komputer 172.20.147.20)
Gotowy plik `discovery-service-1.1.0.jar` jest w zakładce Releases repozytorium. Na komputerze w pracowni:
```bash
java -version                      # potrzebna Java 17 albo nowsza
wget https://github.com/TybuRak/psso-discovery-service/releases/download/v1.1.0/discovery-service-1.1.0.jar
java -jar discovery-service-1.1.0.jar          # albo: PORT=80 java -jar discovery-service-1.1.0.jar
```
Brak Javy i brak uprawnień do instalacji: przenośny JDK do katalogu domowego (bez sudo):
```bash
wget -O jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
tar xzf jdk.tar.gz && ./jdk-21*/bin/java -jar discovery-service-1.1.0.jar
```
Sprawdzenie z innego komputera w sieci: `curl http://172.20.147.20:8080/services` powinno zwrócić `[]`.

## Aktualizacja działającego serwisu (1.0.0 -> 1.1.0) bez utraty rejestracji
API jest zgodne wstecz: te same żądania i odpowiedzi, klienci nic nie zmieniają.
```bash
cd ~
wget https://github.com/TybuRak/psso-discovery-service/releases/download/v1.1.0/discovery-service-1.1.0.jar
curl -s http://localhost:8080/services > registry.json      # zrzut rejestru ze starej wersji
# w oknie starej wersji: Ctrl+C
~/jdk-21*/bin/java -jar ~/discovery-service-1.1.0.jar       # w logach: "Loaded N instance(s)"
```
Przerwa trwa około 2 sekund; w tym czasie klienci korzystają z adresów awaryjnych.
