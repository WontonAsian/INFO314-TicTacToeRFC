package Server;

public class GameState {
    /*
    0 : waiting for 2nd player
    1 : game currently in progress
    2 : game is over
     */
    private int gameStatus;
    private String[] playerids;
    private int turn;
    private String[][] board;
    private int winner;
    private String gameID;

    /*
    constructs the gamestate object
     */
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

    /*
			pre:    a player name
			post:   adds the player into the game and returns a 0
					else, returns a 1
			*/
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

    /*
    pre:    x and y coordinates on the tic tac toe board
    post:   makes the player move,  and return a 0 status code.
            if coordinates are out of bounds, return 1
            if spot alrady taken, return 2
            if player wins, return 3
            if it is not the player's turn, return 4
     */
    public int move(int x, int y, String playerid) {
        // check if it is the player's turn
        if (!playerids[turn].equals(playerid)) return 4;

        // check if the x or y coordinates are out of bounds
        if (x < 1 || x > 3) {
            return 1;
        }
        if (y < 1 || y > 3) {
            return 1;
        }

        // subtract 1 to x and y since arrays are 0 indexed
        x--;
        y--;

        // check if the spot is already taken
        if (board[x][y] != null) return 2;

        String marker = turn == 0 ? "X" : "O";

        // make move
        this.board[x][y] = marker;

        // check if anyone won the game
        int potentialWinner = checkWin();
        if (potentialWinner != -1) {
            // do stuff to stop game
            this.gameStatus = 2;
            this.winner = potentialWinner;
            return 3;
        }

        // switch player turn
        this.turn = this.turn ^ 1;

        return 0;
    }

    /*
    post:   if player 1 wins, return 0
            if player 2 wins, return 1
            else, return -1
     */
    private int checkWin(){

        String marker = turn == 0 ? "X" : "O";
        boolean win = false;

        // check diagonal
        win = (checkThree(board[0][0], board[1][1], board[2][2]) ||
                checkThree(board[0][2], board[1][1], board[2][0]));

        // check rows
        for (int i = 0; i < 3; i++) {
            if (checkThree(board[0][i], board[1][i], board[2][i])) {
                win = true;
            }
        }

        // check columns
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

    /*
    post:   checks if the 3 strings are the same
     */
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