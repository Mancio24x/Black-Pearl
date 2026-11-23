-- Le tecniche, in tre sezioni. Ogni tutorial:
--   id, titolo, sez ('sopravvivenza', 'nodi', 'trappole'), livello (1-3), tempo
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

{ id = 'tarp', titolo = 'Riparo con il tarp', sez = 'sopravvivenza', livello = 1, tempo = '20 min',
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

}
