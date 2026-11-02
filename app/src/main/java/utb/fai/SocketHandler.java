//spracocovava aky klient je pripojeny na aky server
package utb.fai;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class SocketHandler { //
	/** mySocket je socket, o který se bude tento SocketHandler starat */
	Socket mySocket;

	/** client ID je øetìzec ve formátu <IP_adresa>:<port> */
	String clientID; //je ip adresa a port ktory je pripojeny na server - prerobit aby to bol nickname namiesto ip a portu

	/**
	 * activeHandlers je reference na mnoinu vech právì bìících SocketHandlerù.
	 * Potøebujeme si ji udrovat, abychom mohli zprávu od tohoto klienta
	 * poslat vem ostatním!
	 */
	ActiveHandlers activeHandlers; //zdielana referencia medzi vsetkymi

	// uzivatelske jmeno, dokud neni nastaveno je null
	volatile String name = null;

	/**
	 * messages je fronta pøíchozích zpráv, kterou musí mít kaý klient svoji
	 * vlastní - pokud bude je pøetíená nebo nefunkèní klientova sí,
	 * èekají zprávy na doruèení právì ve frontì messages
	 */
	ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<String>(20); //funguje ako buffer, ak socket handler 
	// zisti ze nejaky klient odosle spravu na server, tak handler vsetkym aktivnym handlerom 
	// vlozi spravu na buffer - runnable obstara spravu a odosle von 

	/**
	 * startSignal je synchronizaèní závora, která zaøizuje, aby oba tasky
	 * OutputHandler.run() a InputHandler.run() zaèaly ve stejný okamik.
	 */
	CountDownLatch startSignal = new CountDownLatch(2);

	/** outputHandler.run() se bude starat o OutputStream mého socketu */
	OutputHandler outputHandler = new OutputHandler();
	/** inputHandler.run() se bude starat o InputStream mého socketu */
	InputHandler inputHandler = new InputHandler();
	/**
	 * protoe v outputHandleru nedovedu detekovat uzavøení socketu, pomùe mi
	 * inputFinished
	 */
	volatile boolean inputFinished = false;

	public SocketHandler(Socket mySocket, ActiveHandlers activeHandlers) {
		this.mySocket = mySocket;
		clientID = mySocket.getInetAddress().toString() + ":" + mySocket.getPort();
		this.activeHandlers = activeHandlers;
	}

//berire spravy z fronty a spracovava spravy, ak tam nieco je tak to preposle spravnemu klientovi, ak tam nic neni tak caka
//moc nezasahovat, mozno tak upravit nieco velmi banalne
	class OutputHandler implements Runnable { 
		public void run() {
			OutputStreamWriter writer;
			try {
				System.err.println("DBG>Output handler starting for " + clientID);
				startSignal.countDown();
				startSignal.await();
				System.err.println("DBG>Output handler running for " + clientID);
				writer = new OutputStreamWriter(mySocket.getOutputStream(), "UTF-8");
				writer.write("\nYou are connected from " + clientID + "\n");
				writer.flush();
				while (!inputFinished) {
					String m = messages.take();// blokující ètení - pokud není ve frontì zpráv nic, uspi se!
					writer.write(m + "\r\n"); // pokud nìjaké zprávy od ostatních máme,
					writer.flush(); // poleme je naemu klientovi
					System.err.println("DBG>Message sent to " + clientID + ":" + m + "\n");
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.err.println("DBG>Output handler for " + clientID + " has finished.");

		}
	}
//cita inpout stream a vytvori sa request, request - co klient odosiela, loguje

	class InputHandler implements Runnable {
		public void run() {
			try {
				System.err.println("DBG>Input handler starting for " + clientID);
				startSignal.countDown();
				startSignal.await();
				System.err.println("DBG>Input handler running for " + clientID);
				String request = "";
				/**
				 * v okamiku, kdy nás Thread pool spustí, pøidáme se do mnoiny
				 * vech aktivních handlerù, aby chodily zprávy od ostatních i nám
				 */
				activeHandlers.add(SocketHandler.this);
				messages.offer("Welcome! Choose your name: #setMyName <name>");
				BufferedReader reader = new BufferedReader(new InputStreamReader(mySocket.getInputStream(), "UTF-8"));
				while ((request = reader.readLine()) != null) { // prisla od meho klienta nejaka zprava?
					request = request.trim();
					if (request.equals("")) {
						continue;
					}
					if (request.startsWith("#")) {
						processCommand(request); // prikaz
					} else if (name == null) {
						messages.offer("Set your name first: #setMyName <name>");
					} else {
						// zprava - poslat do vsech mistnosti, kde jsem
						System.out.println("From " + name + ": " + request);
						activeHandlers.sendMessageToGroups(SocketHandler.this, "[" + name + "] >> " + request);
					}
				}
				inputFinished = true;
				messages.offer("OutputHandler, wakeup and die!");
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				// remove yourself from the set of activeHandlers
				synchronized (activeHandlers) {
					activeHandlers.remove(SocketHandler.this);
				}
			}
			System.err.println("DBG>Input handler for " + clientID + " has finished.");
		}

		// zpracuje prikazy #setMyName, #sendPrivate, #join, #leave, #groups
		private void processCommand(String line) {
			String[] parts = line.split(" ", 3);
			String command = parts[0];

			if (command.equals("#setMyName")) {
				if (parts.length != 2) {
					messages.offer("Usage: #setMyName <name> (no spaces)");
				} else if (!activeHandlers.setName(SocketHandler.this, parts[1])) {
					messages.offer("Name " + parts[1] + " is already taken, choose another one.");
				} else {
					messages.offer("Your name is now " + parts[1]);
				}
			} else if (command.equals("#sendPrivate")) {
				if (name == null) {
					messages.offer("Set your name first: #setMyName <name>");
				} else if (parts.length < 3) {
					messages.offer("Usage: #sendPrivate <name> <message>");
				} else if (!activeHandlers.sendPrivate(parts[1], "[" + name + "] >> " + parts[2])) {
					messages.offer("User " + parts[1] + " not found.");
				}
			} else if (command.equals("#join")) {
				if (parts.length != 2) {
					messages.offer("Usage: #join <room>");
				} else if (!activeHandlers.join(SocketHandler.this, parts[1])) {
					messages.offer("You are already in " + parts[1]);
				} else {
					messages.offer("Joined " + parts[1]);
				}
			} else if (command.equals("#leave")) {
				if (parts.length != 2) {
					messages.offer("Usage: #leave <room>");
				} else if (!activeHandlers.leave(SocketHandler.this, parts[1])) {
					messages.offer("You are not in " + parts[1]);
				} else {
					messages.offer("Left " + parts[1]);
				}
			} else if (command.equals("#groups")) {
				messages.offer(activeHandlers.groups(SocketHandler.this));
			} else {
				messages.offer("Unknown command.");
			}
		}

	}
}
