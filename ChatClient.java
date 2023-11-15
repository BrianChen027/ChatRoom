import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    public static void main(String[] args) throws IOException {
        String serverAddress = "localhost";
        int port = 1234;

        try (Socket socket = new Socket(serverAddress, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {

            // 创建一个线程来读取并显示服务器的消息
            Thread responseThread = new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = input.readLine()) != null) {
                        System.out.println(serverMessage); // 打印服务器响应
                    }
                } catch (IOException e) {
                    if (!socket.isClosed()) {
                        System.out.println("Error reading from server: " + e.getMessage());
                    }
                }
            });
            responseThread.start();

            // 主线程处理用户输入
            System.out.print("Enter your username: ");
            while (scanner.hasNextLine()) {
                String userInput = scanner.nextLine();
                out.println(userInput); // 发送用户输入到服务器

                if ("exit".equalsIgnoreCase(userInput)) {
                    break; // 如果输入 exit，则结束循环
                }
            }
            socket.close();
            
            responseThread.join(); // 等待响应线程结束

        } catch (IOException | InterruptedException e) {
            System.out.println("An error occurred: " + e.getMessage());
        }
    }
}
