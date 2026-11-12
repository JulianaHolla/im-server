package utb.fai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

// Web bridge: browser <-> WebSocket <-> this class <-> TCP <-> IM server
// Every browser connection gets its own TCP connection to the IM server.
// Every WebSocket message is sent to the IM server as one line,
// every line from the IM server is sent to the browser as one WebSocket message.
public class WebBridge extends WebSocketServer {

    static String tcpHost = "localhost";
    static int tcpPort = 33000;

    // one browser connection = one TCP socket to the IM server
    static class Session {
        Socket socket;
        PrintWriter out;
    }

    private ConcurrentHashMap<WebSocket, Session> sessions = new ConcurrentHashMap<WebSocket, Session>();

    public WebBridge(int wsPort) {
        super(new InetSocketAddress(wsPort));
    }

    public static void main(String[] args) throws IOException {
        int httpPort = 8080;
        if (args.length > 0)
            httpPort = Integer.parseInt(args[0]);
        if (args.length > 1)
            tcpHost = args[1];
        if (args.length > 2)
            tcpPort = Integer.parseInt(args[2]);
        int wsPort = httpPort + 1; // WebSocket port is the HTTP port + 1

        // HTTP server only serves the page
        HttpServer http = HttpServer.create(new InetSocketAddress(httpPort), 0);
        http.createContext("/", WebBridge::handleIndex);
        http.start();

        // WebSocket server
        new WebBridge(wsPort).start();

        System.out.println("Open http://localhost:" + httpPort + " in the browser");
        System.out.println("WebSocket on port " + wsPort + " -> IM server " + tcpHost + ":" + tcpPort);
    }

    // GET / -> index.html (from the project folder, or from the jar/classpath as a fallback)
    static void handleIndex(HttpExchange ex) throws IOException {
        byte[] page = null;
        File file = new File("app/src/main/resources/index.html");
        if (!file.exists())
            file = new File("src/main/resources/index.html"); // when started from the app folder
        if (file.exists()) {
            page = java.nio.file.Files.readAllBytes(file.toPath());
        } else {
            InputStream in = WebBridge.class.getResourceAsStream("/index.html");
            if (in != null)
                page = in.readAllBytes();
        }
        if (!ex.getRequestURI().getPath().equals("/") || page == null) {
            ex.sendResponseHeaders(404, -1);
            ex.close();
            return;
        }
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, page.length);
        ex.getResponseBody().write(page);
        ex.close();
    }

    // browser connected -> open a TCP connection to the IM server
    @Override
    public void onOpen(final WebSocket conn, ClientHandshake handshake) {
        try {
            final Session session = new Session();
            session.socket = new Socket(tcpHost, tcpPort);
            session.out = new PrintWriter(new OutputStreamWriter(session.socket.getOutputStream(), StandardCharsets.UTF_8), true);
            sessions.put(conn, session);

            // thread: IM server -> browser
            Thread reader = new Thread(new Runnable() {
                public void run() {
                    try {
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(session.socket.getInputStream(), StandardCharsets.UTF_8));
                        String line;
                        while ((line = in.readLine()) != null) {
                            if (line.trim().isEmpty())
                                continue;
                            conn.send(line);
                        }
                    } catch (IOException e) {
                        // connection closed
                    }
                    conn.close(); // IM server closed the connection
                }
            });
            reader.setDaemon(true);
            reader.start();
        } catch (IOException e) {
            System.err.println("Cannot connect to IM server: " + e.getMessage());
            conn.close();
        }
    }

    // browser -> IM server
    @Override
    public void onMessage(WebSocket conn, String message) {
        Session session = sessions.get(conn);
        if (session != null) {
            message = message.replace("\r", "").replace("\n", " ").trim();
            session.out.println(message);
        }
    }

    // browser disconnected -> close the TCP connection
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Session session = sessions.remove(conn);
        if (session != null) {
            try {
                session.socket.close();
            } catch (IOException e) {
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started");
    }
}
