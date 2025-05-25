import java.io.*;
import java.net.*;
import java.util.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import netscape.javascript.JSObject;

import java.time.*;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javax.net.ssl.*;
import java.security.KeyStore;
import java.io.FileInputStream;


public class ChatServer {

	protected int serverPort = 1234;
	//protected List<Socket> clients = new ArrayList<Socket>(); // list of clients
	protected HashMap<String, Socket> clients = new HashMap<>();
	public static void main(String[] args) throws Exception {
		new ChatServer();
	}

	public ChatServer() {
		ServerSocket serverSocket = null;		

		// create socket
		try {
			serverSocket = new ServerSocket(this.serverPort); // create the ServerSocket
		} catch (Exception e) {
			System.err.println("[system] could not create socket on port " + this.serverPort);
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// start listening for new connections
		System.out.println("[system] listening ...");
		
		try {
			while (true) {
				JSONParser parser = new JSONParser();
				Socket newClientSocket = serverSocket.accept(); // wait for a new client connection
				DataInputStream input = new DataInputStream(newClientSocket.getInputStream());
				String registerMessage = input.readUTF();
				JSONObject temp = (JSONObject) parser.parse(registerMessage);
				String usernameToAdd = (String) temp.get("sender");
				synchronized(this) {
					boolean alreadyTaken = false;
					Set userNames = this.clients.keySet();
					Iterator usernamesIterator = userNames.iterator();
					while(usernamesIterator.hasNext()){
						if(usernameToAdd.equals(usernamesIterator.next()) || usernameToAdd.equals("all")){ 
							alreadyTaken = true;
							break;
						}
					}
					if(!alreadyTaken){	
						clients.put(usernameToAdd, newClientSocket);
						System.out.println("[system] Client "+usernameToAdd+" has successfully connected to the chat server!");
						alertAll(usernameToAdd);
						ChatServerConnector conn = new ChatServerConnector(this, newClientSocket); 
						conn.start(); 
					}else{
						JSONObject dupeUser = new JSONObject();
						dupeUser.put("type", "error");
						dupeUser.put("sender", "server");
						dupeUser.put("targetUser", "naj bi bil "+usernameToAdd);
						dupeUser.put("content", "Uporabnisko ime "+usernameToAdd+" je zasedeno ali ni dovoljeno !");
						dupeUser.put("cas", ZonedDateTime.now(ZoneId.of("Europe/Ljubljana")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
						try{
							DataOutputStream out = new DataOutputStream(newClientSocket.getOutputStream());
							out.writeUTF(dupeUser.toJSONString());
						}catch(IOException e){
							e.printStackTrace();
						}
						finally{			 //v primeru dupliciranega up. imena moramo vedno zapreti socket
							try{
								newClientSocket.close();

							}catch(Exception e){}
						}
					}
				}
				
			}
		} catch (Exception e) {
			System.err.println("[error] Accept failed.");
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// close socket
		System.out.println("[system] closing server socket ...");
		try {
			serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	// send a message to all clients connected to the server
	public void sendToAllClients(String message) throws Exception {
		/*Iterator<Socket> i = clients.iterator();
		while (i.hasNext()) { // iterate through the client list
			Socket socket = (Socket) i.next(); // get the socket for communicating with this client
			try {
				DataOutputStream out = new DataOutputStream(socket.getOutputStream()); // create output stream for sending messages to the client
				out.writeUTF(message); // send message to the client
			} catch (Exception e) {	
				System.err.println("[system] could not send message to a client");
				e.printStackTrace(System.err);
			}
		}
*/
		for(Socket value : clients.values()){
			Socket socket = (Socket) value;
			try {
				DataOutputStream out = new DataOutputStream(socket.getOutputStream()); 
				out.writeUTF(message); 
			} catch (Exception e) {	
				System.err.println("[system] could not send message to a client");
				e.printStackTrace(System.err);
			}
		}
	}

	public void sendToPrivateClient(String message) throws Exception {	
		try {
			JSONParser parser = new JSONParser();
			JSONObject temp = (JSONObject) parser.parse(message);
			String targetUser = (String) temp.get("targetUser");
			Socket socket = clients.get(targetUser);
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			out.writeUTF(message);
		}
		catch(NullPointerException a){
			JSONParser parser = new JSONParser();
			JSONObject temp = (JSONObject) parser.parse(message);
			String targetUser = (String) temp.get("sender");
			sendError(targetUser);
		}
		catch (Exception e) {	
			System.err.println("[system] could not send message to a client");
			e.printStackTrace(System.err);
		}

	}

	public void sendError(String userReceiver) throws Exception {	
		JSONObject temp = new JSONObject();
		temp.put("type", "error");
		temp.put("sender", "server");
		temp.put("targetUser", userReceiver);
		temp.put("content", "Napaka: Uporabik ne obstaja!");
		temp.put("cas", ZonedDateTime.now(ZoneId.of("Europe/Ljubljana")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

		String message = temp.toJSONString();

		Socket socket = clients.get(userReceiver);
		DataOutputStream out = new DataOutputStream(socket.getOutputStream()); 
		out.writeUTF(message); 

	}

	public void sendListOfClients(String message) throws Exception {
			JSONParser parser = new JSONParser();
			JSONObject temp = (JSONObject) parser.parse(message);
			String targetUser = (String) temp.get("targetUser");
			Socket socket = clients.get(targetUser);
			DataOutputStream out = new DataOutputStream(socket.getOutputStream()); // create output stream for sending messages to the client
			
			JSONObject outMsg = new JSONObject();

			String vsiUporabiki = "";

			Set mnozica = clients.keySet();

			Iterator it = mnozica.iterator();

			boolean isFirst = true;

			while(it.hasNext()) {
				if(isFirst){
					vsiUporabiki = it.next().toString();
					isFirst = false;
				}
				else
					vsiUporabiki = vsiUporabiki +" "+ it.next();
			}

			outMsg.put("type", "listClients");
			outMsg.put("sender", "server");
			outMsg.put("targetUser", targetUser);
			outMsg.put("content", vsiUporabiki);
			outMsg.put("cas", ZonedDateTime.now(ZoneId.of("Europe/Ljubljana")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
			
			out.writeUTF(outMsg.toJSONString()); // send message to the client

	}

	public void alertAll(String username) throws Exception {
		JSONObject alert = new JSONObject();
		alert.put("type", "alert");
		alert.put("sender", "server");
		alert.put("targetUser", "all");
		alert.put("content", "Uporabik "+username+" se je pridruzil klepetu!");
		alert.put("cas", ZonedDateTime.now(ZoneId.of("Europe/Ljubljana")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
		sendToAllClients(alert.toJSONString());
	}

	public void removeClient(Socket socket){ //metodo sem razdelil v dva dela zaradi "ConcurrentModificationException"
		synchronized(this) {
			
			var pari = clients.entrySet();
			String userToRemove = null;
			for(var par : pari){
				if(par.getValue() == socket){
					userToRemove = (String) par.getKey();
					break;
				}
			}

			if(userToRemove != null){
				clients.remove(userToRemove);
				System.out.println("[system] Client "+userToRemove+" has been removed");
				JSONObject removedClientAlert = new JSONObject();
				removedClientAlert.put("type", "removedClient");
				removedClientAlert.put("sender", "server");
				removedClientAlert.put("targetUser", "all");
				removedClientAlert.put("content", "Uporabnik "+userToRemove+" je zapustil klepet!");
				removedClientAlert.put("cas", ZonedDateTime.now(ZoneId.of("Europe/Ljubljana")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
				try{
					sendToAllClients(removedClientAlert.toJSONString());
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		}
	}
	
}

class ChatServerConnector extends Thread {
	private ChatServer server;
	private Socket socket;

	public ChatServerConnector(ChatServer server, Socket socket) {
		this.server = server;
		this.socket = socket;
	}

	public void run() {
		System.out.println("[system] connected with " + this.socket.getInetAddress().getHostName() + ":" + this.socket.getPort());
		System.out.println();

		DataInputStream in;
		try {
			in = new DataInputStream(this.socket.getInputStream()); // create input stream for listening for incoming messages
		} catch (IOException e) {
			System.err.println("[system] could not open input stream!");
			e.printStackTrace(System.err);
			this.server.removeClient(socket);
			return;
		}

		while (true) { // infinite loop in which this thread waits for incoming messages and processes them
			String msg_received;
			try {
				msg_received = in.readUTF(); // read the message from the client

			} catch (Exception e) {
				System.err.println("[system] there was a problem while reading message client on port " + this.socket.getPort() + ", removing client");
				e.printStackTrace(System.err);
				this.server.removeClient(this.socket);
				return;
			}

			if (msg_received.length() == 0) 
				continue;



				 JSONParser parser = new JSONParser();
					JSONObject temp;
					try {
						temp = (JSONObject) parser.parse(msg_received);
					} catch (Exception e) {
						continue;
					}
				
				
				String type = (String) temp.get("type");  //brez object castinga error!! kljuci so stringi
				String sender = (String) temp.get("sender");
				String targetUser = (String) temp.get("targetUser");
				String content = (String) temp.get("content");
				String time = (String) temp.get("cas");

				if(type.equals("public")) System.out.println("Tip sporocila: Javno");

				if(type.equals("private")) System.out.println("Tip sporocila: Privatno");

				if(type.equals("error")) System.out.println("Tip sporocila: Napaka");

				if(type.equals("listClients")) System.out.println("Tip sporocila: Vsi aktivni uporabiki");

				System.out.println("Posiljatelj: "+sender);
				
				System.out.println("Prejemnik: "+targetUser);

				System.out.println("cas: "+time);

				System.out.println("Vsebina:\n"+content);

				System.out.println();

				String msg_send = msg_received;
		
			try {

				if(type.equals("public"))
					this.server.sendToAllClients(msg_send);

				else if(type.equals("private"))
					this.server.sendToPrivateClient(msg_send);	//dodaj private message metodo
				
				else if(type.equals("listClients"))
					this.server.sendListOfClients(msg_send);
				else if(type.equals("IamLeaving")){
					this.server.removeClient(this.socket);
					try{
						socket.close();
					}catch (IOException e){
						e.printStackTrace();
					}
					return;
				}

			} catch (Exception e) {
				System.err.println("[system] there was a problem while sending the message");
				e.printStackTrace(System.err);
				continue;
			}
		}
	}
}
