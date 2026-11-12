// Takes care of one connected client (socket)
package utb.fai;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class SocketHandler {
	/** socket of this client */
	Socket mySocket;

	/** IP:port of the client, replaced by the nickname after the name is set */
	String clientID;

	/** shared reference to all active handlers */
	ActiveHandlers activeHandlers;

	/** user name, null until the client sets it */
	volatile String name = null;

	/**
	 * Queue of messages for this client. Other handlers put messages here,
	 * OutputHandler takes them out and sends them to the client.
	 */
	ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<String>(20);

	/** makes both tasks (output and input) start at the same moment */
	CountDownLatch startSignal = new CountDownLatch(2);

	/** takes care of the OutputStream of the socket */
	OutputHandler outputHandler = new OutputHandler();
	/** takes care of the InputStream of the socket */
	InputHandler inputHandler = new InputHandler();

	/** OutputHandler cannot detect a closed socket, so InputHandler tells it */
	volatile boolean inputFinished = false;

	public SocketHandler(Socket mySocket, ActiveHandlers activeHandlers) {
		this.mySocket = mySocket;
		clientID = mySocket.getInetAddress().toString() + ":" + mySocket.getPort();
		this.activeHandlers = activeHandlers;
	}

	// takes messages from the queue and sends them to the client (waits if the queue is empty)
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
					String m = messages.take(); // blocks if there is no message
					writer.write(m + "\r\n");
					writer.flush();
					System.err.println("DBG>Message sent to " + clientID + ":" + m + "\n");
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.err.println("DBG>Output handler for " + clientID + " has finished.");
		}
	}

	// reads what the client sends, processes commands and forwards messages
	class InputHandler implements Runnable {
		public void run() {
			try {
				System.err.println("DBG>Input handler starting for " + clientID);
				startSignal.countDown();
				startSignal.await();
				System.err.println("DBG>Input handler running for " + clientID);
				String request = "";

				// add ourselves to the active handlers (and to the public room)
				activeHandlers.add(SocketHandler.this);
				messages.offer("Welcome! Type your name (no spaces):");
				BufferedReader reader = new BufferedReader(new InputStreamReader(mySocket.getInputStream(), "UTF-8"));
				while ((request = reader.readLine()) != null) {
					request = request.trim();
					if (request.equals("")) {
						continue;
					}
					if (request.startsWith("#")) {
						processCommand(request);
					} else if (name == null) {
						// the first message of the client is its name
						setName(request);
					} else {
						// message goes to all rooms of this client
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
				// remove yourself from the active handlers
				synchronized (activeHandlers) {
					activeHandlers.remove(SocketHandler.this);
				}
			}
			System.err.println("DBG>Input handler for " + clientID + " has finished.");
		}

		// sets the name if it is free and has no spaces
		private void setName(String newName) {
			if (newName.contains(" ")) {
				messages.offer("Name must not contain spaces, choose another one.");
			} else if (!activeHandlers.setName(SocketHandler.this, newName)) {
				messages.offer("Name " + newName + " is already taken, choose another one.");
			} else {
				messages.offer("Your name is now " + newName);
			}
		}

		// commands: #setMyName, #sendPrivate, #join, #leave, #groups
		private void processCommand(String line) {
			String[] parts = line.split(" ", 3);
			String command = parts[0];

			if (command.equals("#setMyName")) {
				if (parts.length != 2) {
					messages.offer("Usage: #setMyName <name> (no spaces)");
				} else {
					setName(parts[1]);
				}
			} else if (command.equals("#sendPrivate")) {
				if (name == null) {
					messages.offer("Set your name first.");
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
