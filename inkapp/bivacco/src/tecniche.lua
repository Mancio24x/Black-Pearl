-- Le tecniche, in tre sezioni. Ogni tutorial:
--   id, titolo, sez ('sopravvivenza' = Bushcraft, 'nodi', 'trappole', 'tarp'), livello (1-3), tempo
--   intro       a cosa serve
--   serve       cosa ti serve (facoltativo)
--   attenzione  l'avviso di sicurezza o di legge (facoltativo)
--   passi       { titolo, testo, img = 'nome' } ; img è il disegno del passo (tools/disegni.py)
-- Testi solo in Latin-1: niente trattini lunghi né virgolette curve.

return {

------------------------------------------------------------------ sopravvivenza

{ id = 'stop', titolo = 'Ti sei perso: S.T.O.P.', sez = 'sopravvivenza', livello = 1, tempo = '5 min',
  intro = "La prima cosa da fare quando qualcosa va storto: fermarsi. La maggior parte degli incidenti peggiora perché si continua a camminare a caso.",
  passi = {
    { "S: fermati", "Siediti, bevi un sorso, mangia qualcosa. Non fare un passo finché non hai un piano. Il panico fa camminare veloce nella direzione sbagliata." },
    { "T: pensa", "Respira. Quando hai visto l'ultimo segnale del sentiero? Quante ore di luce restano? Che tempo fa? Qualcuno sa dove sei e quando rientri?" },
    { "O: osserva", "Guardati intorno: tracce, segnavia, rumore di un torrente o di una strada. Controlla la carta. Il telefono: chiama il 112 se serve, poi mettilo in modalità aereo per risparmiare batteria." },
    { "P: pianifica", "Se ritrovi il sentiero con certezza, torna indietro sulle tue tracce. Se no, o se sta per fare buio o c'è nebbia, resta dove sei: chi ti cerca parte dall'ultimo punto noto." },
    { "Le priorità: la regola del 3", "Si resiste circa 3 ore al freddo senza riparo, 3 giorni senza acqua, 3 settimane senza cibo. Quindi, in ordine: proteggiti dal freddo e dal vento, fatti trovare, poi acqua, poi fuoco. Il cibo viene per ultimo." },
    { "Fatti trovare", "Mettiti in un punto aperto e visibile, stendi qualcosa di colorato. Il segnale di soccorso alpino: 6 fischi (o lampi di luce) in un minuto, poi un minuto di pausa, e ripeti. All'elicottero: braccia alzate a Y vuol dire 'sì, serve aiuto'." },
  } },

{ id = 'fuoco', titolo = 'Accendere il fuoco', sez = 'sopravvivenza', livello = 2, tempo = '30 min',
  intro = "Un fuoco a tepee (a cono), il più facile da accendere anche con legna non perfetta. Il segreto non è l'accensione: è la preparazione.",
  serve = "Acciarino o accendino, coltello, legna secca in tre misure.",
  attenzione = "In molte regioni accendere fuochi nei boschi è vietato, soprattutto d'estate. Usa il fornelletto: il fuoco solo nelle aree attrezzate o in una vera emergenza. Mai sotto gli alberi o col vento forte.",
  passi = {
    { "Scegli il posto", "Terra nuda, a 3 metri da tenda, alberi, rocce strapiombanti e erba secca. Niente rami sopra. Riparato dal vento. Pulisci un cerchio di un metro fino alla terra e, se ci sono, metti intorno delle pietre asciutte (mai prese da un torrente: con il calore possono scoppiare).", img = 'fuoco1' },
    { "Prepara tre mucchi", "Prima di accendere raccogli tutto. Esca: un pugno di materiale finissimo e secco. Legnetti: rametti dallo spessore di un fiammifero a quello di una matita. Legna: dal pollice al polso. Raccogline il doppio di quello che pensi. Il legno buono è morto e ancora attaccato all'albero: si spezza con un crack secco.", img = 'fuoco2' },
    { "La base e il nido", "Se il terreno è umido fai una base di rametti secchi affiancati. Sopra metti l'esca a forma di nido: erba secca, corteccia di betulla, aghi di pino secchi, i riccioli di un bastoncino piumato (vedi il tutorial).", img = 'fuoco3' },
    { "Il tepee", "Appoggia i legnetti più sottili intorno al nido, a cono, punte che si toccano in alto. Lascia una porta dalla parte da cui arriva il vento, per arrivare all'esca. Poi un secondo giro di legnetti più grossi.", img = 'fuoco4' },
    { "Accendi", "Con l'acciarino: appoggia la punta della barra dentro il nido, tieni il raschietto fermo e tira indietro la barra con forza: le scintille cadono sull'esca senza disfare il nido. Quando l'esca fuma soffia piano, alla base, finché fiammeggia.", img = 'fuoco5' },
    { "Alimenta e spegni", "Aggiungi legna sempre un po' più grossa, senza soffocare la fiamma: il fuoco ha bisogno di aria. Per spegnere: acqua, mescola le braci, ancora acqua, finché la cenere è fredda al tatto. Poi cancella ogni traccia.", img = 'fuoco6' },
  } },

{ id = 'piuma', titolo = 'Il bastoncino piumato', sez = 'sopravvivenza', livello = 2, tempo = '15 min',
  intro = "Quando tutto è bagnato, l'interno del legno morto è ancora asciutto. Con il coltello lo trasformi in riccioli sottili che prendono fuoco con una scintilla.",
  serve = "Un coltello a lama fissa, un ramo secco morto spesso come il polso.",
  attenzione = "Taglia sempre lontano dal corpo e mai verso le gambe. Vedi anche Coltello e intaglio.",
  passi = {
    { "Trova il cuore asciutto", "Prendi un ramo morto ancora sull'albero (non da terra). Spaccalo per il lungo: appoggia la lama sopra e batti il dorso con un altro legno. Il legno chiaro e asciutto dentro è quello che serve.", img = 'piuma1' },
    { "La presa giusta", "Appoggia una punta del legno su un ceppo o su un sasso, un po' inclinato. Tieni il coltello con tutto il pugno, il braccio teso, la lama che lavora verso il basso e lontano da te.", img = 'piuma2' },
    { "I riccioli", "Appoggia la lama quasi piatta sul legno e spingi giù con un movimento lungo e lento, togliendo una striscia sottilissima. Fermati prima della fine: il ricciolo deve restare attaccato. Ruota un poco il legno e ripeti.", img = 'piuma3' },
    { "Il risultato", "Alla fine hai un ciuffo di riccioli in fondo al bastoncino. Raschia con il dorso della lama anche un po' di polvere fine di legno: con l'acciarino si accende subito. Usane due o tre nel nido del fuoco.", img = 'piuma4' },
  } },

{ id = 'coltello', titolo = 'Coltello e intaglio: il picchetto', sez = 'sopravvivenza', livello = 2, tempo = '20 min',
  intro = "Le regole per lavorare il legno senza farsi male, e il primo oggetto utile: un picchetto per la tenda o il tarp.",
  serve = "Un coltello a lama fissa, un ramo duro e dritto spesso come il pollice.",
  attenzione = "Un taglio nell'interno coscia può raggiungere l'arteria femorale: lì non si taglia mai.",
  passi = {
    { "Il triangolo di sangue", "Siediti: l'area tra le ginocchia e l'inguine è il triangolo di sangue. Non tagliare mai in quella direzione. Lavora con i gomiti sulle ginocchia e il legno davanti, fuori dalle gambe." },
    { "Lo spazio intorno", "Tieni il braccio teso e ruota: nessuno deve essere a portata. Quando non usi il coltello, rimettilo nel fodero, non piantarlo nel terreno." },
    { "Il legno", "Taglia un pezzo lungo 25 cm da un ramo duro: nocciolo, faggio, frassino. Togli i rametti con colpi verso l'esterno." },
    { "La punta", "Appoggia il legno su un ceppo e fai la punta con tagli lunghi verso il basso, girando il pezzo: quattro facce, come una matita. Non troppo sottile, o si rompe." },
    { "La tacca per la corda", "A 3 cm dalla testa fai un taglio dritto di traverso, profondo un terzo. Poi un taglio inclinato dal basso che arriva al primo: salta via un cuneo. La corda si aggancia lì e non scivola." },
    { "La testa", "Smussa il bordo della testa con piccoli tagli tutto intorno: quando lo batti con un sasso non si scheggia. Pianta il picchetto inclinato all'indietro, a 45 gradi rispetto alla corda." },
  } },

{ id = 'tarp', titolo = 'Tarp a tettoia', sez = 'tarp', livello = 1, tempo = '20 min',
  intro = "Il riparo a tettoia: un telo, una corda e quattro picchetti. Ripara da pioggia e vento, e davanti puoi fare il fuoco che ti scalda.",
  serve = "Tarp, 10 m di corda, 4-6 picchetti, due alberi a 3-4 metri.",
  passi = {
    { "Scegli il posto", "Due alberi a 3-4 metri, terreno piano o in leggera pendenza, mai in una conca dove si ferma l'acqua. Guarda in alto: niente rami secchi sopra. Da dove arriva il vento? Il lato chiuso del riparo andrà da quella parte.", img = 'tarp1' },
    { "Tendi la dorsale", "Lega la corda al primo albero con una gassa d'amante, a circa un metro di altezza, e all'altro con un tendicavo, così la tendi bene. Più bassa è, meglio ripara dal vento.", img = 'tarp2' },
    { "Appendi il tarp", "Appoggia il tarp sulla corda, con un lato lungo che scende fino a terra e l'altro più corto. Lega i due angoli alti alla corda, ben tesi.", img = 'tarp3' },
    { "Picchetta il lato al vento", "Il lato lungo va a terra dalla parte del vento: picchetta prima gli angoli, poi il centro, tirando il telo. I picchetti inclinati all'indietro.", img = 'tarp4' },
    { "Controlla e sistemati", "Il telo deve essere teso senza pieghe dove si ferma l'acqua. Sotto: il materassino, o uno strato spesso di frasche e foglie secche. Il fuoco eventualmente davanti, a 2 metri, mai sotto il telo.", img = 'tarp5' },
  } },

{ id = 'capanna', titolo = 'Capanna di frasche', sez = 'sopravvivenza', livello = 3, tempo = '2-3 ore',
  intro = "Senza tenda né telo: un riparo fatto solo di rami e foglie, che isola come un sacco a pelo. Costa fatica: comincia molto prima che faccia buio.",
  serve = "Un ramo lungo e robusto, molti rami, tantissime foglie secche.",
  passi = {
    { "La trave", "Trova un ramo robusto lungo una volta e mezza la tua altezza. Appoggia un capo su un ceppo o in una forcella all'altezza della vita, l'altro a terra. Sdraiati sotto: devi entrarci con un palmo di spazio, non di più. Piccola è calda." },
    { "Le costole", "Appoggia ai due lati della trave tanti rami, fitti, che formano una A. Non devono sporgere molto sopra la trave." },
    { "Il graticcio", "Intreccia rami sottili e frasche di traverso sulle costole: servono a tenere su le foglie." },
    { "La copertura", "Ammucchia sopra foglie secche, aghi di pino, felci: almeno un braccio di spessore. È lo spessore che scalda e tiene fuori la pioggia. Sopra qualche ramo perché il vento non la porti via." },
    { "Il letto", "Dentro, uno strato di foglie secche spesso 30 cm, ben pressato: il freddo arriva soprattutto dal terreno." },
    { "La porta", "Entra con i piedi per primi e chiudi l'apertura tirando dentro un mucchio di foglie, o uno zaino." },
  } },

{ id = 'acqua', titolo = "Trovare e rendere sicura l'acqua", sez = 'sopravvivenza', livello = 1, tempo = '',
  intro = "In montagna l'acqua non manca quasi mai, ma anche quella più limpida può contenere batteri e parassiti.",
  serve = "Pentolino, filtro o pastiglie, una bandana.",
  passi = {
    { "Dove cercarla", "L'acqua scende: segui le valli e i canaloni verso il basso. Erba più verde, felci, muschi, salici indicano acqua vicina. Al mattino e alla sera gli uccelli e gli insetti volano verso l'acqua." },
    { "Scegli quella buona", "Meglio un torrente che scorre veloce di una pozza ferma. Prendila dal centro della corrente. Evita l'acqua sotto pascoli, malghe e paesi: è quella più contaminata." },
    { "Prefiltra", "Se è torbida, lasciala riposare nel pentolino e versa piano la parte limpida, oppure filtrala attraverso una bandana. Serve solo a togliere la terra: non la rende sicura." },
    { "Rendila sicura", "Bollire è il metodo più sicuro: un minuto di bollore pieno, tre minuti sopra i 2000 metri. Oppure il filtro (non ferma i virus) o le pastiglie (aspetta il tempo scritto sulla confezione)." },
    { "Rugiada e neve", "All'alba passa una maglietta sull'erba bagnata e strizzala nel pentolino. La neve va sciolta, mai mangiata: raffredda il corpo. Metti un fondo d'acqua nel pentolino prima della neve, o lo bruci." },
    { "Quanta bere", "2-3 litri al giorno, di più col caldo o in salita. Bevi poco e spesso. Se l'urina è scura, stai bevendo troppo poco." },
  } },

{ id = 'nord', titolo = 'Trovare il nord', sez = 'sopravvivenza', livello = 1, tempo = '20 min',
  intro = "Senza bussola: con l'ombra di un bastone, con l'orologio o con le stelle. Il muschio sugli alberi non è affidabile.",
  serve = "Un bastone dritto lungo un metro, due sassi.",
  passi = {
    { "Pianta il bastone", "Su un terreno piano, in pieno sole, pianta un bastone dritto in verticale.", img = 'nord1' },
    { "Segna la prima ombra", "Metti un sasso esattamente sulla punta dell'ombra.", img = 'nord2' },
    { "Aspetta e segna ancora", "Aspetta 15-20 minuti. L'ombra si è spostata: metti un secondo sasso sulla nuova punta.", img = 'nord3' },
    { "Leggi la direzione", "La linea dal primo al secondo sasso va da ovest a est. Mettiti con il primo sasso a sinistra e il secondo a destra: davanti a te c'è il nord.", img = 'nord4' },
    { "Con l'orologio a lancette", "Tieni l'orologio piatto e punta la lancetta delle ore verso il sole. A metà tra la lancetta delle ore e le 12 c'è il sud (con l'ora legale usa l'1 invece delle 12). Il nord è dalla parte opposta." },
    { "Con le stelle", "Trova il Grande Carro. Prendi le due stelle del bordo esterno del carro e prolunga la loro distanza circa 5 volte: arrivi alla Stella Polare, l'unica che non si muove. Sotto di lei, all'orizzonte, c'è il nord." },
  } },

------------------------------------------------------------------ nodi

{ id = 'otto', titolo = 'Nodo a otto', sez = 'nodi', livello = 1, tempo = '2 min',
  intro = "Il nodo d'arresto: fa un ingrossamento alla fine della corda, così non scappa da un anello o da un occhiello. Facile da sciogliere anche dopo un carico.",
  passi = {
    { "Forma un occhiello", "Tieni la parte fissa nella sinistra. Con il capo fai un occhiello, passando sopra la parte fissa.", img = 'otto1' },
    { "Gira dietro", "Porta il capo dietro la parte fissa.", img = 'otto2' },
    { "Infila", "Passa il capo dentro l'occhiello, da davanti verso dietro.", img = 'otto3' },
    { "Stringi", "Tira il capo e la parte fissa. Se il nodo ha la forma di un 8 è giusto. Lascia un capo lungo almeno un palmo.", img = 'otto4' },
  } },

{ id = 'gassa', titolo = "Gassa d'amante", sez = 'nodi', livello = 2, tempo = '3 min',
  intro = "Il re dei nodi: un'asola fissa che non si stringe sotto carico e si scioglie facilmente. Per legare la corda a un albero, la dorsale del tarp, una persona.",
  attenzione = "Non va bene per l'arrampicata se non è assicurata: lì si usa il nodo a otto inseguito.",
  passi = {
    { "La tana", "Sulla parte fissa fai un piccolo occhiello, con la parte verso il capo che passa sopra. È la tana del coniglio. Il resto della corda forma la grande asola in basso.", img = 'gassa1' },
    { "Il coniglio esce dalla tana", "Il capo (il coniglio) sale dentro l'occhiello, da sotto.", img = 'gassa2' },
    { "Gira intorno all'albero", "Il capo passa dietro la parte fissa (l'albero).", img = 'gassa3' },
    { "Torna nella tana", "Il capo scende di nuovo dentro l'occhiello, accanto a sé stesso.", img = 'gassa4' },
    { "Stringi", "Tieni insieme il capo e il lato dell'asola, e tira la parte fissa. Il capo deve restare lungo almeno un palmo.", img = 'gassa5' },
  } },

{ id = 'piano', titolo = 'Nodo piano', sez = 'nodi', livello = 1, tempo = '1 min',
  intro = "Per unire i due capi della stessa corda intorno a qualcosa: un fascio di legna, una benda, il sacco a pelo.",
  attenzione = "Non usarlo per unire due corde che devono reggere un peso: sotto strappi può sciogliersi.",
  passi = {
    { "Destra sopra sinistra", "Incrocia il capo destro sopra il sinistro e fallo passare sotto: è il primo mezzo nodo." },
    { "Sinistra sopra destra", "Ora il capo che è a sinistra va sopra quello di destra, e poi sotto." },
    { "Controlla", "I due capi devono uscire dallo stesso lato del nodo, paralleli alle loro parti. Se escono storti hai fatto il nodo della nonna, che scivola: rifallo." },
    { "Sciogli", "Si scioglie tirando uno dei capi indietro verso la sua parte: il nodo si rovescia e scorre via." },
  } },

{ id = 'parlato', titolo = 'Nodo parlato', sez = 'nodi', livello = 1, tempo = '2 min',
  intro = "Per legare velocemente una corda a un palo o a un ramo, e regolarne la lunghezza.",
  attenzione = "Su un palo liscio o con strappi può scivolare: aggiungi un mezzo collo con il capo intorno alla parte fissa.",
  passi = {
    { "Primo giro", "Passa la corda intorno al palo, da davanti a dietro, e riportala davanti." },
    { "La croce", "Fai passare il capo sopra la parte fissa: si forma una X." },
    { "Secondo giro", "Fai un secondo giro intorno al palo, sotto al primo, sempre nella stessa direzione." },
    { "Infila", "Passa il capo sotto il secondo giro, al centro della X: esce accanto alla parte fissa, in direzione opposta." },
    { "Stringi", "Tira i due capi in direzioni opposte. Le due spire devono essere affiancate e la X al centro." },
  } },

{ id = 'tendicavo', titolo = 'Tendicavo', sez = 'nodi', livello = 2, tempo = '3 min',
  intro = "Il nodo dei tiranti: scorre per tendere la corda e si blocca da solo sotto tensione. Per i tiranti della tenda e del tarp.",
  passi = {
    { "Intorno al picchetto", "Passa il capo intorno al picchetto (o all'albero) e riportalo verso la parte tesa della corda, lasciando un'asola." },
    { "Due giri dentro", "Avvolgi il capo due volte intorno alla parte tesa, lavorando verso il picchetto, dentro l'asola." },
    { "Un giro fuori", "Porta il capo oltre i due giri, fuori dall'asola, e fai un altro giro intorno alla parte tesa. Infila il capo sotto quest'ultimo giro." },
    { "Tendi", "Stringi i giri. Fai scorrere il nodo lungo la corda per tendere: sotto carico si blocca. Per allentare, fallo scorrere con le dita." },
  } },

{ id = 'prusik', titolo = 'Prusik', sez = 'nodi', livello = 3, tempo = '3 min',
  intro = "Un nodo autobloccante fatto con un anello di cordino intorno a una corda più grossa: scorre quando è scarico e si blocca sotto carico. Per appendere oggetti alla dorsale del tarp e spostarli.",
  serve = "Un anello di cordino di diametro più piccolo della corda (circa i due terzi).",
  attenzione = "Per l'arrampicata e la risalita su corda serve un corso: non imparare dalle immagini.",
  passi = {
    { "L'anello", "Chiudi un pezzo di cordino lungo un metro con un nodo inglese doppio: ottieni un anello." },
    { "Il primo giro", "Appoggia l'anello dietro la corda, passa un capo dell'anello davanti e infilalo dentro l'altro capo." },
    { "Tre giri", "Ripeti, avvolgendo l'anello intorno alla corda e dentro sé stesso, per tre giri. Le spire devono restare dentro l'anello, ordinate e parallele." },
    { "Prova", "Sistema le spire senza incroci. Tira l'anello: deve bloccarsi. Spingi il nodo con la mano: deve scorrere. Se scivola sotto carico, aggiungi un giro." },
  } },

------------------------------------------------------------------ trappole

{ id = 'regole', titolo = 'Prima di tutto: la legge', sez = 'trappole', livello = 1, tempo = '3 min',
  intro = "Leggi questa pagina prima delle altre trappole.",
  passi = {
    { "In Italia è vietato", "Cacciare con trappole, lacci e trabocchetti è vietato (legge 157 del 1992): è bracconaggio, punito anche con l'arresto. Nei parchi è vietato anche disturbare gli animali. La pesca richiede la licenza e ha regole precise." },
    { "Solo in una vera emergenza", "Queste tecniche sono per una situazione in cui la vita è in pericolo e non c'è altro modo. In Italia, in una montagna normale, i soccorsi arrivano in ore o giorni: per sopravvivere non serve cacciare. Chiama il 112." },
    { "Il cibo viene per ultimo", "Si resiste settimane senza mangiare. Riparo, calore, acqua e farsi trovare sono molto più importanti, e cacciare consuma energie e acqua." },
    { "Sicurezza e rispetto", "Una trappola può ferire una persona, un cane, un animale protetto. Non lasciarla mai incustodita, segna dove l'hai messa e smontala sempre prima di andare via." },
  } },

{ id = 'quattro', titolo = 'Trappola a quattro', sez = 'trappole', livello = 3, tempo = '1 ora',
  attenzione = "Solo in una vera emergenza di sopravvivenza: in Italia è vietata. Armala con le mani sempre fuori da sotto il peso.",
  intro = "Tre bastoncini incastrati a forma di 4 reggono una pietra piatta. Quando un piccolo animale tocca l'esca, il 4 si scompone e la pietra cade. Insegna a intagliare tacche precise.",
  serve = "Coltello, tre bastoncini dritti spessi un dito, una pietra piatta pesante.",
  passi = {
    { "I tre pezzi", "Montante: 30 cm, verticale, con la cima tagliata a scalpello piatto. Asta: 30 cm, la diagonale. Braccio: 40 cm, orizzontale, porterà l'esca.", img = 'quattro1' },
    { "Le tacche", "Asta: una tacca vicino a un capo, che si appoggia sulla cima del montante, e l'altro capo a scalpello. Braccio: una tacca quadrata a metà, che aggancia il montante, e una tacca in punta dove si infila lo scalpello dell'asta.", img = 'quattro2' },
    { "Monta il 4", "Tieni il montante verticale. Aggancia il braccio orizzontale con la sua tacca a metà del montante. Appoggia l'asta in diagonale: la sua tacca sulla cima del montante, lo scalpello nella tacca in punta al braccio. Insieme formano un 4.", img = 'quattro3' },
    { "Appoggia la pietra", "Appoggia un bordo della pietra a terra e l'altro sulla cima dell'asta: il peso tiene il 4 in tensione. Le mani sempre fuori da sotto la pietra.", img = 'quattro4' },
    { "Come scatta", "Un tocco al braccio lo sgancia dal montante: il 4 si scompone e la pietra cade. Provala più volte con un ramo, da lontano. Controllala spesso e smontala prima di andare via.", img = 'quattro5' },
  } },

{ id = 'laccio', titolo = 'Il laccio', sez = 'trappole', livello = 2, tempo = '20 min',
  attenzione = "Solo in una vera emergenza di sopravvivenza: in Italia è vietato. Un laccio dimenticato continua a uccidere: smontalo sempre.",
  intro = "Il metodo più semplice e antico: un cappio di filo sottile lungo un passaggio usato dagli animali.",
  serve = "Filo sottile e rigido (o cordino fine), un paletto.",
  passi = {
    { "Trova il passaggio", "Cerca i sentieri degli animali: tracce, erba schiacciata, passaggi sotto i cespugli, vicino all'acqua. Il laccio va dove l'animale è costretto a passare." },
    { "Il cappio", "Fai un piccolo occhiello a un capo del filo e passa l'altro capo dentro: ottieni un cappio che si stringe scorrendo." },
    { "La misura", "Il cappio deve essere largo come una mano per un animale piccolo, con il bordo basso a un pugno da terra." },
    { "Fissalo", "Lega l'altro capo a un paletto ben piantato o a un ramo robusto. Metti dei rametti ai lati del passaggio perché l'animale passi proprio nel cappio." },
    { "Controlla e smonta", "Controlla almeno due volte al giorno. Quando te ne vai, smonta tutto e porta via il filo." },
  } },

{ id = 'pesca', titolo = 'Pesca di sopravvivenza', sez = 'trappole', livello = 2, tempo = '',
  attenzione = "In Italia pescare richiede la licenza e rispetta stagioni e misure: questo è solo per l'emergenza.",
  intro = "Nei torrenti di montagna ci sono trote: con una lenza e un amo di fortuna si può pescare senza canna.",
  serve = "Filo o cordino sottile, un amo (o una spina, un osso, un fermaglio), esca.",
  passi = {
    { "L'amo di fortuna", "Una spina di rovo o di biancospino legata a un rametto, un fermaglio piegato, oppure un bastoncino appuntito ai due capi legato al centro: l'esca lo nasconde e il pesce lo inghiotte di traverso." },
    { "L'esca", "Vermi e larve sotto i sassi e nel legno marcio, insetti, cavallette. Mettila in modo che copra la punta." },
    { "Dove pescare", "Le trote stanno ferme dove la corrente rallenta: dietro i massi, nelle buche profonde, sotto le rive. Avvicinati piano, senza farti vedere e senza ombra sull'acqua." },
    { "La lenza", "Lega la lenza a un ramo lungo e flessibile. Lascia che l'esca scenda con la corrente verso la buca." },
    { "Linee fisse", "Più lenze legate a rami sulla riva pescano mentre fai altro. Controllale spesso e toglile tutte prima di andare via." },
  } },


------------------------------------------------------------------ bushcraft (altre)

{ id = 'tipifuoco', titolo = 'Tipi di fuoco', sez = 'sopravvivenza', livello = 1, tempo = '',
  intro = "Ogni fuoco ha il suo scopo: accendere in fretta, cucinare, scaldare tutta la notte. La preparazione (esca, legnetti, legna) è sempre la stessa.",
  attenzione = "Valgono le stesse regole di 'Accendere il fuoco': divieti regionali, terra nuda, niente rami sopra, sempre spento con acqua.",
  passi = {
    { "Tepee (a cono)", "Il più facile da accendere: i legnetti a cono intorno all'esca. Brucia alto e veloce, buono per partire e per far bollire l'acqua in fretta. Quando crolla diventa una base di brace.", img = 'tipi1' },
    { "Capanna (a catasta)", "Due legni paralleli, poi due di traverso sopra, e così via come una capanna di tronchi, con l'esca e un piccolo tepee al centro. Brucia regolare e lascia tanta brace: il migliore per cucinare.", img = 'tipi2' },
    { "Stella", "Quattro o cinque tronchi grossi a raggiera con le punte che si toccano al centro, dove arde il fuoco. Man mano che bruciano li spingi verso il centro. Consuma poco: dura tutta la notte con poca sorveglianza.", img = 'tipi3' },
    { "Con il riflettore", "Dietro il fuoco una parete di tronchi verdi appoggiati a due paletti, o una roccia: rimanda il calore verso il riparo. Il fuoco va a un passo e mezzo dall'entrata del riparo." },
    { "Quale scegliere", "Per partire: tepee. Per cucinare: capanna, e si cucina sulla brace, non sulla fiamma. Per la notte: stella, con un riflettore. Con il vento forte: il fuoco Dakota (vedi il tutorial)." },
  } },

{ id = 'dakota', titolo = 'Il fuoco Dakota', sez = 'sopravvivenza', livello = 2, tempo = '30 min',
  intro = "Un fuoco dentro una buca, con un tunnel per l'aria: col vento non si spegne, fa poco fumo, si vede poco e consuma poca legna. Ottimo per cucinare.",
  serve = "Un coltello o una paletta, un pentolino, legna corta e sottile.",
  attenzione = "Non scavare tra le radici (il fuoco può covare sottoterra) né sulla torba. Valgono i divieti sui fuochi.",
  passi = {
    { "La buca del fuoco", "Su terreno compatto, senza radici né sassi, togli la zolla d'erba e mettila da parte. Scava una buca larga e profonda come un avambraccio (25-30 cm), con le pareti dritte.", img = 'dakota1' },
    { "Il tunnel dell'aria", "A una spanna dalla prima, dalla parte da cui arriva il vento, scava una buca più stretta e falla scendere in diagonale fino a sbucare sul fondo della prima. Da lì entra l'aria.", img = 'dakota2' },
    { "Accendi sul fondo", "Metti l'esca e i legnetti sul fondo della buca grande e accendi. Il tiraggio del tunnel ravviva la fiamma da solo: alimenta dall'alto con legna corta.", img = 'dakota3' },
    { "Cucina", "Appoggia il pentolino su due bastoni verdi messi di traverso sulla buca. La fiamma resta concentrata sotto il fondo." },
    { "Chiudi", "Spegni con acqua, mescola, ancora acqua. Riempi le buche con la terra e rimetti la zolla: non resta traccia." },
  } },

{ id = 'archetto', titolo = "Fuoco ad archetto", sez = 'sopravvivenza', livello = 3, tempo = '1-2 ore',
  intro = "Accendere il fuoco per attrito, senza fiammiferi: un fuso che gira su una tavoletta finché la polvere di legno diventa brace. È la tecnica più impegnativa: provala a casa prima di averne bisogno.",
  serve = "Coltello, cordino, legno secco morto e tenero (tiglio, salice, pioppo, nocciolo), un sasso o un legno duro con un incavo.",
  passi = {
    { "I quattro pezzi", "Tavoletta: spessa 2 cm, larga tre dita. Fuso: dritto, lungo una spanna, spesso un pollice, dello stesso legno, la punta in alto appuntita e quella in basso arrotondata. Archetto: un ramo un po' curvo lungo come il braccio, con il cordino legato non troppo teso. Impugnatura: un sasso o un legno duro con un incavo.", img = 'archetto1' },
    { "Il primo foro", "Con la punta del coltello fai un piccolo incavo sulla tavoletta, a un dito dal bordo. Lì appoggerai il fuso." },
    { "La posizione", "In ginocchio sul destro, il piede sinistro sulla tavoletta vicino al foro. Avvolgi una volta il cordino intorno al fuso. Il fuso verticale nel foro, l'impugnatura sopra, il polso sinistro bloccato contro lo stinco.", img = 'archetto2' },
    { "Brucia il foro", "Muovi l'archetto avanti e indietro con colpi lunghi e regolari, usando tutto il cordino. Quando il foro è nero e largo come il fuso, fermati." },
    { "La tacca", "Taglia una tacca a V dal bordo della tavoletta fino al centro del foro, larga come un ottavo di torta. Lì si raccoglie la polvere che diventerà brace. Metti sotto un pezzo di corteccia." },
    { "La brace", "Ricomincia: prima piano, poi più veloce e premendo di più. Quando esce tanto fumo continua per altri 20 colpi. Se la polvere nera nella tacca continua a fumare da sola, hai la brace." },
    { "Dal carbone alla fiamma", "Sposta la brace con la corteccia dentro il nido d'esca, chiudilo intorno e soffia piano e a lungo finché prende fiamma. Poi il fuoco come al solito." },
  } },

{ id = 'batoning', titolo = 'Spaccare la legna col coltello', sez = 'sopravvivenza', livello = 2, tempo = '10 min',
  intro = "Il batoning: spaccare un ciocco battendo il dorso del coltello con un bastone. Serve per arrivare al legno asciutto quando fuori è tutto bagnato.",
  serve = "Un coltello a lama fissa robusto (lama e manico in un pezzo solo), un bastone duro da battere.",
  attenzione = "Mai con un coltello pieghevole. Legna più sottile della lunghezza della lama e senza nodi. Le gambe fuori dalla traiettoria.",
  passi = {
    { "Prepara il ceppo", "Metti un ciocco largo come base. Appoggia sopra in piedi il pezzo da spaccare, lungo al massimo come il tuo avambraccio." },
    { "Appoggia la lama", "Appoggia il filo della lama al centro della testa del pezzo, dritta, con la punta che sporge dall'altra parte di qualche dito.", img = 'baton1' },
    { "Batti", "Con il bastone batti il dorso della lama vicino al manico, con colpi decisi. Quando la lama è entrata, batti sulla punta che sporge, così scende dritta.", img = 'baton2' },
    { "Apri", "Continua finché il pezzo si divide. Se la lama si incastra in un pezzo lungo, infila un cuneo di legno nella spaccatura e batti quello." },
    { "Ancora a metà", "Spacca ogni metà in due e poi ancora: i quarti interni sono asciutti anche dopo giorni di pioggia. Ne ricavi esca, legnetti e il bastoncino piumato." },
  } },

{ id = 'cordame', titolo = 'Cordino di fibre naturali', sez = 'sopravvivenza', livello = 3, tempo = '1 ora',
  intro = "Con le fibre di ortica, di tiglio o di erba lunga si intreccia un cordino robusto: il metodo della torsione inversa, usato in tutto il mondo da migliaia di anni.",
  serve = "Fibre lunghe e flessibili: steli di ortica secchi, corteccia interna di tiglio o salice, erbe lunghe.",
  passi = {
    { "Prepara le fibre", "Ortica: schiaccia lo stelo secco per il lungo, apri, togli la parte legnosa e tieni le fibre esterne. Tiglio: la corteccia interna, lasciata a bagno qualche giorno, si sfoglia in strisce." },
    { "Piega il fascio", "Prendi un fascio di fibre spesso come un fiammifero e piegalo non a metà, ma a un terzo della lunghezza: le giunte cadranno in punti diversi." },
    { "Il primo nodino", "Tieni la piega tra pollice e indice e torci il ramo di sopra lontano da te finché si arriccia e fa un piccolo nodo alla piega." },
    { "Torci e avvolgi", "Torci il ramo di sopra lontano da te, poi portalo sopra l'altro verso di te. Ora quello che era sotto è sopra: torci, avvolgi, e ripeti. Le due torsioni opposte si bloccano a vicenda." },
    { "Allunga", "Quando un ramo sta per finire, sovrapponi un nuovo ciuffo di fibre per qualche centimetro e continua a torcerli insieme." },
    { "Finisci e prova", "Chiudi con un nodo semplice e taglia i ciuffi sporgenti. Prova a tirare: un buon cordino non si srotola e regge molto più di quanto sembri." },
  } },

{ id = 'gancio', titolo = 'Il gancio per il pentolino', sez = 'sopravvivenza', livello = 2, tempo = '30 min',
  intro = "Appendere il pentolino sopra il fuoco con legni intagliati: due forcelle, una traversa e un gancio regolabile.",
  serve = "Coltello, legno verde (che brucia poco): due rami a forcella, una traversa, un ramo con un moncone.",
  passi = {
    { "Le due forcelle", "Taglia due rami verdi con una forcella in cima, lunghi come il tuo braccio. Appuntiscili in basso e piantali ai due lati del fuoco, ben dritti.", img = 'gancio1' },
    { "La traversa", "Un ramo verde dritto e robusto appoggiato nelle due forcelle, sopra il fuoco." },
    { "Il gancio", "Cerca un ramo con un moncone laterale: taglia il moncone lungo due dita e inclinato verso l'alto. Quello è il gancio dove si appende il manico del pentolino." },
    { "Regolare l'altezza", "In cima al gancio intaglia una tacca e appendilo alla traversa. Per avere più altezze, fai due o tre tacche a distanze diverse: più in basso cuoce, più in alto tiene in caldo." },
    { "Controlla", "Il legno verde col tempo si secca e può bruciare: guardalo ogni tanto e cambia la traversa se si annerisce." },
  } },

------------------------------------------------------------------ nodi e legature (altri)

{ id = 'mezzicolli', titolo = 'Due mezzi colli', sez = 'nodi', livello = 1, tempo = '1 min',
  intro = "Il modo più semplice per fissare una corda a un albero, a un palo o a un anello, e si scioglie subito. Chiude anche molti altri nodi.",
  passi = {
    { "Intorno al palo", "Passa il capo intorno all'albero o al palo, e riportalo verso la parte fissa." },
    { "Primo mezzo collo", "Passa il capo intorno alla parte fissa e infilalo dentro l'occhiello che si è formato." },
    { "Secondo mezzo collo", "Ripeti uguale, più in là sulla parte fissa, girando nello stesso verso. Se giri al contrario il nodo scivola." },
    { "Stringi", "Fai scorrere i due mezzi colli contro il palo e tira la parte fissa." },
  } },

{ id = 'camionista', titolo = 'Nodo del camionista', sez = 'nodi', livello = 2, tempo = '3 min',
  intro = "Tende una corda con la forza di una carrucola: per la dorsale del tarp, per legare un carico. Si scioglie con uno strattone.",
  passi = {
    { "Il primo ancoraggio", "Lega un capo della corda al primo albero con una gassa d'amante o due mezzi colli." },
    { "L'asola a metà", "Più o meno a due terzi della distanza, fai un'asola sulla corda: un occhiello ripiegato chiuso con un mezzo nodo (o un'asola a farfalla se la sai fare)." },
    { "Intorno al secondo albero", "Passa il capo libero intorno al secondo albero e riportalo indietro, facendolo entrare nell'asola." },
    { "Tira", "Tira il capo verso il secondo albero: l'asola fa da carrucola e la corda si tende il doppio di quanto tireresti a mano." },
    { "Blocca", "Tenendo la tensione con una mano, chiudi con due mezzi colli intorno ai due rami della corda, sotto l'asola. Per scioglierlo veloce, fai l'ultimo mezzo collo con un'ansa (ganciato)." },
  } },

{ id = 'bandiera', titolo = 'Nodo di bandiera', sez = 'nodi', livello = 1, tempo = '2 min',
  intro = "Per unire due corde, anche di spessore diverso: quella che avanza del tarp con un cordino, due corde corte per farne una lunga.",
  passi = {
    { "L'ansa", "Con la corda più grossa fai un'ansa, piegandola a U." },
    { "Entra", "Passa il capo della corda sottile dentro l'ansa, da sotto." },
    { "Gira dietro", "Portalo intorno a tutti e due i rami dell'ansa, passando dietro." },
    { "Sotto sé stesso", "Infila il capo sotto sé stesso, ma sopra l'ansa: non dentro l'ansa." },
    { "Stringi e controlla", "Stringi tirando le parti lunghe. I due capi corti devono uscire dallo stesso lato. Per più sicurezza fai un secondo giro (nodo di bandiera doppio)." },
  } },

{ id = 'quadra', titolo = 'Legatura quadra', sez = 'nodi', livello = 2, tempo = '5 min',
  intro = "Unisce due pali incrociati ad angolo retto: per tavoli, rastrelliere, le strutture dei ripari. È la legatura più usata.",
  serve = "Due pali, 3-4 metri di cordino.",
  passi = {
    { "Il nodo d'inizio", "Fai un nodo parlato sul palo verticale, appena sotto il punto dove appoggerai la traversa. Avvolgi il capo corto intorno alla corda per non lasciarlo libero.", img = 'quadra1' },
    { "I giri", "Porta la corda sopra la traversa, dietro il palo verticale, sotto la traversa dall'altra parte e di nuovo dietro il verticale. Ripeti 3-4 volte tirando forte: sulla traversa i giri vanno uno fuori dall'altro, sul verticale uno dentro l'altro.", img = 'quadra2' },
    { "Le strozzature", "Ora gira la corda tra i due pali, intorno a tutti i giri: 2-3 strozzature, tirate più che puoi. Sono loro che rendono la legatura rigida.", img = 'quadra3' },
    { "Chiudi", "Chiudi con un nodo parlato sulla traversa. La legatura non deve muoversi se scuoti i pali." },
  } },

{ id = 'diagonale', titolo = 'Legatura diagonale', sez = 'nodi', livello = 2, tempo = '5 min',
  intro = "Per due pali incrociati che tendono ad allontanarsi, come le diagonali di un telaio. Li tira uno contro l'altro.",
  serve = "Due pali, 3-4 metri di cordino.",
  passi = {
    { "Il nodo di legname", "Intorno ai due pali, nel punto dove si incrociano, fai un nodo di legname: passa la corda intorno a entrambi, gira il capo intorno alla parte fissa e poi su sé stesso tre volte. Tirando, stringe i pali insieme." },
    { "Tre giri su una diagonale", "Avvolgi la corda tre volte intorno all'incrocio in diagonale, nello stesso verso del nodo di legname." },
    { "Tre giri sull'altra", "Ora tre giri sull'altra diagonale, a formare una X." },
    { "Strozzature e chiusura", "Due strozzature tra i pali, intorno ai giri, e chiudi con un nodo parlato su un palo." },
  } },

{ id = 'treppiede', titolo = 'Legatura a treppiede', sez = 'nodi', livello = 2, tempo = '10 min',
  intro = "Tre pali legati in cima che si aprono a treppiede: per appendere il pentolino sul fuoco, fare la struttura di un riparo o tenere alto lo zaino.",
  serve = "Tre pali di lunghezza simile, 3-4 metri di cordino.",
  passi = {
    { "Affianca i pali", "Metti i tre pali affiancati a terra, con quello in mezzo rovesciato (la punta grossa dalla parte opposta). Le cime sporgono tutte di una spanna oltre la legatura." },
    { "Inizia", "Fai un nodo parlato su un palo esterno, vicino alla cima." },
    { "I giri", "Avvolgi la corda intorno ai tre pali per 6-8 giri, passando sopra e sotto i pali alternati, senza stringere troppo: devono poter ruotare per aprirsi." },
    { "Le strozzature", "Fai due strozzature in ciascuno dei due spazi tra i pali, intorno ai giri." },
    { "Chiudi e apri", "Chiudi con un nodo parlato sull'altro palo esterno. Alza il treppiede e apri le gambe, incrociando quello di mezzo: la legatura si stringe da sola.", img = 'treppiede1' },
  } },

{ id = 'cesoia', titolo = 'Legatura a cesoia', sez = 'nodi', livello = 2, tempo = '5 min',
  intro = "Due pali legati in cima che si aprono ad A: il bipode per la dorsale di un riparo quando mancano gli alberi, o la testata di una capanna.",
  serve = "Due pali, 2-3 metri di cordino.",
  passi = {
    { "Affianca", "Metti i due pali paralleli, uno accanto all'altro." },
    { "Inizia", "Fai un nodo parlato (o di legname) su un palo, a una spanna dalle cime." },
    { "I giri", "Avvolgi 6-8 giri intorno ai due pali insieme, non troppo stretti." },
    { "Strozzature e chiusura", "Due strozzature tra i pali e un nodo parlato sull'altro palo." },
    { "Apri ad A", "Apri i pali a forbice: la legatura si stringe. Con due bipodi e un palo di traverso hai il telaio di un riparo." },
  } },

------------------------------------------------------------------ tarp (altri)

{ id = 'tarp_corde', titolo = 'Preparare il tarp', sez = 'tarp', livello = 1, tempo = '30 min',
  intro = "Un tarp ben preparato a casa si monta in cinque minuti anche sotto la pioggia, con i guanti, al buio.",
  serve = "Il tarp, 15 m di cordino da 3-4 mm, 8 picchetti, 2 anelli di cordino sottile per i prusik.",
  passi = {
    { "Un cordino per ogni occhiello", "Lega ai quattro angoli e agli occhielli dei lati un cordino da 2 metri con una gassa d'amante. Avvolgili e tienili legati: non si aggrovigliano." },
    { "La dorsale", "Un cordino lungo 10-12 metri per la dorsale. Un capo già pronto con una gassa d'amante da passare intorno all'albero." },
    { "I prusik", "Sulla dorsale metti due anelli di cordino sottile con il nodo prusik (vedi Nodi). Agganci lì gli angoli del tarp e li fai scorrere per tenderlo." },
    { "Tendicavi ai picchetti", "Ai capi dei cordini degli angoli bassi farai un tendicavo intorno al picchetto: così regoli la tensione senza slegare niente." },
    { "Riporlo", "Arrotola il tarp sulla dorsale, con i cordini dentro. La prossima volta srotoli e tiri." },
  } },

{ id = 'tarp_a', titolo = 'Tarp a capanna (A)', sez = 'tarp', livello = 1, tempo = '15 min',
  intro = "Il tarp a cavallo della dorsale, i due lati a terra: una tenda senza fondo. Ripara da pioggia e vento da due lati.",
  serve = "Tarp, dorsale, 6 picchetti.",
  passi = {
    { "La dorsale bassa", "Tendi la dorsale tra due alberi a un metro da terra (o poco più alta della tua spalla da seduto), con il camionista per tenderla bene." },
    { "A cavallo", "Appoggia il tarp a metà sulla dorsale, con la linea centrale esattamente sopra la corda. Fissa i due occhielli centrali con i prusik.", img = 'tarpa1' },
    { "Picchetta i lati", "Tira un lato verso terra e picchetta i due angoli, poi l'altro lato. Stessa inclinazione a destra e a sinistra." },
    { "Tendi", "Regola con i tendicavi finché il telo è liscio. Se un'estremità è verso il vento, abbassala o chiudila con un sacco." , img = 'tarpa2' },
  } },

{ id = 'tarp_diamante', titolo = 'Tarp a diamante', sez = 'tarp', livello = 2, tempo = '10 min',
  intro = "Il tarp girato a rombo, con un solo albero: un angolo in alto, quello opposto a terra. Veloce, leggero, perfetto per una persona.",
  serve = "Tarp quadrato, un albero, 3-4 picchetti, un bastone (facoltativo).",
  passi = {
    { "L'angolo alto", "Lega un angolo del tarp all'albero, all'altezza del petto, con due mezzi colli o un tendicavo.", img = 'diam1' },
    { "L'angolo basso", "Tira l'angolo opposto lontano dall'albero e picchettalo a terra, dalla parte da cui arriva il vento." },
    { "I due lati", "Tendi i due angoli laterali verso il basso e in fuori, con i cordini ai picchetti. Più li abbassi, più ripara; più li alzi, più spazio hai.", img = 'diam2' },
    { "Dentro", "Si dorme con la testa verso l'albero, dove il telo è più alto. Con un bastone sotto il centro di un lato guadagni spazio." },
  } },

{ id = 'tarp_vomere', titolo = 'Tarp a punta (piramide)', sez = 'tarp', livello = 2, tempo = '15 min',
  intro = "Il 'vomere': tre angoli a terra e uno in alto, appeso a un albero o a un bastone. Chiuso su tre lati, tiene il vento meglio di tutti.",
  serve = "Tarp quadrato, 4 picchetti, un albero o un bastone lungo 1,3 m.",
  passi = {
    { "L'angolo in alto", "Lega un angolo del tarp all'albero a un metro e mezzo, o appoggialo sulla punta di un bastone piantato dritto e tenuto da un tirante.", img = 'vomere1' },
    { "I due angoli posteriori", "Picchetta i due angoli adiacenti a terra, ben tesi, dalla parte del vento: formano la schiena del riparo." },
    { "L'angolo davanti", "Il quarto angolo è l'entrata: picchettalo a terra per chiuderti dentro, oppure alzalo con un bastone per fare una veranda.", img = 'vomere2' },
    { "Sistema", "Tendi i lati con gli occhielli intermedi. Dentro c'è spazio per una persona e lo zaino." },
  } },

{ id = 'tarp_cucina', titolo = 'Tettoia per stare in piedi', sez = 'tarp', livello = 1, tempo = '15 min',
  intro = "Il tarp alto e quasi piatto, inclinato: una tettoia sotto cui cucinare, mangiare e aspettare che smetta di piovere.",
  serve = "Tarp, dorsale, 4 picchetti, due bastoni lunghi 1,5 m (facoltativi).",
  attenzione = "Il fornelletto sotto il tarp solo se il telo è alto e c'è tanta aria. Mai il fuoco sotto il telo.",
  passi = {
    { "La dorsale alta", "Tendi la dorsale tra due alberi più in alto della tua testa." },
    { "Il lato alto", "Appendi un lato lungo del tarp alla dorsale con i prusik, così che il telo stia tutto da una parte." },
    { "Il lato basso", "Tira l'altro lato in giù a un metro e mezzo, con i cordini ai picchetti. Deve pendere abbastanza da far scolare la pioggia da quel lato.", img = 'cucina1' },
    { "Più spazio", "Per alzare il lato basso, mettilo su due bastoni dritti tenuti da tiranti. Togli le pieghe: dove resta una conca, l'acqua si accumula e il telo cede." },
  } },

{ id = 'tarp_pioggia', titolo = 'Tarp col brutto tempo', sez = 'tarp', livello = 2, tempo = '',
  intro = "Le regole per restare asciutti con la pioggia battente e il vento forte.",
  passi = {
    { "Il lato al vento", "Il lato basso e chiuso del tarp va sempre verso il vento. Se il vento gira, abbassa quel lato fino a terra." },
    { "Niente conche", "Il telo deve essere teso e in pendenza ovunque. Una conca piena d'acqua pesa chili e può strappare gli occhielli." },
    { "I gocciolatoi", "La pioggia scende lungo la dorsale e i cordini fino a te. Lega un pezzetto di cordino sulla dorsale appena fuori dal tarp, che pende: l'acqua gocciola lì e non entra." },
    { "Il terreno", "Mai in una conca o sotto una pendenza da cui scende l'acqua. Meglio un piccolo rilievo, con uno strato di frasche sotto il materassino." },
    { "Ritendi", "Il telo bagnato si allunga: dopo un'ora di pioggia ritendi i tendicavi. Di notte allentali un poco se fa molto freddo: il nylon gelato si accorcia." },
  } },

------------------------------------------------------------------ trappole (altre)

{ id = 'paiute', titolo = 'Trappola Paiute', sez = 'trappole', livello = 3, tempo = '1 ora',
  attenzione = "Solo in una vera emergenza di sopravvivenza: in Italia è vietata. Le mani mai sotto la pietra mentre la armi.",
  intro = "Una variante della trappola a quattro che usa un cordino: scatta con un tocco leggerissimo ed è più sensibile. Inventata dal popolo Paiute del Nord America.",
  serve = "Coltello, quattro bastoncini, 20 cm di cordino, una pietra piatta pesante.",
  passi = {
    { "I pezzi", "Montante: 25 cm, verticale. Leva: 30 cm, appoggiata in cima al montante. Levetta: 5 cm, legata al cordino. Bastoncino dell'esca: lungo e sottile." },
    { "La leva", "Lega il cordino a un capo della leva. Appoggia la leva in cima al montante: un capo regge il bordo della pietra, dall'altro pende il cordino." },
    { "La levetta", "Lega la levetta all'altro capo del cordino e girala intorno al montante: il cordino tirato dalla leva la tiene premuta contro il montante." },
    { "L'esca", "Incastra il bastoncino dell'esca tra la punta della levetta e il bordo della pietra. Un tocco all'esca libera la levetta, la leva scatta e la pietra cade." },
    { "Prova e smonta", "Provala più volte con un ramo, da lontano. Controllala spesso e smontala sempre prima di andare via." },
  } },

{ id = 'molla', titolo = 'Laccio a molla', sez = 'trappole', livello = 3, tempo = '40 min',
  attenzione = "Solo in una vera emergenza di sopravvivenza: in Italia è vietato. Un ramo in tensione può colpire il viso: lavora di lato.",
  intro = "Il laccio legato a un ramo flessibile piegato: quando scatta, il ramo si raddrizza e solleva la preda, che non scappa e resta lontano da altri animali.",
  serve = "Cordino, filo per il cappio, coltello, un alberello flessibile vicino al passaggio, due pioli.",
  passi = {
    { "Il grilletto", "Due pioli con una tacca ciascuno che si incastrano tra loro: uno piantato a terra con la tacca verso il basso, l'altro legato al cordino con la tacca verso l'alto." },
    { "La molla", "Lega il cordino alla cima di un alberello flessibile e piegalo verso il passaggio degli animali." },
    { "Arma", "Incastra il piolo del cordino sotto quello piantato: la tensione dell'alberello li tiene agganciati." },
    { "Il cappio", "Al piolo del cordino lega il laccio, aperto sul passaggio come nel tutorial 'Il laccio'. Quando l'animale tira il cappio, i pioli si sganciano." },
    { "Controlla e smonta", "Controlla almeno due volte al giorno. Prima di andare via, sgancia la molla con un bastone e smonta tutto." },
  } },

{ id = 'nassa', titolo = 'Nassa per pesci', sez = 'trappole', livello = 2, tempo = '30 min',
  attenzione = "In Italia pescare con le nasse è vietato senza permesso: solo per l'emergenza.",
  intro = "Una trappola a imbuto: il pesce entra seguendo l'esca e non trova più l'uscita. La versione più semplice si fa con una bottiglia di plastica.",
  serve = "Una bottiglia grande di plastica (o rami sottili e cordino), coltello, esca, sassi.",
  passi = {
    { "Taglia la bottiglia", "Taglia la bottiglia dove inizia a stringersi. Capovolgi la parte del collo e infilala dentro il corpo: diventa l'imbuto d'entrata.", img = 'nassa1' },
    { "Fissa e fora", "Fissa l'imbuto con qualche punto di cordino. Fai dei buchi nel corpo, così l'acqua passa e la bottiglia affonda." },
    { "L'esca", "Dentro metti l'esca: vermi, pane, insetti." },
    { "Posizionala", "In acqua bassa e calma, vicino alla riva, con l'imbuto verso valle: il pesce risale la corrente. Tienila ferma con dei sassi." },
    { "Con i rami", "La versione grande si intreccia con rami sottili: un cono d'entrata dentro un cesto più lungo, legati con cordino. Il principio è lo stesso." },
  } },


}
