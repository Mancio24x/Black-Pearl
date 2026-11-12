# Taccuino della Bibbia e importazione dei vault

Base iniziale `v0.8.10`, ramo `fix/bible-notebook-isolation`. L'ultima richiesta di Leo ha priorità: la Home deve aprirsi sulle note personali, le note bibliche si leggono entrando nel taccuino in ordine biblico. Identificare il kit incluso e gestire le eventuali copie senza perdere modifiche o riferimenti. Il punto precedente riguarda percorsi e collegamenti del vault importato e il riepilogo dei libri disponibili.

## Ripresa

Controllare i commit e la PR del ramo prima di modificare file. Leggere `BibleStudyKit.kt`, `MainViewModel.kt`, DAO e test dei taccuini. In 0.8.10 la Home esclude solo l'ID salvato nelle preferenze e il seeder crea un taccuino nuovo se l'ID manca. Verificare installazione pulita, aggiornamento con ID mancante o obsoleto, due taccuini e note personali modificate. Confrontare lo ZIP fornito in privato con gli asset per titolo e percorso; non caricare i file dell'utente nel repo. Aggiornare qui dopo le verifiche con i risultati e il prossimo passo. Se il client indica circa il 5% di crediti residui, salvare i cambiamenti e aggiornare il passaggio di consegne prima di interrompersi.
