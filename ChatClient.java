import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    public static void main(String[] args) throws IOException {
        String serverAddress = "localhost";
        int port = 1234;

        Socket socket = new Socket(serverAddress, port);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter your username: ");
        String userName = scanner.nextLine();
        out.println(userName); // 發送用戶名到服務器

        // 讀取來自服務器的信息
        new Thread(() -> {
            try {
                String serverMessage;
                while ((serverMessage = input.readLine()) != null) {
                    System.out.println(serverMessage);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // 處理用戶輸入
        while (scanner.hasNextLine()) {
            String userInput = scanner.nextLine();
            out.println(userInput);

            if (userInput.equalsIgnoreCase("exit")) {
                break;
            }
        }

        socket.close();
        scanner.close();
    }
}
