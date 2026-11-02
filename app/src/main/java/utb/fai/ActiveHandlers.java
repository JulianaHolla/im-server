//zdielana classa, uchovava aktivnych handlerov - napr. aktivne sockety 
package utb.fai;

import java.util.*;

public class ActiveHandlers {
    private static final long serialVersionUID = 1L;
    private HashSet<SocketHandler> activeHandlersSet = new HashSet<SocketHandler>();
    // nazev mistnosti -> seznam klientu v mistnosti
    private LinkedHashMap<String, ArrayList<SocketHandler>> rooms = new LinkedHashMap<String, ArrayList<SocketHandler>>();

    public ActiveHandlers() {
        rooms.put("public", new ArrayList<SocketHandler>()); // vychozi mistnost
    }

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
        rooms.get("public").add(handler); // kazdy klient je automaticky v public
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
        ArrayList<String> names = new ArrayList<String>(rooms.keySet());
        for (String room : names) { // odebrat ze vsech mistnosti
            leave(handler, room);
        }
        return activeHandlersSet.remove(handler);
    }

    // posle zpravu vsem v mistnostech odesilatele (kazdemu jen jednou)
    synchronized void sendMessageToGroups(SocketHandler sender, String message) {
        ArrayList<SocketHandler> alreadySent = new ArrayList<SocketHandler>();
        for (ArrayList<SocketHandler> members : rooms.values()) {
            if (members.contains(sender)) {
                for (SocketHandler handler : members) {
                    if (handler != sender && !alreadySent.contains(handler)) {
                        if (!handler.messages.offer(message))
                            System.err.printf("Client %s message queue is full, dropping the message!\n", handler.clientID);
                        alreadySent.add(handler);
                    }
                }
            }
        }
    }

    // soukroma zprava, vraci false pokud uzivatel neexistuje
    synchronized boolean sendPrivate(String name, String message) {
        for (SocketHandler handler : activeHandlersSet) {
            if (name.equals(handler.name)) {
                if (!handler.messages.offer(message))
                    System.err.printf("Client %s message queue is full, dropping the message!\n", handler.clientID);
                return true;
            }
        }
        return false;
    }

    // nastavi jmeno, pokud uz neni pouzite
    synchronized boolean setName(SocketHandler handler, String name) {
        for (SocketHandler other : activeHandlersSet) {
            if (other != handler && name.equals(other.name))
                return false;
        }
        handler.name = name;
        handler.clientID = name; // misto IP:port pouzijeme nickname
        return true;
    }

    // pripoji do mistnosti, vytvori ji pokud neexistuje
    synchronized boolean join(SocketHandler handler, String room) {
        if (!rooms.containsKey(room)) {
            rooms.put(room, new ArrayList<SocketHandler>());
        }
        ArrayList<SocketHandler> members = rooms.get(room);
        if (members.contains(handler))
            return false;
        members.add(handler);
        return true;
    }

    // odpoji z mistnosti, prazdnou mistnost (krome public) smaze
    synchronized boolean leave(SocketHandler handler, String room) {
        ArrayList<SocketHandler> members = rooms.get(room);
        if (members == null || !members.contains(handler))
            return false;
        members.remove(handler);
        if (members.isEmpty() && !room.equals("public"))
            rooms.remove(room);
        return true;
    }

    // mistnosti klienta oddelene carkami
    synchronized String groups(SocketHandler handler) {
        String result = "";
        for (String room : rooms.keySet()) {
            if (rooms.get(room).contains(handler)) {
                if (!result.equals(""))
                    result += ",";
                result += room;
            }
        }
        return result;
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