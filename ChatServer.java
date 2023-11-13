import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int MAX_CLIENTS = 5;
    private static Map<String, Set<ClientHandler>> chatRooms = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = 1234;
        ServerSocket serverSocket = new ServerSocket(port);

        System.out.println("Server is listening on port " + port);

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(new ClientHandler(socket)).start();
        }
    }

    // 處理客戶端連接
    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private String currentRoomId;
        private String userName;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new PrintWriter(socket.getOutputStream(), true);
        }

        public void run() {
            try {
                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                userName = input.readLine(); // 讀取用戶名

                String clientMessage;

                while ((clientMessage = input.readLine()) != null) {
                    String[] tokens = clientMessage.split(" ", 2);
                    String command = tokens[0];

                    switch (command) {
                        case "join":
                            joinRoom(tokens[1]);
                            break;
                        case "leave":
                            leaveRoom();
                            break;
                        case "message":
                            broadcastMessage(userName + ": " + tokens[1]);
                            break;
                        case "exit":
                            leaveRoom();
                            return;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                leaveRoom();
            }
        }

        private void joinRoom(String roomId) {
            leaveRoom();
            chatRooms.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>());
            Set<ClientHandler> room = chatRooms.get(roomId);

            if (room.size() < MAX_CLIENTS) {
                room.add(this);
                currentRoomId = roomId;
                sendMessage("Joined room: " + roomId);
            } else {
                sendMessage("Room is full");
            }
        }

        private void leaveRoom() {
            if (currentRoomId != null && chatRooms.containsKey(currentRoomId)) {
                Set<ClientHandler> room = chatRooms.get(currentRoomId);
                room.remove(this);
                if (room.isEmpty()) {
                    chatRooms.remove(currentRoomId);
                }
                currentRoomId = null;
            }
        }

        private void broadcastMessage(String message) {
            if (currentRoomId != null && chatRooms.containsKey(currentRoomId)) {
                for (ClientHandler client : chatRooms.get(currentRoomId)) {
                    if (client != this) { // 不向發送者發送消息
                        client.sendMessage(message);
                    }
                }
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }
    }
}
