import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.*;
import javax.net.ssl.*;
import java.security.KeyStore;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class ChatServer {

    private final int serverPort = 1234;
    // zdaj hranimo SSLSocket-e, spremeniti sem moral vse HashMap entry-e
    private final Map<String, DataOutputStream> clientOutputs = new HashMap<>();



    public static void main(String[] args) throws Exception {
        new ChatServer().start();
    }

    public void start() throws Exception {

        KeyStore clientTs = KeyStore.getInstance("JKS");
        clientTs.load(new FileInputStream("client.public"), "public".toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(clientTs);


        KeyStore serverKs = KeyStore.getInstance("PKCS12");
        serverKs.load(new FileInputStream("server.private"), "serverpwd".toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(serverKs, "serverpwd".toCharArray());


        SSLContext sslCtx = SSLContext.getInstance("TLSv1.2");
        sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);


        SSLServerSocketFactory ssf = sslCtx.getServerSocketFactory();
        SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(serverPort);
        serverSocket.setNeedClientAuth(true);
        serverSocket.setEnabledProtocols(new String[] { "TLSv1.2" });
        serverSocket.setEnabledCipherSuites(new String[] {
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
        });

        System.out.println("[system] listening...");


        while (true) {
            SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
            clientSocket.startHandshake();

           // za pridobitev Common name
            String dn = clientSocket.getSession()
                                    .getPeerPrincipal()
                                    .getName();
            String username = dn.split(",")[0].substring(3);
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

            synchronized (this) {
                if (clientOutputs.containsKey(username) || "all".equalsIgnoreCase(username)) {

                    JSONObject dupe = new JSONObject();
                    dupe.put("type", "error");
                    dupe.put("sender", "server");
                    dupe.put("content", "Uporabniško ime '" + username + "' ni na voljo!");
                    dupe.put("cas", ZonedDateTime.now(ZoneId.of("Europe/Ljubljana"))
                                              .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

                        out.writeUTF(dupe.toJSONString());

                    clientSocket.close();
                } else {
                    clientOutputs.put(username, out);
                    System.out.println("[system] Client " + username + " connected via TLS");
                    alertAll(username);
                    new ChatServerConnector(this, clientSocket, username).start();
                }
            }
        }
    }

    // Broadcast to all
    public void sendToAllClients(String message) {
      for (DataOutputStream out : clientOutputs.values()) {
        try {
          out.writeUTF(message);
          out.flush();
        } catch (IOException e) {
          System.err.println("[system] could not send to one client, removing…");

        }
      }
    }



   public void sendToPrivateClient(String message) throws Exception {
       JSONObject temp = (JSONObject) new JSONParser().parse(message);
       String target = (String) temp.get("targetUser");
       DataOutputStream out = clientOutputs.get(target);

       if (out != null) {
           out.writeUTF(message);
           out.flush();
       } else {
           //ni tega userja
           sendError((String) temp.get("sender"));
       }
   }


    public void sendError(String user) throws Exception {
        JSONObject err = new JSONObject();
        err.put("type", "error");
        err.put("sender", "server");
        err.put("targetUser", user);
        err.put("content", "Napaka: uporabnik ne obstaja!");
        err.put("cas", ZonedDateTime.now(ZoneId.of("Europe/Ljubljana"))
                                  .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        DataOutputStream out = clientOutputs.get(user);
        if (out != null) {
            out.writeUTF(err.toJSONString());
            out.flush();
        }

    }


    public void sendListOfClients(String requestMsg) throws Exception {
        JSONObject req = (JSONObject) new JSONParser().parse(requestMsg);
        String target = (String) req.get("targetUser");


        StringBuilder list = new StringBuilder();
        for (String user : clientOutputs.keySet()) {
            if (list.length() > 0) list.append(' ');
            list.append(user);
        }

        JSONObject resp = new JSONObject();
        resp.put("type", "listClients");
        resp.put("sender", "server");
        resp.put("targetUser", target);
        resp.put("content", list.toString());
        resp.put("cas", ZonedDateTime.now(ZoneId.of("Europe/Ljubljana"))
                                   .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        DataOutputStream out = clientOutputs.get(target);
        if (out != null) {
            out.writeUTF(resp.toJSONString());
            out.flush();
        }
    }


    public void alertAll(String username) {
        JSONObject alert = new JSONObject();
        alert.put("type", "alert");
        alert.put("sender", "server");
        alert.put("targetUser", "all");
        alert.put("content", "Uporabnik " + username + " se je pridružil klepetu!");
        alert.put("cas", ZonedDateTime.now(ZoneId.of("Europe/Ljubljana"))
                                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        sendToAllClients(alert.toJSONString());
    }

   public synchronized void removeClient(String user) {

       DataOutputStream out = clientOutputs.remove(user);
       if (out != null) {
           try { out.close(); }
           catch(IOException ignored) { }
       }

       System.out.println("[system] Client " + user + " has been removed");
       JSONObject note = new JSONObject();
       note.put("type", "removedClient");
       note.put("sender", "server");
       note.put("targetUser", "all");
       note.put("content", "Uporabnik " + user + " je zapustil klepet!");
       note.put("cas", ZonedDateTime.now(ZoneId.of("Europe/Ljubljana"))
                                  .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
       sendToAllClients(note.toJSONString());
   }

    class ChatServerConnector extends Thread {
        private ChatServer server;
        private SSLSocket socket;
        private String username;

        public ChatServerConnector(ChatServer server, SSLSocket socket, String username) {
            this.server = server;
            this.socket = socket;
            this.username = username;
        }

        @Override
        public void run() {
            System.out.println("[system] connected with "
                + socket.getInetAddress().getHostName()
                + ":" + socket.getPort() + " as " + username);
            System.out.println();

            DataInputStream in;
            try {
                in = new DataInputStream(this.socket.getInputStream());
            } catch (IOException e) {
                System.err.println("[system] could not open input stream!");
                e.printStackTrace(System.err);
                this.server.removeClient(username);
                return;
            }

            while (true) {
                String msg_received;
                try {
                    msg_received = in.readUTF();
                } catch (Exception e) {
                    System.err.println("[system] error reading from client " + username + ", removing client");
                    e.printStackTrace(System.err);
                    this.server.removeClient(username);
                    return;
                }

                if (msg_received.length() == 0) {
                    continue;
                }

                JSONParser parser = new JSONParser();
                JSONObject temp;
                try {
                    temp = (JSONObject) parser.parse(msg_received);
                } catch (Exception e) {
                    // neveljavno sporočilo, ignoriramo
                    continue;
                }

                String type = (String) temp.get("type");
                String sender = (String) temp.get("sender");
                String targetUser = (String) temp.get("targetUser");
                String content = (String) temp.get("content");
                String time = (String) temp.get("cas");

                if (type.equals("public")) {
                    System.out.println("Tip sporocila: Javno");
                } else if (type.equals("private")) {
                    System.out.println("Tip sporocila: Privatno");
                } else if (type.equals("error")) {
                    System.out.println("Tip sporocila: Napaka");
                } else if (type.equals("listClients")) {
                    System.out.println("Tip sporocila: Vsi aktivni uporabiki");
                }

                System.out.println("Posiljatelj: " + sender);
                System.out.println("Prejemnik: " + targetUser);
                System.out.println("cas: " + time);
                System.out.println("Vsebina:\n" + content);
                System.out.println();

                String msg_send = msg_received;

                try {
                    if (type.equals("public")) {
                        this.server.sendToAllClients(msg_send);
                    } else if (type.equals("private")) {
                        this.server.sendToPrivateClient(msg_send);
                    } else if (type.equals("listClients")) {
                        this.server.sendListOfClients(msg_send);
                    } else if (type.equals("IamLeaving")) {
                        this.server.removeClient(sender);
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("[system] problem while sending the message");
                    e.printStackTrace(System.err);
                    // nadaljuj
                }
            }
        }
    }
}
