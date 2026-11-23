-- L'attrezzatura, nell'ordine in cui si carica lo zaino da 60 litri: dal fondo alla cima.
--
-- La regola: in fondo ciò che è leggero e serve solo la sera, al centro e contro la schiena ciò che
-- pesa, in cima e nelle tasche ciò che serve durante il cammino. Così il peso sta vicino al corpo e
-- in alto sulle spalle, e non devi svuotare lo zaino per trovare la giacca quando piove.
--
-- Ogni gruppo: { titolo, spiega, img (facoltativa), voci = { { id, nome, peso (grammi, circa), dove, perche, consiglio } } }
-- L'id serve a salvare la spunta: non cambiarlo.

return {

{ titolo = 'Gli zaini', img = 'zaino',
  spiega = "Prima si sceglie e si regola lo zaino grande: tutto il resto si dispone dentro di lui.",
  voci = {
    { id = 'zaino60', nome = 'Zaino 60 L', peso = 1800, dove = 'Sulle spalle',
      perche = "Porta tutto per più giorni: riparo, cucina, vestiti, cibo.",
      consiglio = "Carico, il peso va sui fianchi: allaccia prima la cintura sopra le ossa del bacino, poi stringi gli spallacci, poi i tiranti di carico in alto. Fodera l'interno con un sacco della spazzatura robusto: è il miglior coprizaino." },
    { id = 'zaino20', nome = 'Zaino 20 L', peso = 400, dove = 'Piegato in cima allo zaino grande',
      perche = "Al campo diventa lo zainetto per la vetta o per cercare acqua e legna, senza portarsi dietro tutto.",
      consiglio = "Dentro, per la giornata: acqua, filtro, cibo, pile, antipioggia, frontale e coltello." },
  } },

{ titolo = 'Addosso',
  spiega = "Quello che indossi non va nello zaino.",
  voci = {
    { id = 'scarpe', nome = 'Scarpe da trekking', peso = 1200, dove = 'Ai piedi',
      perche = "Proteggono le caviglie e tengono sul bagnato e sulle pietre.",
      consiglio = "Mai nuove il giorno della partenza: rodale prima in passeggiate brevi. La sera allenta i lacci e togli le solette per farle asciugare." },
  } },

{ titolo = '1. Il fondo',
  spiega = "Leggero, voluminoso, serve solo la sera: il sonno. Riempie il fondo e fa da cuscinetto.",
  voci = {
    { id = 'sacco', nome = 'Sacco a pelo', peso = 1100, dove = 'In fondo, nella sacca stagna',
      perche = "Senza un sonno caldo la notte in montagna è pericolosa, anche d'estate.",
      consiglio = "Sceglilo con la temperatura di comfort adatta alla notte più fredda prevista. Tienilo sempre asciutto: un sacco bagnato non scalda." },
    { id = 'tuta', nome = 'Pantalone tuta', peso = 350, dove = 'In fondo, col sacco a pelo',
      perche = "È il pantalone asciutto per la sera e per dormire.",
      consiglio = "Non usarlo per camminare: deve restare sempre asciutto." },
    { id = 'calze', nome = 'Calze di ricambio', peso = 100, dove = 'In fondo, nella sacca stagna',
      perche = "Piedi asciutti vuol dire niente vesciche e niente freddo.",
      consiglio = "Un paio per la notte da tenere sempre asciutto. Quelle bagnate si asciugano di notte dentro il sacco, sul petto." },
    { id = 'maglietta', nome = 'Maglietta di ricambio', peso = 150, dove = 'In fondo, nella sacca stagna',
      perche = "Al campo togli subito quella sudata: il sudore che si raffredda ruba calore.",
      consiglio = "Meglio lana merino o sintetica. Il cotone bagnato non scalda più." },
  } },

{ titolo = '2. Il centro, contro la schiena',
  spiega = "Le cose pesanti: vicino alla schiena e all'altezza delle scapole, così lo zaino non ti tira indietro.",
  voci = {
    { id = 'cibo', nome = 'Cibo', peso = 800, dove = 'Al centro, contro la schiena',
      perche = "Energia per camminare e calore per la notte.",
      consiglio = "Circa 600-800 g al giorno di cibo secco e calorico: frutta secca, pasta, riso, avena, cioccolato. Gli snack del giorno nelle tasche della cintura." },
    { id = 'fornello', nome = 'Fornelletto', peso = 350, dove = 'Al centro, con la cartuccia dentro il pentolino',
      perche = "Pasti caldi e acqua bollita, cioè sicura da bere.",
      consiglio = "Porta l'accendino anche se ha l'accensione automatica. Mai cucinare dentro la tenda chiusa: monossido di carbonio." },
    { id = 'acqua', nome = 'Acqua', peso = 2000, dove = 'Sacca idrica contro la schiena, o borracce nelle tasche laterali',
      perche = "È la cosa più pesante e la più importante.",
      consiglio = "Parti con 1,5-2 litri e studia sulla carta dove riempire. Bevi poco e spesso, prima di avere sete." },
    { id = 'tenda', nome = 'Tenda', peso = 1600, dove = 'Al centro; i paletti in verticale lungo un fianco',
      perche = "Riparo da pioggia, vento, insetti e freddo.",
      consiglio = "Montala a casa almeno una volta. Se è bagnata, mettila nella tasca esterna per non bagnare il resto." },
    { id = 'materassino', nome = 'Materassino', peso = 450, dove = 'Gonfiabile dentro in verticale; in espanso fuori, sotto o di lato',
      perche = "Isola dal terreno: è da sotto che si perde più calore di notte.",
      consiglio = "Dentro lo zaino, arrotolato lungo la schiena, fa anche da schienale. Controlla le valvole prima di partire." },
  } },

{ titolo = '3. La cima',
  spiega = "Quello che può servire all'improvviso: pioggia, vento, una sosta al freddo.",
  voci = {
    { id = 'antipioggia', nome = 'Antipioggia', peso = 350, dove = 'In cima, sopra tutto',
      perche = "Il tempo in montagna cambia in mezz'ora. Ripara anche dal vento.",
      consiglio = "Giacca, e se puoi anche i pantaloni. Indossala ai primi goccioloni, non quando sei già bagnato." },
    { id = 'pile', nome = 'Pile', peso = 350, dove = 'In cima',
      perche = "Lo strato che scalda anche quando è umido.",
      consiglio = "Mettilo nelle soste e toglilo prima di ripartire: si cammina con un po' di freddo." },
    { id = 'piumino', nome = 'Piumino', peso = 400, dove = 'In cima, compresso nella sua sacca',
      perche = "Il calore della sera e delle soste lunghe, in pochissimo peso.",
      consiglio = "La piuma bagnata non scalda: sotto la pioggia va sotto l'antipioggia, mai sopra." },
    { id = 'tarp', nome = 'Tarp', peso = 400, dove = "In cima o nella tasca esterna",
      perche = "Un telo per il riparo veloce: pausa sotto la pioggia, cucina coperta, o riparo per la notte.",
      consiglio = "Tienilo con i cordini già legati agli angoli: lo monti in pochi minuti (vedi Tecniche, Riparo con il tarp)." },
    { id = 'filtro', nome = 'Filtro acqua', peso = 100, dove = 'In cima o nella tasca del cappuccio',
      perche = "Rende bevibile l'acqua dei torrenti senza doverla bollire.",
      consiglio = "Non filtra i virus né le sostanze chimiche. D'inverno tienilo nel sacco a pelo la notte: se gela si rompe." },
  } },

{ titolo = '4. Tasche e cintura',
  spiega = "Le piccole cose che servono subito, senza togliere lo zaino.",
  voci = {
    { id = 'frontale', nome = 'Torcia frontale', peso = 90, dove = 'Tasca del cappuccio',
      perche = "Mani libere al buio: montare la tenda, cucinare, camminare se fai tardi.",
      consiglio = "Batterie cariche e una di scorta. Il blocco tasti evita che si accenda nello zaino." },
    { id = 'coltello', nome = 'Coltello', peso = 150, dove = 'Tasca della cintura o del cappuccio',
      perche = "Legna per il fuoco, corda, cibo, riparazioni.",
      consiglio = "Lama fissa robusta per il bushcraft. Attenzione alle leggi: portarlo senza un motivo è vietato, in escursione il motivo c'è." },
    { id = 'corda', nome = 'Corda', peso = 200, dove = 'Tasca esterna',
      perche = "Riparo, stendere i panni, appendere il cibo, riparazioni.",
      consiglio = "15-20 m di cordino da 3-4 mm (paracord). Vedi Tecniche, Nodi." },
  } },

{ titolo = 'Ti consiglio di aggiungere', facoltativo = true,
  spiega = "Non erano nella tua lista, ma in montagna fanno la differenza in un'emergenza.",
  voci = {
    { id = 'kit', nome = 'Kit di primo soccorso', peso = 250, dove = 'In cima',
      perche = "Vesciche, tagli, distorsioni.",
      consiglio = "Cerotti per vesciche, garze, disinfettante, benda elastica, antidolorifico, i tuoi farmaci." },
    { id = 'telo', nome = 'Telo termico', peso = 60, dove = 'Tasca del cappuccio',
      perche = "Pesa nulla e trattiene il calore del corpo in caso di incidente.",
      consiglio = "Avvolgilo intorno al corpo, sotto la giacca." },
    { id = 'fuoco', nome = 'Accendino e acciarino', peso = 60, dove = 'In due posti diversi',
      perche = "Il fuoco non si improvvisa: due modi per accenderlo, separati.",
      consiglio = "L'acciarino (ferro-cerio) funziona anche bagnato e al freddo." },
    { id = 'mappa', nome = 'Carta e bussola', peso = 100, dove = 'Tasca della cintura',
      perche = "Il telefono si scarica o non prende.",
      consiglio = "Carta topografica 1:25.000 della zona, in una busta impermeabile." },
    { id = 'fischietto', nome = 'Fischietto', peso = 10, dove = 'Legato allo spallaccio',
      perche = "Si sente molto più lontano della voce e non stanca.",
      consiglio = "Il segnale di soccorso alpino: 6 fischi in un minuto, poi un minuto di pausa. Si risponde con 3." },
  } },

}
