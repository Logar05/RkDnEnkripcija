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


public class ChatClient extends Thread
{
	protected int serverPort = 1234;

	public static void main(String[] args) throws Exception {
		new ChatClient();
	}

	public ChatClient() throws Exception {

        BufferedReader std_in = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Vnesi svoje ime: ");
        String name = std_in.readLine();


        KeyStore serverTs = KeyStore.getInstance("JKS");
        serverTs.load(new FileInputStream("server.public"), "public".toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(serverTs);

        String ksFile = name.toLowerCase() + ".private";
        String ksPass = name.toLowerCase() + "pwd";
        KeyStore clientKs = KeyStore.getInstance("PKCS12");
        clientKs.load(new FileInputStream(ksFile), ksPass.toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(clientKs, ksPass.toCharArray());

        SSLContext sslCtx = SSLContext.getInstance("TLSv1.2");
        sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        SSLSocketFactory sf = sslCtx.getSocketFactory();
        SSLSocket sslSocket = (SSLSocket) sf.createSocket("localhost", serverPort);
        sslSocket.setEnabledProtocols(new String[]{ "TLSv1.2" });
        sslSocket.setEnabledCipherSuites(new String[]{ "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256" });
        sslSocket.startHandshake();

        System.out.println("[system] connected via TLS as " + name);
        System.out.println();


        DataInputStream in = new DataInputStream(sslSocket.getInputStream());
        DataOutputStream out = new DataOutputStream(sslSocket.getOutputStream());


        ChatClientMessageReceiver message_receiver = new ChatClientMessageReceiver(in);
        message_receiver.start();

        String userInput;
        while ((userInput = std_in.readLine()) != null) {
            this.sendMessage(userInput, out, name);
        }

        // 5) cleanup
        out.close();
        in.close();
        std_in.close();
        sslSocket.close();
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
