//zdielana classa, uchovava aktivnych handlerov - napr. aktivne sockety 
package utb.fai;

import java.util.*;

public class ActiveHandlers {
    private static final long serialVersionUID = 1L;
    private HashSet<SocketHandler> activeHandlersSet = new HashSet<SocketHandler>();

    /**
     * sendMessageToAll - Posle zprávu vsem aktivním klientùm krome sebe sama
     * 
     
    //z jedneho handleru preposle spravu vsetkym aktivnym klientom, okrem samych seba 
     * @param sender  - reference odesílatele
     * @param message - retezec se zprávou
     */
    synchronized void sendMessageToAll(SocketHandler sender, String message) {
        for (SocketHandler handler : activeHandlersSet) // pro vsechny aktivní handlery
            if (handler != sender) {
                if (!handler.messages.offer(message)) // zkus pridat zprávu do fronty jeho zpráv
                    System.err.printf("Client %s message queue is full, dropping the message!\n", handler.clientID);
            }
    }

    /**
     * add peidá do mnoziny aktivních handleru nový handler.
     * Metoda je sychronizovaná, protoe HashSet neumí multithreading.
     * 
     * @param handler - reference na handler, který se má pridat.
     * @return true if the set did not already contain the specified element.
     */
    synchronized boolean add(SocketHandler handler) {
        return activeHandlersSet.add(handler);
    }

    /**
     * remove odebere z mnoziny aktivních handlere nový handler.
     * Metoda je sychronizovaná, protoze HashSet neumí multithreading.
     * 
     * @param handler - reference na handler, který se má odstranit
     * @return true if the set did not already contain the specified element.
     */
    synchronized boolean remove(SocketHandler handler) {
        return activeHandlersSet.remove(handler);
    }
}


//na gite tabulka ako to ma vyzerat - dodrzat format
//setMyNAme - uzivatelske meno bez medzier, aby bol vramci serveru unikatny nick, server ho musi vyzvat ak uz existuje,
//po uspesnom pripojeni server by mal vyzvat uzivatela pre zvolenie nickname hned na zaciatku

//sendPrivate (nickname - komu posielame, message - co cheme poslat - moze byt s medzerami )

//join(chatMiestnost - vytvorit vlakno pre chat, spravy sa mozu posielat len do tej skupiny v ktorych su)
//ak skupina s nazvom neexistuje tak sa vytvori nova a je tam klient najprv sam, potom sa ludia mozu pripojit
//skupina by mala mat id public

//leave (public-main skupina v ktorej sme)

//groups informacny command, v akych skupinach sme, oddelene ciarkami



//ked server posiela spravu inemu uzivatelovi tak dodrzat format - [<od koho>] >> <sprava>

//ak uzivatel je v roznych skupinach - posiela sa sprava aj do public - default skupina v ktorej je, prida sa aj do skupiny group1
//sprava sa posiela do vsetkych skupin v ktorych uzivatel je - public aj group1 