// Shared class, keeps all active handlers (connected clients) and chat rooms
package utb.fai;

import java.util.*;

public class ActiveHandlers {
    private HashSet<SocketHandler> activeHandlersSet = new HashSet<SocketHandler>();
    // room name -> list of clients in the room
    private LinkedHashMap<String, ArrayList<SocketHandler>> rooms = new LinkedHashMap<String, ArrayList<SocketHandler>>();

    public ActiveHandlers() {
        rooms.put("public", new ArrayList<SocketHandler>()); // default room
    }

    /**
     * Sends the message to all active clients except the sender.
     */
    synchronized void sendMessageToAll(SocketHandler sender, String message) {
        for (SocketHandler handler : activeHandlersSet)
            if (handler != sender) {
                if (!handler.messages.offer(message))
                    System.err.printf("Client %s message queue is full, dropping the message!\n", handler.clientID);
            }
    }

    /**
     * Adds a new handler to the active handlers and to the "public" room.
     * Synchronized, because HashSet is not thread safe.
     */
    synchronized boolean add(SocketHandler handler) {
        rooms.get("public").add(handler); // every client is automatically in public
        return activeHandlersSet.add(handler);
    }

    /**
     * Removes the handler from the active handlers and from all rooms.
     */
    synchronized boolean remove(SocketHandler handler) {
        ArrayList<String> names = new ArrayList<String>(rooms.keySet());
        for (String room : names) {
            leave(handler, room);
        }
        return activeHandlersSet.remove(handler);
    }

    /**
     * Sends the message to everyone in the rooms of the sender.
     * Each client gets the message only once, the sender does not get it.
     */
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

    /**
     * Private message. Returns false if the user does not exist.
     */
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

    /**
     * Sets the name of the client if nobody else uses it.
     */
    synchronized boolean setName(SocketHandler handler, String name) {
        for (SocketHandler other : activeHandlersSet) {
            if (other != handler && name.equals(other.name))
                return false;
        }
        handler.name = name;
        handler.clientID = name; // nickname instead of IP:port
        return true;
    }

    /**
     * Joins the client to the room, the room is created if it does not exist.
     * Returns false if the client is already in the room.
     */
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

    /**
     * Removes the client from the room, an empty room (except public) is deleted.
     * Returns false if the client was not in the room.
     */
    synchronized boolean leave(SocketHandler handler, String room) {
        ArrayList<SocketHandler> members = rooms.get(room);
        if (members == null || !members.contains(handler))
            return false;
        members.remove(handler);
        if (members.isEmpty() && !room.equals("public"))
            rooms.remove(room);
        return true;
    }

    /**
     * Rooms of the client separated by commas, e.g. "public,MyGroup1".
     */
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
