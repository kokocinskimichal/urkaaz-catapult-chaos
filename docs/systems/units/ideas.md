# Pomysły na nowe jednostki

Dokument zawiera niezatwierdzone koncepcje nowych jednostek dla Urkaaaz.
Żadna z propozycji nie jest częścią aktualnego balansu, kampanii ani runtime.

Przed implementacją każda jednostka wymaga osobnej decyzji dotyczącej:

- roli i celu gameplayowego;
- statystyk oraz kosztu;
- limitu aktywnych jednostek;
- interakcji z istniejącymi jednostkami;
- zachowania AI;
- animacji, assetów i czytelności na polu bitwy;
- testów symulacji i integracji z kampanią.

## Istniejące role

Aktualnie w grze występują:

| Jednostka | Główna rola |
|---|---|
| Defender | podstawowa walka wręcz |
| Goblin Raging Boar | szybka walka wręcz |
| Goblin Slingmaster | atak dystansowy |
| Demolisher | jednorazowa eksplozja kontaktowa |
| Sapper | misja oblężnicza przeciw fortecy |

Nowe propozycje powinny uzupełniać te role, a nie tworzyć kilka jednostek o
tym samym zastosowaniu.

## Propozycje

### 1. Shieldbearer — tarczownik

**Rola:** obrona i osłona sojuszników.

Powolna jednostka walcząca wręcz, która zmniejsza obrażenia dystansowe
otrzymywane przez pobliskie jednostki. Sama zadaje mniejsze obrażenia niż
Defender, ale może utrzymywać linię frontu.

**Możliwe zasady:**

- ochrona działa tylko w ograniczonym promieniu;
- efekt nie kumuluje się z drugim Shieldbearerem;
- tarcza nie chroni przed eksplozjami Demolishera;
- jednostka traci ochronę podczas ataku albo po otrzymaniu określonej liczby
  trafień.

**Ryzyko:** zbyt silna ochrona może sprawić, że Slingmaster i katapulta będą
mało skuteczne przeciwko zwartej formacji.

### 2. Burrower — podkopacz

**Rola:** obchodzenie pierwszej linii.

Jednostka poruszająca się przez krótki odcinek pod powierzchnią terenu.
Okresowo znika z normalnej kolizji jednostek, a następnie wynurza się przy
celu.

**Możliwe zasady:**

- nie może wynurzyć się w fundamencie fortecy;
- podczas podkopu nie może atakować;
- wynurzenie ma opóźnienie i jest widoczne jako ostrzeżenie;
- eksplozja lub specjalny typ obrażeń może przerwać podkop.

**Ryzyko:** wymaga jasnej reguły interakcji z niszczeniem terenu i nie może
tworzyć drugiego, niezależnego systemu kolizji.

### 3. Orc Banner Bearer — chorąży

**Rola:** wsparcie grupy i wzmacnianie formacji.

Jednostka nie atakuje bezpośrednio albo zadaje symboliczne obrażenia, ale
zwiększa morale pobliskich jednostek.

**Możliwe zasady:**

- sojusznicy otrzymują niewielki bonus do szybkości lub obrażeń;
- efekt działa tylko, gdy chorąży żyje i znajduje się za pierwszą linią;
- bonus nie zwiększa obrażeń Demolishera ani efektów Sappera;
- po śmierci chorążego efekt znika po krótkim czasie, zamiast natychmiast.

**Ryzyko:** bonus powinien być łatwy do odczytania i ograniczony, aby nie
zastępował systemu synergii melee.

### 4. Hook Goblin — hakownik

**Rola:** kontrola pozycji wroga.

Jednostka dystansowa używająca haka do krótkiego przyciągnięcia lekkiej
jednostki albo spowolnienia ciężkiego celu.

**Możliwe zasady:**

