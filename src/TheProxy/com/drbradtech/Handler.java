package TheProxy.com.drbradtech;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author brad
 */
public class Handler extends Thread {
    
    public static final Pattern CONNECT_PATTERN = Pattern.compile("CONNECT (.+):(.+) HTTP/(1\\.[01])", Pattern.CASE_INSENSITIVE);
    private final Socket clientSocket;
    private boolean previousWasR = false;

    public Handler(Socket clientSocket){
        this.clientSocket = clientSocket;
    }

    @Override
    public void run(){
        try{
            String request = readLine(clientSocket);
            Matcher matcher = CONNECT_PATTERN.matcher(request);
            
            if(matcher.matches()){
                System.out.println("HTTPS  -  "+request);
                String header;
                do{
                    header = readLine(clientSocket);
                }while(!"".equals(header));
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(clientSocket.getOutputStream(), "ISO-8859-1");

                final Socket forwardSocket;
                try{
                    forwardSocket = new Socket(matcher.group(1), Integer.parseInt(matcher.group(2)));
                }catch(IOException | NumberFormatException e){
                    e.printStackTrace();
                    outputStreamWriter.write("HTTP/" + matcher.group(3) + " 502 Bad Gateway\r\n");
                    outputStreamWriter.write("Proxy-agent: Simple/0.1\r\n");
                    outputStreamWriter.write("\r\n");
                    outputStreamWriter.flush();
                    return;
                }
                try{
                    outputStreamWriter.write("HTTP/" + matcher.group(3) + " 200 Connection established\r\n");
                    outputStreamWriter.write("Proxy-agent: Simple/0.1\r\n");
                    outputStreamWriter.write("\r\n");
                    outputStreamWriter.flush();

                    Thread remoteToClient = new Thread(){
                        @Override
                        public void run(){
                            forwardData(forwardSocket, clientSocket);
                        }
                    };
                    remoteToClient.start();
                    try{
                        if(previousWasR){
                            int read = clientSocket.getInputStream().read();
                            if(read != -1){
                                if(read != '\n'){
                                    forwardSocket.getOutputStream().write(read);
                                }
                                forwardData(clientSocket, forwardSocket);
                            }else{
                                if(!forwardSocket.isOutputShutdown()){
                                    forwardSocket.shutdownOutput();
                                }
                                if(!clientSocket.isInputShutdown()){
                                    clientSocket.shutdownInput();
                                }
                            }
                        }else{
                            forwardData(clientSocket, forwardSocket);
                        }
                    }finally{
                        try{
                            remoteToClient.join();
                        }catch(InterruptedException e){
                            e.printStackTrace();
                        }
                    }
                }finally{
                    forwardSocket.close();
                }
                
                
            }else{
                System.out.println("HTTP   -  "+request);

                if(request.split(" ").length > 1){
                    String urlToCall = request.split(" ")[1];
                    
                    DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
                    BufferedReader rd = null;
                    try{

                        URL url = new URL(urlToCall);
                        URLConnection conn = url.openConnection();
                        conn.setDoInput(true);
                        conn.setDoOutput(false);

                        InputStream is = null;
                        try{
                            is = conn.getInputStream();
                            rd = new BufferedReader(new InputStreamReader(is));
                        }catch(IOException ioe){
                        }

                        byte by[] = new byte[ 32768 ];
                        int index = is.read(by, 0, 32768);

                        while( index != -1 ){
                          out.write(by, 0, index);
                          index = is.read(by, 0, 32768);
                        }

                        out.flush();

                    }catch(Exception e){
                        out.writeBytes("");
                    }

                    if(rd != null){
                        rd.close();
                    }
                    if(out != null){
                        out.close();
                    }
                }
            }
            
        }catch(IOException e){
            e.printStackTrace();
        }finally{
            try{
                clientSocket.close();
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    private static void forwardData(Socket inputSocket, Socket outputSocket){
        try{
            InputStream inputStream = inputSocket.getInputStream();
            try{
                OutputStream outputStream = outputSocket.getOutputStream();
                try{
                    byte[] buffer = new byte[4096];
                    int read;
                    do{
                        read = inputStream.read(buffer);
                        if(read > 0){
                            outputStream.write(buffer, 0, read);
                            if(inputStream.available() < 1){
                                outputStream.flush();
                            }
                        }
                    }while(read >= 0);
                }finally{
                    if(!outputSocket.isOutputShutdown()){
                        outputSocket.shutdownOutput();
                    }
                }
            }finally{
                if(!inputSocket.isInputShutdown()){
                    inputSocket.shutdownInput();
                }
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private String readLine(Socket socket)throws IOException{
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int next;
        readerLoop:
        while((next = socket.getInputStream().read()) != -1){
            if(previousWasR && next == '\n'){
                previousWasR = false;
                continue;
            }
            previousWasR = false;
            switch(next){
                case '\r':
                    previousWasR = true;
                    break readerLoop;
                case '\n':
                    break readerLoop;
                default:
                    byteArrayOutputStream.write(next);
                    break;
            }
        }
        return byteArrayOutputStream.toString("ISO-8859-1");
    }
}
