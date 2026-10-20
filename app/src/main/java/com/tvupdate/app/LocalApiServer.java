package com.tvupdate.app;

import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

final class LocalApiServer {
    interface Endpoint { Reply handle(String method, String path, String body) throws Exception; }
    static final class Reply {
        final int code;
        final JSONObject json;
        Reply(int code, JSONObject json) { this.code = code; this.json = json; }
    }

    private final Endpoint endpoint;
    private ServerSocket server;
    private Thread thread;

    LocalApiServer(Endpoint endpoint) { this.endpoint = endpoint; }

    synchronized void start() throws Exception {
        if (server != null) return;
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress("0.0.0.0", 8765));
        server = socket;
        thread = new Thread(() -> loop(socket), "tv-update-api");
        thread.start();
    }

    synchronized void stop() {
        if (server == null) return;
        try { server.close(); } catch (Exception ignored) {}
        server = null;
        thread = null;
    }

    private void loop(ServerSocket socket) {
        while (!socket.isClosed()) {
            try (Socket client = socket.accept()) {
                client.setSoTimeout(8000);
                serve(client);
            } catch (Exception e) {
                if (!socket.isClosed()) Log.w("TvUpdate", "API client failed", e);
            }
        }
    }

    private void serve(Socket client) throws Exception {
        if (!(client.getInetAddress().isSiteLocalAddress()
                || client.getInetAddress().isLoopbackAddress())) {
            write(client, 403, new JSONObject().put("error", "LAN only"));
            return;
        }
        InputStream in = client.getInputStream();
        String first = line(in);
        String[] parts = first.split(" ");
        if (parts.length != 3 || !parts[2].startsWith("HTTP/1.")) {
            write(client, 400, new JSONObject().put("error", "bad request"));
            return;
        }
        int length = 0;
        boolean hasLength = false;
        for (int i = 0; i < 32; i++) {
            String header = line(in);
            if (header.isEmpty()) break;
            int colon = header.indexOf(':');
            if (colon < 1) throw new IllegalArgumentException("bad header");
            if (header.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                length = Integer.parseInt(header.substring(colon + 1).trim());
                hasLength = true;
            }
            if (i == 31) throw new IllegalArgumentException("too many headers");
        }
        if (length < 0 || length > 8192) {
            write(client, 413, new JSONObject().put("error", "body too large"));
            return;
        }
        if (!(parts[0].equals("GET") || parts[0].equals("POST") || parts[0].equals("PUT"))) {
            write(client, 405, new JSONObject().put("error", "method not allowed"));
            return;
        }
        if (!parts[0].equals("GET") && !hasLength) {
            write(client, 411, new JSONObject().put("error", "Content-Length required"));
            return;
        }
        byte[] body = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = in.read(body, offset, length - offset);
            if (n < 0) throw new IllegalArgumentException("incomplete body");
            offset += n;
        }
        try {
            Reply reply = endpoint.handle(parts[0], parts[1], new String(body, StandardCharsets.UTF_8));
            write(client, reply.code, reply.json);
        } catch (Exception e) {
            write(client, 400, new JSONObject().put("error", e.getMessage()));
        }
    }

    private static String line(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (out.size() <= 8192) {
            int b = in.read();
            if (b < 0) throw new IllegalArgumentException("unexpected end of request");
            if (b == '\n') {
                byte[] bytes = out.toByteArray();
                int n = bytes.length;
                if (n > 0 && bytes[n - 1] == '\r') n--;
                return new String(bytes, 0, n, StandardCharsets.US_ASCII);
            }
            out.write(b);
        }
        throw new IllegalArgumentException("line too long");
    }

    private static void write(Socket client, int code, JSONObject json) throws Exception {
        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        String reason = code == 200 ? "OK" : code == 202 ? "Accepted" : "Error";
        String head = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
        OutputStream out = client.getOutputStream();
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }
}