- hak nie może przemieszczać fortec, katapult ani Sappera podczas misji;
- ciężkie jednostki są tylko spowalniane;
- efekt ma czas odnowienia i wymaga linii widzenia;
- hak przerywa ruch, ale nie przerywa już rozpoczętego ataku.

**Ryzyko:** przesuwanie jednostek może komplikować deterministyczne rozwiązywanie
kolizji i kolejność rozstrzygania ataków.

### 5. Alchemist — alchemik

**Rola:** obszarowe wsparcie i kontrola terenu.

Jednostka rzuca fiolki tworzące krótkotrwały obszar spowolnienia albo
osłabienia. Nie powinna konkurować bezpośrednio z Demolisherem jako jednostka
eksplozywna.

**Możliwe zasady:**

- obszar nie niszczy terenu;
- efekt ma stały czas działania;
- kolejne fiolki odświeżają czas zamiast zwiększać siłę;
- jednostka może nakładać efekt na jednostki, ale nie na fortecę.

**Ryzyko:** obszary czasowe wymagają jawnego deterministycznego czasu
wygaśnięcia i dobrego sposobu prezentacji na małym ekranie.

### 6. Harpooner — harpunnik

**Rola:** specjalista przeciw szybkim jednostkom.

Jednostka dystansowa z długim czasem przeładowania, która unieruchamia albo
mocno spowalnia cel na krótki czas.

**Możliwe zasady:**

- harpun może trafić tylko jeden cel;
- efekt nie działa na Demolishera po rozpoczęciu detonacji;
- trafienie Sappera spowalnia jego misję, ale nie resetuje jej;
- brak klasycznego DPS-u rekompensuje kontrola ruchu.

**Ryzyko:** kontrola ruchu może prowadzić do trwałego blokowania przeciwnika,
jeżeli czas odnowienia i odporności nie będą jawne.

### 7. Tunnel Sapper — saper tunelowy

**Rola:** alternatywna jednostka oblężnicza.

Wariant Sappera skupiony na przygotowaniu krótkiego tunelu lub punktu
przejścia, zamiast bezpośredniego marszu do fortecy.

**Możliwe zasady:**

- posiada osobną, wyraźnie ograniczoną misję;
- może ominąć jedną przeszkodę, ale jest wolniejszy;
- po dotarciu do fortecy zadaje mniejsze obrażenia niż zwykły Sapper;
- nie może być aktywny jednocześnie ze zwykłym Sapperem tej samej drużyny.

**Ryzyko:** łatwo stworzyć duplikat Sappera. Ta propozycja wymagałaby
wykazania, że wnosi inną decyzję strategiczną, a nie tylko inną prędkość.

### 8. Berserker — berserker

**Rola:** ryzykowna jednostka przełamująca.

Jednostka wręcz, która zyskuje szybkość lub obrażenia po otrzymaniu obrażeń.
Ma niższą bazową wytrzymałość i jest podatna na kontrolę dystansową.

**Możliwe zasady:**

- bonus ma limit i nie kumuluje się bez końca;
- bonus resetuje się po krótkim okresie bez obrażeń;
- jednostka pozostaje uczestnikiem zwykłej synergii melee;
- Demolisher może ją skutecznie kontrować mimo aktywnego bonusu.

**Ryzyko:** interakcja z jednoczesnym rozstrzyganiem obrażeń musi być jawna,
aby kolejność trafień nie zmieniała wyniku replayu.

### 9. Scout — zwiadowca

**Rola:** rozpoznanie i wsparcie celowania.

Lekka jednostka o małej wytrzymałości, która zwiększa widoczność lub poprawia
informację o celu. Nie powinna bezpośrednio zwiększać wszystkich obrażeń.

**Możliwe zasady:**

- ujawnia pozycję ukrytych lub odległych celów;
- może zapewniać niewielką poprawę podglądu trajektorii;
- sam zadaje minimalne obrażenia;
- po śmierci informacja znika albo pozostaje przez krótki czas.

