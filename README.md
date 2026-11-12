# IM server

Jednoduchý chat server v Jave s webovým rozhraním cez WebSockety.

## Návod na spustenie

 Nutnosť mať Javu 17 a internet pri prvom builde (Gradle si stiahne knižnice).
 V priečinku projektu si otvoríme 2 teminály, v ktorých spustíme nasledovné príkazy:

1. **Build**
   ```
   .\gradlew build
   ```
2. **Terminál 1: IM server** (port a max. počet klientov)
   ```
   java -jar app/build/libs/app.jar 33000 10
   ```
3. **Terminál 2: webový bridge**
   ```
   .\gradlew runBridge
   ```
4. **Prehliadač:** otvor `http://localhost:8080`, zadaj meno a píš.

Oba terminály musia ostať otvorené, možnosť ich zastaviť pomocou `Ctrl+C`.

Ak ti hlási `Address already in use`, port už používa starý proces. Nájdi ho cez `netstat -ano | findstr :33000` a zastav cez `taskkill /PID <cislo> /F`.

## Príkazy

| Príkaz | Popis |
|---|---|
| `#setMyName <meno>` | zmení meno (bez medzier, musí byť unikátne) |
| `#sendPrivate <meno> <správa>` | pošle súkromnú správu |
| `#join <názov>` | pripojí do miestnosti, ak neexistuje, vytvorí ju |
| `#leave <názov>` | opustí miestnosť |
| `#groups` | vypíše tvoje miestnosti oddelené čiarkou |

Prvá správa po pripojení sa berie ako meno. Každý klient je automaticky v miestnosti `public`.
Správy chodia ostatným vo formáte `[<od koho>] >> <správa>` a idú do všetkých miestností, v ktorých odosielateľ je.

