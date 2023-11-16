import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    private static String attemptingRoomId = "";

    public static void main(String[] args) throws IOException {
        String serverAddress = "localhost";
        int port = 1234;

        try (Socket socket = new Socket(serverAddress, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {

            Thread responseThread = new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = input.readLine()) != null) {
                        System.out.println(serverMessage);
                        
                        if (serverMessage.startsWith("Please Enter the password for room")) {
                            // 在此线程中等待用户输入密码
                            System.out.print("Enter password: ");
                            String password = scanner.nextLine();
                            out.println("join " + attemptingRoomId + " " + password);
                        }
                    }
                } catch (IOException e) {
                    if (!socket.isClosed()) {
                        System.out.println("Error reading from server: " + e.getMessage());
                    }
                }
            });
            responseThread.start();

            // 显示用户名提示
            System.out.print("Enter the Username: ");
            attemptingRoomId = scanner.nextLine();
            out.println(attemptingRoomId);

            while (scanner.hasNextLine()) {
                String userInput = scanner.nextLine();
                String[] tokens = userInput.split(" ", 3);
                if ("join".equals(tokens[0]) && tokens.length > 1) {
                    attemptingRoomId = tokens[1];
                }
                out.println(userInput);

                if ("exit".equalsIgnoreCase(userInput)) {
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("An error occurred: " + e.getMessage());
        }
    }
}