**Ryzyko:** wymaga wcześniejszego zdefiniowania, czy Urkaaaz ma stan ukrycia
oraz osobny system widoczności. Bez takiego systemu Scout może być pozornie
użyteczny, ale gameplayowo pusty.

### 10. Stone Golem — kamienny golem

**Rola:** ciężka jednostka nacisku.

Bardzo wolna jednostka o dużej wytrzymałości, odporna na część efektów
spowalniających. Ma ograniczone obrażenia i jest dobrym celem dla
Demolishera oraz katapulty.

**Możliwe zasady:**

- nie może być odpychany przez zwykłe jednostki;
- otrzymuje zwiększone obrażenia od Demolishera;
- nie korzysta z pełnego bonusu szybkości Banner Goblina;
- zajmuje więcej miejsca w formacji.

**Ryzyko:** wymaga wspólnej, jawnej definicji masy, odporności i miejsca
zajmowanego przez jednostkę, aby nie dodawać wyjątków do każdego systemu.

### 11. Aerial Bomber — goblin w balonie

**Rola:** powolne zagrożenie powietrzne i atak obszarowy.

Jednostka przemieszcza się po czytelnej, łukowej trasie między fortecami i
okresowo zrzuca małą bombę w kierunku najbliższej grupy przeciwnika.

**Możliwe zasady:**

- bomba ma krótki lont i ograniczony promień wybuchu;
- bezpośrednie trafienie z katapulty niszczy balon;
- pocisk może trafić bombę w locie i wywołać przedwczesny wybuch;
- jednostka nie może automatycznie niszczyć fortecy;
- trasa, moment zrzutu i wysokość muszą być deterministyczne.

**Ryzyko:** jednostka wymaga osobnej, ale ograniczonej warstwy ruchu
powietrznego oraz jasnej reguły kolizji z pociskami.

### 12. Wind Disruptor — ork na latawcu

**Rola:** krótkotrwałe zakłócanie celowania.

Mały ork na niestabilnym latawcu przelatuje między fortecami i okresowo
tworzy niewielką strefę lokalnych turbulencji.

**Możliwe zasady:**

- trasa latawca jest zygzakowata, ale przewidywalna;
- turbulencja wpływa tylko na kolejny lub najbliższy pocisk w lokalnej strefie;
- efekt nie zmienia globalnie fizyki wiatru;
- efekt nie niszczy pocisku ani trwale nie zmienia parametrów celowania;
- trafienie z katapulty niszczy latawiec.

**Ryzyko:** nie wolno dopuścić do powstania drugiego globalnego systemu
wiatru. Efekt musi być lokalnym, deterministycznym modyfikatorem pocisku.

### 13. Burrowing Assault Unit — ork na wielkim krecie

**Rola:** kontrolowane obejście pierwszej linii.

Jednostka przez krótki czas porusza się po powierzchni, następnie przechodzi
do jawnego stanu `UNDERGROUND` i podąża widocznym tunelem lub kopcem ziemi w
kierunku fortecy przeciwnika.

**Możliwe zasady:**

- pod ziemią jednostka jest niewidoczna albo ma ograniczoną podatność na ataki;
- nie może dowolnie deformować wspólnej heightmapy;
- nie może tworzyć dziur w fundamentach fortec;
- po dotarciu do celu wynurza się i wykonuje jeden silny atak;
- przeciwnik może trafić ją przed zanurzeniem albo przechwycić po wynurzeniu;
- tunel jest kontrolowanym efektem gameplayowym, a nie ogólnym systemem
  deformacji terenu.

**Ryzyko:** wymaga jednoznacznego stanu runtime, sygnałów wizualnych,
interakcji z craterami oraz testów deterministycznego ruchu.

### 14. Destroyer — niszczyciel

**Rola:** ciężka jednostka oblężnicza.

