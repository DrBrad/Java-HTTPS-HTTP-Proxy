package TheProxy.com.drbradtech;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author brad
 */
public class Main extends Thread {

    public static void main(String[] args){
        (new Main()).run();
    }

    public Main(){
        super("Server Thread");
    }

    @Override
    public void run(){
        try(ServerSocket serverSocket = new ServerSocket(1151)){
            Socket socket;
            try{
                while((socket = serverSocket.accept()) != null){
                    (new Handler(socket)).start();
                }
            }catch(IOException e){
                e.printStackTrace();
            }
        }catch(IOException e){
            e.printStackTrace();
            return;
        }
    }
}
