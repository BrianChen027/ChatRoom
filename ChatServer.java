import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int MAX_CLIENTS = 5;
    private static final long ROOM_LIFETIME = 10000; // 5分鐘 = 300000毫秒
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
                    sendMessage("Enter your username: ");
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
                            if (tokens.length > 1 && tokens[1].equals("rooms")) {
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

        private void createRoom(String roomId) {
            if (!chatRooms.containsKey(roomId)) {
                chatRooms.put(roomId, new CopyOnWriteArraySet<>());
                // 设置定时器，5分钟后检查聊天室是否为空
                ScheduledFuture<?> roomTimer = scheduler.schedule(() -> {
                    if (chatRooms.getOrDefault(roomId, Collections.emptySet()).isEmpty()) {
                        chatRooms.remove(roomId);
                        roomTimers.remove(roomId);
                        System.out.println("Room " + roomId + " was removed due to inactivity.");
                    }
                }, ROOM_LIFETIME, TimeUnit.MILLISECONDS);
                roomTimers.put(roomId, roomTimer);
                System.out.println("Created room: " + roomId);
            } else {
                System.out.println("Room already exists: " + roomId);
            }
        }

        private void joinRoom(String roomId) {
            if (chatRooms.containsKey(roomId)) {
                Set<ClientHandler> room = chatRooms.get(roomId);
                if (room.size() < MAX_CLIENTS) {
                    leaveRoom();
                    room.add(this);
                    currentRoomId = roomId;

                    ScheduledFuture<?> roomTimer = roomTimers.get(roomId);
                    if (roomTimer != null) {
                        roomTimer.cancel(false);
                        roomTimers.remove(roomId);
                    }

                    sendMessage("Joined room: " + roomId);
                    broadcastMessageToRoom(roomId, userName + " has joined the room.");
                    System.out.println(userName + " has joined room: " + roomId);
                } else {
                    sendMessage("Room is full.");
                }
            } else {
                sendMessage("Room does not exist: " + roomId);
            }
        }

        private void leaveRoom() {
            String roomIdToCheck = currentRoomId;

            if (roomIdToCheck != null && chatRooms.containsKey(roomIdToCheck)) {
                Set<ClientHandler> room = chatRooms.get(roomIdToCheck);
                room.remove(this);
                broadcastMessageToRoom(roomIdToCheck, userName + " has left the room.");
                System.out.println(userName + " has left room: " + roomIdToCheck);

                if (room.isEmpty()) {
                    System.out.println("Room " + roomIdToCheck + " is empty");
                    ScheduledFuture<?> roomTimer = scheduler.schedule(() -> {
                        if (chatRooms.getOrDefault(roomIdToCheck, Collections.emptySet()).isEmpty()) {
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

        
        // private void leaveRoom() {
        //     if (currentRoomId != null && chatRooms.containsKey(currentRoomId)) {
        //         Set<ClientHandler> room = chatRooms.get(currentRoomId);
        //         room.remove(this);
        //         broadcastMessageToRoom(currentRoomId, userName + " has left the room.");
        //         System.out.println(userName + " has left room: " + currentRoomId);
        //         if (room.isEmpty()) {
        //             System.out.println("Room " + currentRoomId + " is empty");
        //             ScheduledFuture<?> roomTimer = scheduler.schedule(() -> {
        //                 if (chatRooms.getOrDefault(currentRoomId, Collections.emptySet()).isEmpty()) {
        //                     chatRooms.remove(currentRoomId);
        //                     roomTimers.remove(currentRoomId);
        //                     System.out.println("Room " + currentRoomId + " was removed due to inactivity.");
        //                 }
        //             }, ROOM_LIFETIME, TimeUnit.MILLISECONDS);
        //             roomTimers.put(currentRoomId, roomTimer);
        //         }
        //         currentRoomId = null;
        //     }
        // }

        private void broadcastMessageToRoom(String roomId, String message) {
            if (chatRooms.containsKey(roomId)) {
                for (ClientHandler client : chatRooms.get(roomId)) {
                    client.sendMessage(message);
                }
            }
        }

        private void broadcastMessage(String message) {
            if (currentRoomId != null && chatRooms.containsKey(currentRoomId)) {
                for (ClientHandler client : chatRooms.get(currentRoomId)) {
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
                String activeRooms = "Active chat rooms: " + String.join(", ", chatRooms.keySet());
                sendMessage(activeRooms);
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
        public void sendMessageNor(String message) {
            out.print(message);
        }
    }
}