Powolna i wytrzymała jednostka wyspecjalizowana w niszczeniu murów, wież,
przeszkód obronnych lub innych jawnie zdefiniowanych struktur. Nie powinna
być tylko Defenderem z większą liczbą punktów zdrowia.

**Możliwe zasady:**

- preferuje cele strukturalne zamiast zwykłych jednostek;
- zadaje mniejsze obrażenia w walce z jednostkami;
- może absorbować część obrażeń podczas podejścia;
- ma wyraźną kontrę w postaci Demolishera, katapulty i szybkich jednostek;
- nie zmienia fundamentów fortec bez osobnej reguły poziomu.

**Ryzyko:** obecny runtime ma Sappera jako jednostkę oblężniczą. Destroyer
potrzebuje osobnego celu i decyzji strategicznej, aby nie być tylko drugim
Sapperem.

### 15. Goblin Illusionist — gobliński iluzjonista

**Rola:** krótkotrwałe zmylenie przeciwnika.

Krucha jednostka tworząca jedną lub dwie krótkotrwałe kopie wizualne. Kopie
nie są prawdziwymi jednostkami i nie powinny zwiększać liczby aktywnych
jednostek.

**Możliwe zasady:**

- kopie nie zadają obrażeń i nie mają własnego runtime state;
- kopie nie kolidują z jednostkami ani terenem;
- kopia może znikać po otrzymaniu trafienia albo po upływie czasu;
- prawdziwy iluzjonista musi mieć jednoznaczny sygnał możliwy do odczytania
  przez gracza i AI;
- iluzje nie mogą wpływać na wynik, zasoby ani limit jednostek.

**Ryzyko:** renderowanie kopii nie może tworzyć drugiego systemu jednostek.
Celowanie, obrażenia i snapshoty muszą zawsze wskazywać wyłącznie prawdziwą
jednostkę.

## Priorytet koncepcyjny

Jeżeli nowe jednostki mają być dodawane etapami, proponowana kolejność
eksploracji jest następująca:

1. **Shieldbearer** — uzupełnia obecny skład bez nowej topologii ruchu;
2. **Alchemist** — wprowadza kontrolę obszaru, ale wymaga czasu efektów;
3. **Hook Goblin** — dodaje kontrolę pojedynczego celu;
4. **Orc Banner Bearer** — po dopracowaniu granicy między buffami a synergią
   melee;
5. **Destroyer** — po rozdzieleniu jego celu oblężniczego od Sappera;
6. **Wind Disruptor** — po zdefiniowaniu lokalnych modyfikatorów pocisków;
7. **Stone Golem** — po zdefiniowaniu masy i odporności;
8. **Harpooner** — po dopracowaniu odporności na kontrolę;
9. **Berserker** — po potwierdzeniu kolejności symultanicznych obrażeń;
10. **Aerial Bomber** — po przygotowaniu ograniczonego modelu ruchu
    powietrznego;
11. **Burrowing Assault Unit** — dopiero po ustaleniu zasad podziemnego ruchu;
12. **Scout** — dopiero razem z systemem widoczności;
13. **Tunnel Sapper** — po udowodnieniu, że nie jest tylko wariantem Sappera.

Kolejność jest propozycją architektoniczną, a nie decyzją projektową.

## Zasady przed rozpoczęciem implementacji

Nowa jednostka nie powinna być dodawana wyłącznie na podstawie nazwy lub
wyglądu. Przed uruchomieniem `/addUnit <nazwa>` należy zaakceptować:

- jednoznaczną rolę;
- decyzję, jaki istniejący problem strategiczny rozwiązuje;
- kontrę i słabości;
- interakcje z każdą istniejącą jednostką;
- wpływ na fortecę, katapultę, amunicję i teren;
- zasady AI;
- limity, koszt i odblokowanie;
- wymagane eventy, snapshoty i stan runtime;
- testy oraz wymagane assety.

Pomysły z tego dokumentu pozostają propozycjami do czasu osobnej akceptacji.
