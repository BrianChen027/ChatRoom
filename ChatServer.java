import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int MAX_CLIENTS = 5;
    private static final long ROOM_LIFETIME = 300000; // 5分鐘 = 300000毫秒
    private static Map<String, Set<ClientHandler>> chatRooms = new ConcurrentHashMap<>();
    private static Map<String, ScheduledFuture<?>> roomTimers = new ConcurrentHashMap<>();
    private static Set<String> activeUsernames = new CopyOnWriteArraySet<>();
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) throws IOException {
        int port = 1234;
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server is listening on port " + port);

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(new ClientHandler(socket)).start();
        }
    }

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

                if (!setUsername(userName)) {
                    out.println("Error: Username '" + userName + "' is already taken. Please reconnect with a different username.");
                    return; // 結束這個thread
                }

                String clientMessage;
                while ((clientMessage = input.readLine()) != null) {
                    String[] tokens = clientMessage.split(" ", 2);
                    String command = tokens[0];

                    switch (command) {
                        case "create":
                            createRoom(tokens[1]);
                            break;
                        case "join":
                            joinRoom(tokens[1]);
                            break;
                        case "leave":
                            leaveRoom();
                            break;
                        case "message":
                            broadcastMessage(userName + ": " + tokens[1]);
                            break;
                        case "show":
                            if (tokens.length > 1 && "rooms".equals(tokens[1])) {
                                sendActiveRooms();
                            }
                            break;
                        case "action":
                        case "help":
                            sendAvailableActions();
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
                activeUsernames.remove(userName);
            }
        }

        private boolean setUsername(String userName) {
            synchronized (activeUsernames) {
                if (!activeUsernames.contains(userName)) {
                    activeUsernames.add(userName);
                    return true;
                } else {
                    return false;
                }
            }
        }

        private void createRoom(String roomId) {
            // ... 創建聊天室的代碼 ...
        }

        private void sendActiveRooms() {
            // ... 發送活躍聊天室的代碼 ...
        }

        private void joinRoom(String roomId) {
            // ... 加入聊天室的代碼 ...
        }

        private void leaveRoom() {
            // ... 離開聊天室的代碼 ...
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

        private void sendAvailableActions() {
            String actions = "Available actions:\n"
                           + " - join <roomName>: Join a chat room\n"
                           + " - leave: Leave the current chat room\n"
                           + " - create <roomName>: Create a new chat room\n"
                           + " - message <message>: Send a message to the current chat room\n"
                           + " - show rooms: Show list of active chat rooms\n"
                           + " - action/help: Show this help message\n"
                           + " - exit: Exit the chat client";
            sendMessage(actions);
        }

        public void sendMessage(String message) {
            out.println(message);
        }
    }
}
