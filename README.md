# AdhoCondor - Aplicație Android pentru comunicații ad-hoc folosind Wi-Fi Direct

AdhoCondor este o aplicație Android dezvoltată pentru comunicarea peer-to-peer (P2P), care combină tehnologia Wi-Fi Direct cu socket-uri de rețea TCP/UDP și un editor foto integrat (alte funcționalități urmează să fie adăugate). Aplicația permite conectarea directă între dispozitive aflate în proximitate fără a necesita conexiune la Internet, date mobile sau un server centralizat, oferind o soluție complet offline pentru mesagerie instantanee, schimb de date de profil și transfer de fișiere multimedia.

---

## Structura sistemului

Codul este organizat pe trei niveluri:

1. **Modulul de rețelistică și comunicare P2P (`network/`):**
   * `WiFiDirectBroadcastReceiver.kt`: Gestionează evenimentele Wi-Fi Direct trimise de Android (starea Wi-Fi, căutarea dispozitivelor din apropiere și schimbările de conexiune).
   * `UDPHandshake.kt`: Trimite și primește datele inițiale de profil (nickname, UUID) prin pachete UDP pe portul 8888.
   * `ChatServer.kt`: Serverul TCP (portul 8888) care rulează în fundal, primește conexiunile noi, mesajele text și fișierele trimise.
   * `ChatClient.kt`: Clientul TCP care se conectează la IP-ul celuilalt dispozitiv și trimite mesajele text și atașamentele.

2. **Modulul de gestiune și stare (`manager/`):**
   * `ChatManager.kt`: Gestionează conexiunea dintre server și client, cererile de conectare (acceptare, refuz, blocare), trimiterea documentelor și legătura dintre rețea și ecranul de chat.

3. **Interfața grafică și editarea foto (`ui/`):**
   * `ui/main/StarterPage.kt`: Ecranul de pornire unde îți setezi numele, poza de profil și oferi permisiunile necesare.
   * `ui/main/MainActivity.kt`: Ecranul principal de unde cauți și te conectezi la alte dispozitive prin Wi-Fi Direct.
   * `ui/chat/ChatActivity.kt`, `ChatAdapter.kt`, `ChatMessage.kt` și `ChatUtils.kt`: Ecranul de chat în timp real (afișarea mesajelor, trimiterea de emoji-uri și atașarea de fișiere).
   * `ui/photo/PhotoEditorActivity.kt`: Editorul foto pentru decupat, desenat, adăugat text/emoji-uri și aplicat filtre pe poze înainte de a le trimite.
   * `ui/theme/`: Culorile, temele (Light/Dark) și stilurile vizuale ale aplicației.

---

## Structura proiectului (codul sursă)

```text
AdhoCondor/
├── app/                                       # Modulul principal al aplicației
│   ├── build.gradle.kts                       # Dependențele și configurarea modulului app
│   ├── proguard-rules.pro                     # Reguli de optimizare și protecție a codului
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml            # Permisiunile și componentele aplicației
│           ├── java/com/example/adhocondor/
│           │   ├── manager/                   # Gestiunea stării aplicației
│           │   │   └── ChatManager.kt         # Conexiunea de chat, cererile de conectare și fișierele
│           │   ├── network/                   # Comunicarea prin rețea
│           │   │   ├── ChatClient.kt          # Trimiterea mesajelor și fișierelor (TCP)
│           │   │   ├── ChatServer.kt          # Primirea mesajelor și fișierelor (TCP)
│           │   │   ├── UDPHandshake.kt        # Schimbul de date de profil (UDP port 8888)
│           │   │   └── WiFiDirectBroadcastReceiver.kt # Evenimentele Wi-Fi Direct
│           │   └── ui/                        # Interfața grafică
│           │       ├── chat/                  # Ecranul de chat
│           │       │   ├── ChatActivity.kt    # Ecranul principal de chat
│           │       │   ├── ChatAdapter.kt     # Afișarea mesajelor în listă
│           │       │   ├── ChatMessage.kt     # Structura unui mesaj
│           │       │   ├── ChatUtils.kt       # Utilitare pentru timp și fișiere
│           │       │   └── ConversationsActivity.kt # Pagina de conversații (în dezvoltare)
│           │       ├── main/                  # Ecranele de pornire și scanare
│           │       │   ├── MainActivity.kt    # Scanarea și conectarea prin Wi-Fi Direct
│           │       │   └── StarterPage.kt     # Ecranul de pornire și setarea profilului
│           │       ├── photo/                 # Modulul de editare foto
│           │       │   └── PhotoEditorActivity.kt # Editarea, desenarea și decuparea pozelor
│           │       ├── settings/              # Setările aplicației
│           │       │   └── SettingsActivity.kt # Ecranul de setări (în dezvoltare)
│           │       └── theme/                 # Culorile, temele și stilurile vizuale
│           │           ├── Color.kt
│           │           ├── Theme.kt
│           │           └── Type.kt
│           └── res/                           # Elemente vizuale, layout-uri și texte
│               ├── drawable/                  # Imagini și fundaluri vectoriale
│               ├── layout/                    # Layout-urile XML pentru ecrane
│               ├── menu/                      # Meniul de navigare
│               ├── mipmap-*/                  # Pictogramele aplicației
│               ├── values/                    # Culori, texte și stiluri XML
│               └── xml/                       # Configurații pentru FileProvider și backup
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar                 # Fișierele Gradle Wrapper
│       └── gradle-wrapper.properties
├── .gitignore                                 # Fișierele ignorate de Git
├── build.gradle.kts                           # Configurarea proiectului rădăcină
├── gradle.properties                          # Proprietățile Gradle
├── gradlew                                    # Script de rulare Gradle (Linux/macOS)
├── gradlew.bat                                # Script de rulare Gradle (Windows)
├── README.md                                  # Documentația proiectului
└── settings.gradle.kts                        # Configurarea modulelor proiectului
```

---

## Instalare și rulare din codul sursă

### 1. Cerințe
* **Android Studio** (versiune recomandată: Ladybug sau mai nouă).
* **JDK 11** sau o versiune mai nouă configurată în IDE.
* Două **dispozitive fizice Android** cu versiunea Android 8.0 (API Level 26) sau mai nouă (tehnologia Wi-Fi Direct necesită hardware de rețea real și nu funcționează fiabil pe emulatoare).

### 2. Deschidere și configurare în Android Studio
1. Deschide **Android Studio**.
2. Selectează **File** -> **Open...** și alege folderul rădăcină al proiectului (`AdhoCondor`).
3. Așteaptă finalizarea procesului de **Gradle Sync** pentru descărcarea tuturor dependențelor necesare.

### 3. Rularea aplicației
1. Conectează dispozitivele Android la calculator prin cablu USB și asigură-te că opțiunea **USB Debugging** este activată.
2. Selectează dispozitivul țintă din bara superioară a IDE-ului.
3. Apasă butonul **Run 'app'** (sau combinația de taste `Shift + F10`).
4. La prima lansare pe telefon, acordă permisiunile solicitate pentru **Location**, **Nearby Wi-Fi Devices** și **Camera**.
