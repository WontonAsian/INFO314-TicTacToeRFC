import java.util.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static ExecutorService executorService = Executors.newFixedThreadPool(10);
    private static int PORT;

    private static HashMap<String, GameState> games;
    private static HashMap<String, OutputStream> clientInWaiting;
    private static String sessionID;
    private static String clientID;

    public static void main(String... args) {
        PORT = args.length == 1 ? Integer.parseInt(args[0]) : 31161;
        games = new HashMap<>();
        clientInWaiting = new HashMap<>();

        try {
            ServerSocket tcpSocket = new ServerSocket(PORT);
            DatagramSocket udpSocket = new DatagramSocket(PORT);

            new Thread(() -> {
                try {
                    System.out.println("TCP: Server is listening on port " + PORT);

                    while (true) {
                        Socket socket = tcpSocket.accept();

                        executorService.submit(() -> {
                            try {
                                tcp(socket);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();

            new Thread(() -> {
                System.out.println("UDP: Server is listening on port " + PORT);

                DatagramPacket request = new DatagramPacket(new byte[512], new byte[512].length);
                while (true) {
                    try {
                        udpSocket.receive(request);
                        executorService.submit(() -> {
                            udp(udpSocket, request);
                        });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }).start();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void tcp(Socket socket) throws IOException {
        System.out.println("TCP: Received TCP request");
        while (socket != null) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));

            String clientRequest = bufferedReader.readLine();
            String stringData = new String(clientRequest.getBytes(), "US-ASCII");


            System.out.println("received client request: \n" + stringData);
            String[] requestParts = stringData.split("\\s+");
            String command = requestParts[0];

            // handle command
            switch (command) {
                case "HELO":
                    if (requestParts.length != 3) {
                        sendResponse("please provide all arguments ", out);
                    } else {
                        sessionID = generateRandomString();
                        clientID = requestParts[2];
                        if (!clientInWaiting.containsKey(clientID)) {
                            sendResponse("SESS " + sessionID + " " + clientID + "\n\r", out);
                        }
                    }
                    break;
                case "CREA":
                    if (!clientInWaiting.containsKey(clientID)) {
                        String gid = generateRandomString();
                        GameState newGame = new GameState(gid);
                        newGame.join(clientID);
                        games.put(gid, newGame);
                        clientInWaiting.put(clientID, out);
                        sendResponse("JOND " + gid + "\n\r", out);
                    }
                    break;
                case "LIST":

                    Map<String, Integer> gamesIds = new HashMap<>();

                    if (requestParts.length > 1) {
                        if (requestParts[1].equalsIgnoreCase("CURR")) {
                            for (String key : games.keySet()) {
                                if (games.get(key).getStatus() == 0 || games.get(key).getStatus() == 1) {
                                    gamesIds.put(games.get(key).getGameID(), games.get(key).getStatus());
                                }
                            }
                        } else if (requestParts[1].equalsIgnoreCase("ALL")) {
                            for (String key : games.keySet()) {
                                if (games.get(key).getStatus() == 0 || games.get(key).getStatus() == 1 || games.get(key).getStatus() == 2) {
                                    gamesIds.put(games.get(key).getGameID(), games.get(key).getStatus());
                                }
                            }
                        }
                    } else {
                        for (String key : games.keySet()) {
                            if (games.get(key).getStatus() == 0) {
                                gamesIds.put(games.get(key).getGameID(), games.get(key).getStatus());
                            }
                        }
                    }

                    StringBuilder gamesList = new StringBuilder("0: open, 1: in-play, 2: finished \n");
                    for (Map.Entry<String, Integer> entry : gamesIds.entrySet()) {
                        String gameID = entry.getKey();
                        Integer status = entry.getValue();
                        gamesList.append(gameID).append(" ").append(status).append("\n");
                    }
                    System.out.println(gamesList);
                    sendResponse("GAMS " + gamesList + "\r", out);
                    break;
                case "JOIN":
                    String clientRequestGameID = requestParts[1];
                    if (games.get(clientRequestGameID) == null) {
                        sendResponse("game does not exist\n\r", out);
                    } else {
                        GameState game = games.get(clientRequestGameID);
                        String opponent = game.getPlayerids()[0];
                        if (game.join(clientID) == 1) { // this game is full!
                            sendResponse("gameID " + clientRequestGameID + " is full!" + "\n\r", out);
                            break;
                        }
                        sendResponse("JOND " + clientID + " " + clientRequestGameID + "\r", out); // "JOND " CID " " + GID <- return format needs to be like this

                        if (game.getStatus() == 1) { // ready to start!
                            sendResponse("YRMV " + clientRequestGameID + " " + opponent + "\n\r", clientInWaiting.get(opponent));
                            sendResponse("YRMV " + clientRequestGameID + " " + opponent + "\n\r", out);
                        }
                        clientInWaiting.remove(opponent);
                    }
                    break;
                case "STAT":
                    String gameID = requestParts[1];
                    GameState game = games.get(gameID);
                    int gameStat = game.getStatus();
                    sendResponse("BORD " + gameID + " " + gameStat + "\n\r", out);
                    break;
                case "GDBY":
                    socket.close();
                    break;
                case "MOVE":
                    if (requestParts.length != 3) {
                        System.out.println("please provide all arguments");
                        continue;
                    }
                    gameID = requestParts[1];
                    String spot = requestParts[2];
                    CoordinatePair coordinatePair = new CoordinatePair(spot);
                    GameState curGame = games.get(gameID);
                    int moveResult = curGame.move(coordinatePair.getY(), coordinatePair.getX(), clientID);
                    if (moveResult == 0) {
                        String[] players = curGame.getPlayerids();
                        String nextMoveClient = curGame.getPlayerids()[curGame.getTurn() ^ 1];
                        sendResponse("BORD " + gameID + " " + players[0] + " " + players[1] + " " + nextMoveClient + "\n" + curGame.displayBoard() + "\n\r", out);
                    } else if (moveResult == 1) {
                        sendResponse("this move is out of bound" + "\n\r", out);
                    } else if (moveResult == 2) {
                        sendResponse("this move is already taken" + "\n\r", out);
                    } else if (moveResult == 3) {
                        sendResponse(clientID + " wins! " + "\n\r", out);
                    } else {
                        sendResponse("Not your turn!" + "\n\r", out);
                    }
                    break;
                case "QUIT":
                    socket.close();
                    break;

                default:
                    sendResponse("command does not exist", out);
                    socket.close();
                    break;
            }
        }
    }

    private static void sendResponse(String message, OutputStream out) {
        try {
            System.out.println("Sending server response...");
            out.write(message.getBytes());
            System.out.println("server response sent!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String generateRandomString() {
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }

    private static String getGames() {
        if (games.size() == 0) return "no games available";
        String result = "";
        for (Map.Entry<String, GameState> mapElement : games.entrySet()) {
            result += mapElement.getKey() + ", ";
        }
        return result.substring(0, result.length() - 2);
    }


    private static void udp(DatagramSocket socket, DatagramPacket request) {
        try {
            while (true) {
                socket.receive(request);
                System.out.println("UDP: Received UDP request");
                InetAddress clientAddress = request.getAddress();
                int clientPort = request.getPort();

                // handle commands here
                String reply = "UDP Reply";

                byte[] buffer = reply.getBytes();
                DatagramPacket response = new DatagramPacket(buffer, buffer.length, clientAddress, clientPort);
                socket.send(response);
                socket.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static class CoordinatePair {
        private final int x;
        private final int y;

        public CoordinatePair(String spot) {
            String[] coordinates = spot.split(",");
            if (coordinates.length == 1) { // e.g 8
                this.x = Integer.parseInt(coordinates[0]) % 3; // x = 2
                this.y = Integer.parseInt(coordinates[0]) / 3 + 1; // y = 3
            } else { // e.g (2, 1)
                this.x = Integer.parseInt(coordinates[0]); // x = 2
                this.y = 3 - Integer.parseInt(coordinates[1]) + 1; // y = 3
            }
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }

    static class GameState {
        private int gameStatus;
        private String[] playerids;
        private int turn;
        private String[][] board;
        private int winner;
        private String gameID;

        public GameState(String gameID) {
            gameStatus = 0;
            playerids = new String[2];
            turn = 0;
            board = new String[3][3];
            winner = -1;
            this.gameID = gameID;
        }

        public String getGameID() {
            return this.gameID;
        }

        public String[] getPlayerids() {
            return playerids;
        }

        public int getTurn() {
            return turn;
        }

        public int join(String playerId) {
            if (playerids[0] == null) {
                playerids[0] = playerId;
                return 0;
            } else if (playerids[1] == null) {
                playerids[1] = playerId;
                this.gameStatus = 1;
                return 0;
            } else {
                System.out.println("game is full");
                return 1;
            }
        }

        public int move(int x, int y, String playerid) {
            if (!playerids[turn].equals(playerid)) return 4;

            if (x < 1 || x > 3) {
                return 1;
            }
            if (y < 1 || y > 3) {
                return 1;
            }

            x--;
            y--;

            if (board[x][y] != null) return 2;

            String marker = turn == 0 ? "X" : "O";

            board[x][y] = marker;

            int potentialWinner = checkWin();
            if (potentialWinner != -1) {
                gameStatus = 2;
                winner = potentialWinner;
                return 3;
            }

            turn = turn ^ 1;

            return 0;
        }

        private int checkWin() {
            String marker = turn == 0 ? "X" : "O";
            boolean win = false;

            win = (checkThree(board[0][0], board[1][1], board[2][2]) ||
                    checkThree(board[0][2], board[1][1], board[2][0]));

            for (int i = 0; i < 3; i++) {
                if (checkThree(board[0][i], board[1][i], board[2][i])) {
                    win = true;
                }
            }

            for (int i = 0; i < 3; i++) {
                if (checkThree(board[i][0], this.board[i][1], this.board[i][2])) {
                    win = true;
                }
            }

            if (win) {
                if (marker.equals("X")) {
                    return 0;
                } else {
                    return 1;
                }
            }
            return -1;
        }

        private boolean checkThree(String s1, String s2, String s3) {
            return s1 != null && s1.equals(s2) && s2.equals(s3);
        }

        public String displayBoard() {
            String result = "";

            for (int i = 0; i < 3; i++) {
                result += " | ";
                for (int j = 0; j < 3; j++) {
                    String val = "";
                    if (board[i][j] == null) {
                        val = "*";
                    } else {
                        val = board[i][j];
                    }
                    result += val + " | ";
                }
                result += "\n";
            }
            return result;
        }

        public int getStatus() {
            return gameStatus;
        }

        public String getWinner() {
            return playerids[winner];
        }
    }
}
