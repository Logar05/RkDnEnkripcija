import java.io.*;
import java.net.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.time.*;
import java.util.Spliterator;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javax.net.ssl.*;
import java.security.KeyStore;
import java.io.FileInputStream;

public class ChatClient extends Thread
{
	protected int serverPort = 1234;

	public static void main(String[] args) throws Exception {
		new ChatClient();
	}

	public ChatClient() throws Exception {
		Socket socket = null;
		final String name;
		DataInputStream in = null;
		DataOutputStream out = null;
		final BufferedReader std_in;

		System.out.println("Vnesi svoje ime: ");
			std_in = new BufferedReader(new InputStreamReader(System.in));
			name = std_in.readLine();
			
		// connect to the chat server
		try {
			
			System.out.println("[system] connecting to chat server ...");
			socket = new Socket("localhost", serverPort); // create socket connection
			in = new DataInputStream(socket.getInputStream()); // create input stream for listening for incoming messages
			out = new DataOutputStream(socket.getOutputStream()); // create output stream for sending messages
			boolean reg = registracija("register", out, name); // register the client with the server
			if (!reg) {
				System.err.println("[system] could not register");
				System.exit(1);
			}
			System.out.println("[system] connected");
			System.out.println();

			ChatClientMessageReceiver message_receiver = new ChatClientMessageReceiver(in); // create a separate thread for listening to messages from the chat server
			message_receiver.start(); // run the new thread
		} catch (Exception e) {
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// read from STDIN and send messages to the chat server
		
		String userInput;
		while ((userInput = std_in.readLine()) != null) { // read a line from the console
			this.sendMessage(userInput, out, name); // send the message to the chat server
		}

		// cleanup
		out.close();
		in.close();
		std_in.close();
		socket.close();
	}

	private boolean registracija(String registerJSON, DataOutputStream out, String name) {
		try {
			JSONObject register = new JSONObject();
			register.put("type", "register");
			register.put("sender", name);
			register.put("targetUser", "server");
			register.put("content", "registerRequest");
			register.put("cas", ZonedDateTime.now(ZoneId.of("Europe/Ljubljana")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
			String finalMessage = register.toJSONString();
			out.writeUTF(finalMessage); 
			out.flush();
			
			System.out.println("[system] connected");
			System.out.println("[system] Pozdravljeni v pogovoru " + name + "!");
			
			return true;
		} catch (IOException e) {
			System.err.println("[system] could not register");
			e.printStackTrace(System.err);
			return false;
		}
	}

	private void sendMessage(String message, DataOutputStream out, String name) {
			try {
				JSONObject sporocilo = new JSONObject();

				String [] splitMsg = message.split(" ");

				if(splitMsg[0].equals("/public")){
					sporocilo.put("type", "public");

					sporocilo.put("sender", name);

					sporocilo.put("targetUser", "all"); //ce se user klice "all" pa GG


					String vsebina = message.substring(8);

					sporocilo.put("content", vsebina);

					sporocilo.put("cas", ZonedDateTime.now(ZoneId.of("Europe/Ljubljana")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

					out.writeUTF(sporocilo.toJSONString()); // send the message to the chat server
					out.flush(); // ensure the message has been sent

				}
			else if(splitMsg[0].equals("/private")) {
				sporocilo.put("type", "private");

				sporocilo.put("sender", name);

				sporocilo.put("targetUser", splitMsg[1]);

				int userLength = splitMsg[1].length() + 1; //content se zacne po "target user"

				String vsebina = message.substring(9 + userLength);

				sporocilo.put("content", vsebina);

				sporocilo.put("cas", ZonedDateTime.now(ZoneId.of("Europe/Ljubljana")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
				System.out.println();

				out.writeUTF(sporocilo.toJSONString()); // send the message to the chat server
				out.flush(); // ensure the message has been sent
			}
			else if(splitMsg[0].equals("/listClients")){

				sporocilo.put("type", "listClients");

				sporocilo.put("sender", name);

				sporocilo.put("targetUser", name);

				sporocilo.put("content", "userListRequest");

				sporocilo.put("cas", ZonedDateTime.now(ZoneId.of("Europe/Ljubljana")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
				System.out.println();

				out.writeUTF(sporocilo.toJSONString()); // send the message to the chat server
				out.flush(); // ensure the message has been sent


			}
			else if(splitMsg[0].equals("/leave")){
				sporocilo.put("type", "IamLeaving");

				sporocilo.put("sender", name);

				sporocilo.put("targetUser", "server");

				sporocilo.put("content", "userLeaveRequest");

				sporocilo.put("cas", ZonedDateTime.now(ZoneId.of("Europe/Ljubljana")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
				System.out.println();

				out.writeUTF(sporocilo.toJSONString()); // send the message to the chat server
				out.flush(); // ensure the message has been sent

			}
			else{
				System.out.println("Napacen tip sporocila.\nPrefix opcije:\n/public [content] - javno sporocilo za vse udelezence pogovora\n/private [user] [content] - zasebno sporocilo za uporabnika *user*\n/listClients - izpis vseh aktivnih uporabnikov\n/leave - zapusti pogovor");
				System.out.println();
			
			}

		} catch (IOException e) {
			System.err.println("[system] could not send message");
			e.printStackTrace(System.err);
		}
	}
}

// wait for messages from the chat server and print the out
class ChatClientMessageReceiver extends Thread {
	private DataInputStream in;

	public ChatClientMessageReceiver(DataInputStream in) {
		this.in = in;
	}

	public void run() {
		try {
			JSONParser parser= new JSONParser();

			String receivedMsg = in.readUTF();

			while (receivedMsg != null) { 
				JSONObject temp = (JSONObject) parser.parse(receivedMsg); //sparsaj sprejt JSON type
				String type = (String) temp.get("type");  //brez object castinga error!! kljuci so stringi
				String sender = (String) temp.get("sender");
				String targetUser = (String) temp.get("targetUser");
				String content = (String) temp.get("content");
				String time = (String) temp.get("cas");
				
				if(type.equals("public")) System.out.println("Tip sporocila: Javno");

				if(type.equals("private")) System.out.println("Tip sporocila: Privatno");

				if(type.equals("error")) System.out.println("Tip sporocila: Napaka");

				if(type.equals("listClients")) System.out.println("Tip sporocila: Vsi aktivni uporabiki");

				if(type.equals("removedClient")) System.out.println("Tip sporocila: Odklop uporabika");

				System.out.println("Posiljatelj: "+sender);

				System.out.println("Prejemnik: "+targetUser);

				System.out.println("cas: "+time);

				System.out.println("Vsebina:\n"+content);

				System.out.println();

			
				receivedMsg = in.readUTF();
 			}

		} catch (Exception e) {
			System.err.println("[system] could not read message");
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}
}
