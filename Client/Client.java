package Client;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.UUID;

public class Client{

    private static String HOST;
    private static int PORT;
    private static String clientID;

    private static String version;

    private static boolean waitingServer;

    public static void main(String... args) {
        HOST = "localhost";
        PORT = Integer.valueOf(31161);

        System.out.println("Enter which protocol to use: tcp or udp");
        Scanner protocol = new Scanner(System.in);
        String temp = protocol.nextLine();
        waitingServer = false;
        switch (temp.toLowerCase()) {
            case "tcp":
                sendTCP();
                break;
            case "udp":
                sendUDP();
                break;
            default:
        }
    }

    private static void sendTCP() {
        System.out.println("starting TCP");

        try (Socket sock = new Socket(HOST, PORT)) {
            OutputStream out = sock.getOutputStream();
            InputStream in = sock.getInputStream();

            while(true) {
                if (waitingServer) {
                    readServerResponse(in);
                    waitingServer = false;
                    continue;
                }
                System.out.println("Please specify your command");
                Scanner scanner = new Scanner(System.in);

                String request = scanner.nextLine();
                request += "\n";
                ClientMessageMethod method = ClientMessageMethod.fromString(request.substring(0,4));

                if(method == null) {
                    System.out.println("Unacceptable request");
                    out.close();
                    break;
                }

                switch (method) {
                    case CREA:
                        System.out.println("sending client request...");
                        out.write(request.getBytes());
                        System.out.println("client request sent");
                        readServerResponse(in);
                        System.out.println("Waiting for a player to join the game...");
                        waitingServer = true;
                        break;
                    case GDBY:
                        break;
                    case HELO, LIST, JOIN, STAT, MOVE:
                        System.out.println("sending client request...");
                        out.write(request.getBytes());
                        System.out.println("client request sent");
                        break;
                    case QUIT:
                        break;
                    default:
                        System.out.println("something went wrong");
                }
                // read and print server response
                if (!waitingServer) {
                    readServerResponse(in);
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void readServerResponse(InputStream in) throws IOException {
        // read and print server response
        StringBuilder serverReply = new StringBuilder();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));
        String line;
        while((line = bufferedReader.readLine()) != null) {
            if(line.isEmpty()) {
                break;
            }
            serverReply.append(line).append("\n");
        }
        System.out.println(serverReply);
    }

    private static void sendUDP() {
        try (DatagramSocket sock = new DatagramSocket()) {
            InetAddress host = InetAddress.getByName(HOST);
            // ignored message
            String message = "hello";
            DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), host, PORT);
            sock.send(packet);

            byte[] buffer = new byte[512];
            DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
            sock.receive(receivedPacket);

            System.out.println(new String(buffer, 0, receivedPacket.getLength()));

            sock.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

}

enum ClientMessageMethod {
    CREA("CREA"), GDBY("GDBY"),
    HELO("HELO"), JOIN("JOIN"),
    LIST("LIST"), MOVE("MOVE"),
    QUIT("QUIT"), STAT("STAT"),
    CURR("CURR"), ALL("ALL");

    private final String value;
    ClientMessageMethod(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    public static ClientMessageMethod fromString(String str) {
        for(ClientMessageMethod method: ClientMessageMethod.values()) {
            if(method.getValue().equals(str)) {
                return method;
            }
        }
        return null;
    }
}