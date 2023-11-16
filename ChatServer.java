import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatServer {
    private static final int MAX_CLIENTS = 5;
    private static final long ROOM_LIFETIME = 180000; // 3分钟 = 180000毫秒
    private static Map<String, ChatRoom> chatRooms = new ConcurrentHashMap<>();
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

    static class ChatRoom {
        Set<ClientHandler> members;
        String password;

        ChatRoom() {
            this.members = new CopyOnWriteArraySet<>();
            this.password = null; // 默认没有密码
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader input;
        private String currentRoomId;
        private String userName;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }

        public void run() {
            try {
                while (true) {
                    userName = input.readLine();
                    if (userName != null && setUsername(userName)) {
                        out.println("Username accepted: " + userName);
                        break;
                    } else {
                        out.println("Username is already taken.");
                    }
                }

                String clientMessage;
                while ((clientMessage = input.readLine()) != null) {
                    String[] tokens = clientMessage.split(" ", 3);
                    String command = tokens[0];

                    switch (command) {
                        case "create":
                            createRoom(tokens);
                            break;
                        case "join":
                            joinRoom(tokens);
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
                        default:
                            sendMessage("This command does not exist, please enter again!");
                            break;
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

        private void createRoom(String[] tokens) {
            String roomId = tokens[1];
            String password = tokens.length > 2 ? tokens[2] : null;

            if (!chatRooms.containsKey(roomId)) {
                ChatRoom newRoom = new ChatRoom();
                newRoom.password = password;
                chatRooms.put(roomId, newRoom);

                // 设置计时器，检查房间是否空闲
                ScheduledFuture<?> roomTimer = scheduler.schedule(() -> {
                    ChatRoom room = chatRooms.get(roomId);
                    if (room != null && room.members.isEmpty()) {
                        chatRooms.remove(roomId);
                        roomTimers.remove(roomId);
                        System.out.println("Room " + roomId + " was removed due to inactivity.");
                    }
                }, ROOM_LIFETIME, TimeUnit.MILLISECONDS);

                roomTimers.put(roomId, roomTimer);
                System.out.println("Created room: " + roomId + (password != null ? " with password" : ""));
            } else {
                sendMessage("Room already exists: " + roomId);
                // System.out.println("Room already exists: " + roomId);
            }
        }


        private void joinRoom(String[] tokens) {
            String roomId = tokens[1];

            if (chatRooms.containsKey(roomId)) {
                ChatRoom room = chatRooms.get(roomId);

                if (room.password != null && !room.password.isEmpty() && tokens.length == 2) {
                    sendMessage("Please Enter the password for room " + roomId);
                    return; // 等待客户端发送密码
                }

                String password = tokens.length > 2 ? tokens[2] : null;
                if (room.password != null && !room.password.equals(password)) {
                    sendMessage("Password incorrect");
                    return;
                }

            } else {
                sendMessage("Room does not exist: " + roomId);
            }
        }

        private void leaveRoom() {
            String roomIdToCheck = currentRoomId;

            if (roomIdToCheck != null && chatRooms.containsKey(roomIdToCheck)) {
                ChatRoom room = chatRooms.get(roomIdToCheck);
                room.members.remove(this);
                broadcastMessageToRoom(roomIdToCheck, userName + " has left the room.");
                System.out.println(userName + " has left room: " + roomIdToCheck);

                if (room.members.isEmpty()) {
                    // 重新设置计时器
                    ScheduledFuture<?> roomTimer = scheduler.schedule(() -> {
                        ChatRoom checkRoom = chatRooms.get(roomIdToCheck);
                        if (checkRoom != null && checkRoom.members.isEmpty()) {
                            chatRooms.remove(roomIdToCheck);
                            roomTimers.remove(roomIdToCheck);
                            System.out.println("Room " + roomIdToCheck + " was removed due to inactivity.");
                        }
                    }, ROOM_LIFETIME, TimeUnit.MILLISECONDS);

                    roomTimers.put(roomIdToCheck, roomTimer);
                }

                currentRoomId = null;
            }
        }


        private void broadcastMessageToRoom(String roomId, String message) {
            if (chatRooms.containsKey(roomId)) {
                for (ClientHandler client : chatRooms.get(roomId).members) {
                    client.sendMessage(message);
                }
            }
        }

        private void broadcastMessage(String message) {
            if (currentRoomId != null && chatRooms.containsKey(currentRoomId)) {
                for (ClientHandler client : chatRooms.get(currentRoomId).members) {
                    if (client != this) {
                        client.sendMessage(message);
                    }
                }
            }
        }

        private void sendActiveRooms() {
            if (chatRooms.isEmpty()) {
                sendMessage("No active chat rooms.");
            } else {
                StringBuilder roomsInfo = new StringBuilder("Active chat rooms:");
                for (Map.Entry<String, ChatRoom> entry : chatRooms.entrySet()) {
                    String roomId = entry.getKey();
                    ChatRoom room = entry.getValue();
                    roomsInfo.append("\n - ").append(roomId)
                            .append(room.password == null || room.password.isEmpty() ? " (No password)" : " (Password protected)");
                }
                sendMessage(roomsInfo.toString());
            }
        }

        private void sendAvailableActions() {
            String actions = "Available actions:\n"
                           + " - join <roomName> [password]: Join a chat room\n"
                           + " - leave: Leave the current chat room\n"
                           + " - create <roomName> [password]: Create a new chat room\n"
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
